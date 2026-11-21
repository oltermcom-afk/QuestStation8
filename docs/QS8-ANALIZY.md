# QuestStation8 — analizy do decyzji (2026-11-21)

Bez kodu. Źródła: kod P5M w tym repo (pliki i linie podane przy punktach),
dokumentacja Mety „Composition Layer Filtering”, specyfikacja Khronos
`XR_FB_composition_layer_settings`. Koszty w ms to **szacunki** dla Adreno 740
(XR2 Gen 2), nie pomiary. Każdą liczbę trzeba potwierdzić w dzienniku
(`Video path`, medidor „Frames”/„Latency”) na Queście.

---

## 4. Profile (Etap 2)

### Co już istnieje i kiedy działa

| Ustawienie (klucz) | Gdzie | Kiedy działa |
|---|---|---|
| Bitrate `bitrate_kbps` (2–100 Mb/s, >50 eksperyment) | `StreamQualityPrefs.bitrateKbps` → `videoProfile()` | **nowe połączenie** (launch spec) |
| Auto bitrate `auto_bitrate_mode` (Off / Only lowers / Up and down) | `autoBitrateMode` | **następna sesja** (liczy po sesji) |
| 10-bit `hdr` (HEVC Main10 + PQ, tylko PS5, nie w 3D) | `tenBit` → codec + ścieżka shader | **nowe połączenie** |
| Ścieżka shader `tone_mapped` | `toneMapped` → `setRenderPath` przed swapchainem | **nowe połączenie** |
| Przewidywanie klatek `frame_extrapolation` | `setQuality(...)` | na żywo, **ale tylko na ścieżce shader** (ścieżka wybrana przy starcie) |
| Panel 120/180 Hz `panel_180hz` | `selectDisplayRefreshRate` w `VrStreamActivity` l.586 | **nowe połączenie** (technicznie dałoby się na żywo) |
| 3D `synthetic_stereo` (+ siła, zbieżność, pop-out, sieć) | swapchain na oko | **nowe połączenie**; siła 3D na żywo |
| Wyostrzanie `layer_sharpness` | `setQuality` | **na żywo** |
| Jasność `video_brightness` | `setQuality` (color scale kompozytora) | **na żywo** |
| Dźwięk przestrzenny `spatial_audio` | `setSpatialAudio` | **na żywo** |
| Ekran: rozmiar, odległość, wysokość, kształt, krzywizna (`ScreenPrefs`) | `setScreenParams`/`setLayerShape` | **na żywo** |
| Pokój / przyciemnienie `passthrough`, `cinema` | `setPassthrough`/`setQuality` | **na żywo** |

Rozdzielczość jest stała (1920×1080, 60 kl./s, `sourceFps = 60`): to limit Remote Play, nie ustawienie.

### Propozycja wartości

| | Responsywność | Zrównoważony (domyślny) | Ultra Płynność | Kino |
|---|---|---|---|---|
| Ścieżka | direct | direct | shader | shader (wymusza 10-bit) |
| 10-bit | wył. | wył. | wył. | **wł.** |
| Przewidywanie klatek | wył. | wył. | **wł.** | wył. |
| Panel | **180 Hz** (−~3 ms) | 120 Hz | 120 Hz | 120 Hz |
| Bitrate | 25 Mb/s | 30 Mb/s | 30 Mb/s | **40 Mb/s** |
| Auto bitrate | Off | Only lowers | Only lowers | Only lowers |
| Wyostrzanie | Medium (kompozytor) | Medium | Light (CAS w shaderze) | Light (CAS w shaderze) |
| Ekran | bez zmian | bez zmian | bez zmian | preset Kino: cylinder, krzywizna ~0.6, pokój ukryty |

**Kino z wyższym bitrate i 10-bit: tak, z zastrzeżeniami.**
- 10-bit pomaga w gradientach (niebo, mgła, półmrok). To realny zysk w filmach i spokojnych grach.
- Koszt: koniec ścieżki bez kopii (direct). Dochodzi jeden przebieg GPU i opóźnienie, które P5M opisuje jako główną cenę. Mierzone w P5M nie było; szacunek ~1–3 ms.
- 40 Mb/s to bezpieczna górna granica przy Wi-Fi 6E i konsoli na kablu. Powyżej 50 Mb/s jest eksperyment, którego dziennik P5M nie potwierdził. Profil z „Only lowers” sam zejdzie w dół przy stratach.
- 10-bit nie działa z PS4 ani w 3D (`tenBitNaSessao`), więc Kino na PS4 zostaje 8-bit, bez komunikatu o błędzie.

### Gdzie wybierać profil

1. **Launcher, strona „Stream”, pierwszy wiersz „Profile”** (cykl: Responsywność → Zrównoważony → Ultra Płynność → Kino).
   - Większość różnic działa dopiero przy nowym połączeniu, więc wybór przed połączeniem jest uczciwy.
   - Gdy użytkownik zmieni ręcznie którąś wartość profilu, wiersz pokazuje „Custom”. Profil ustawia wartości przez istniejące settery, niczego nie blokuje.
2. **Panel w grze: tylko podgląd** aktywnego profilu w nagłówku, bez przełącznika.
   - Przełącznik w grze musiałby albo rozłączać sesję, albo zmieniać tylko część ustawień (ekran, wyostrzanie). To byłoby mylące.
   - Ewentualnie później: „Profil ◀ ▶” w panelu z jasnym „applies on next connection”.
3. Docelowo razem z 1D (ekran startowy): przy przycisku „Tryb domowy / mobilny” ostatnio użyty profil.

---

## 5. Jakość obrazu

### a) MQSR i CAS — co jest teraz (fakty z kodu)

**CAS (nasz shader, `tone_mapper.cpp` l.548–600)**
- Działa tylko na ścieżce shader (10-bit, tone mapping lub 3D).
- Stopnie Light / Medium / Strong to 0,35 / 0,65 / 1,0 twardości CAS.
- Na ścieżce shader z ustawieniem Light/Medium/Strong kompozytor nic nie filtruje (`compositor_owns_filter` = false).

**Ścieżka direct (domyślna)**
- Stopnie to bity `XrCompositionLayerSettingsFB` kompozytora (`xr_session.cpp` l.2290–2333):
  - Light = `NORMAL_SUPER_SAMPLING`,
  - Medium = `NORMAL_SHARPENING` + `NORMAL_SUPER_SAMPLING`,
  - Strong = `NORMAL_SHARPENING`.
- Używamy więc **poziomu NORMAL** rozszerzenia `XR_FB_composition_layer_settings`. `QUALITY_SUPER_SAMPLING` nie jest używany nigdzie.

**MQSR**
- Według Mety MQSR to bit **`QUALITY_SHARPENING`** („single-pass spatial upscaling and sharpening”).
- P5M dodaje go tylko w trybie „Automatic”, razem z `AUTO_LAYER_FILTER_BIT_META` (`XR_META_automatic_layer_filter`) jako jednego z kandydatów, obok `NORMAL_SHARPENING` i `NORMAL_SUPER_SAMPLING`.
- Jawne MQSR wyłączono w P5M dev.168, bo dawało jasną plamę podążającą za głową, „nas duas formas de tela” (na quad i na cylindrze).

**Wniosek 1: MQSR na cylindrze**
- Dokumentacja Mety nie wymienia typów warstw (ani quad, ani cylinder).
- Specyfikacja dopuszcza dołączenie `XrCompositionLayerSettingsFB` do dowolnej warstwy. Ustawienia są tylko sugestią: kompozytor „may enable or disable them”.
- Doświadczenie P5M pokazuje, że runtime **coś robi na cylindrze** (artefakt był w obu kształtach). Nie ma jednak dowodu, że robi to dobrze.
- Preset Kino (cylinder) z „Automatic” zostawia decyzję runtime'owi.

**Wniosek 2: MQSR w trybie Automatic może nie być nigdy wybierany**
- Meta pisze, że przy dwóch bitach wyostrzania naraz wygrywa NORMAL.
- Przy włączonym bicie AUTO bity są listą kandydatów, ale nie da się z aplikacji sprawdzić, który filtr runtime faktycznie wybrał.

**Koszt**
- Filtry kompozytora to **nie jest „bez kosztu GPU”**, jak zakłada plan (Etap 2: „MQSR włączony wszędzie … bez kosztu GPU”).
- Meta: filtrowanie „requires more GPU resources”, może opóźnić kompozycję i gubić klatki. Tryb Auto ogranicza to sam.
- Liczb w ms Meta nie podaje. Szacunek: 0,2–0,6 ms na klatkę kompozytora przy 120 Hz dla warstwy wideo.

**Rekomendacja dla (a)**
- Zostawić jak jest (Medium na direct, Light-CAS na shader).
- **Poprawić plan Etapu 2**: usunąć „MQSR włączony wszędzie”.
- Jedyny tani test: przełącznik A/B (punkt g) między Medium a Automatic na cylindrze, oceniany na oko.

### b) EASU/RCAS (FSR 1) w przebiegu shadera

- Swapchain ma dokładnie 1920×1080 (`VrStreamActivity` l.545: „qualquer escala aqui viraria trabalho extra”). EASU ma sens tylko przy wyjściu większym niż wejście, czyli przy powiększonym swapchainie (np. 2560×1440, według `XR_META_recommended_layer_resolution`).
- **Zysk:** mały. Kompozytor i tak reprojekcjonuje i filtruje warstwę, a EASU na wideo z kompresją wzmacnia bloki i „mosquito”. RCAS to praktycznie CAS, który już mamy.
- **Koszt:** EASU ~0,6–1,2 ms (12 próbek + dużo ALU na 3,7 Mpx) plus większy swapchain, czyli więcej pamięci i pracy kompozytora.
- **Ryzyko:** średnie (artefakty, temperatura).
- **Nie robić.** RCAS zamiast CAS: zysk niemierzalny.

### c) Chroma 4:2:0 sterowana lumą (w tym samym przebiegu)

- Teraz kolor konwertuje sterownik (`samplerExternalOES`), z dwuliniową chromą. P5M ma już działającą sondę `GL_EXT_YUV_target` (`kProbeShader`, l.826), która czyta surowe Y/Cb/Cr i ustala, jakiej macierzy i zakresu używa sterownik. Fundament istnieje.
- **Zysk:** ostrzejsze kolorowe krawędzie (czerwony/niebieski tekst HUD, napisy, cienkie kolorowe linie). W zwykłej scenie ledwo widoczny.
- **Koszt:** ~0,3–0,5 ms (dodatkowe próbki chromy i wagi z lumy), tylko na ścieżce shader.
- **Ryzyko:** średnie. Trzeba dokładnie odtworzyć macierz i zakres sterownika (sonda to umożliwia), inaczej przesunięcie kolorów.
- **Może kiedyś**, po profilach i A/B, tylko dla Kino/Ultra.

### d) Deblocking w shaderze

- HEVC ma deblocking i SAO wewnątrz pętli dekodera. Przy 25–40 Mb/s klasyczne bloki są rzadkie.
- Widoczne artefakty to raczej pasma w ciemnych gradientach (lepiej leczy je 10-bit) i „mosquito” przy krawędziach.
- **Zysk:** mały. **Koszt:** 0,3–0,8 ms. **Ryzyko:** rozmycie detalu, które walczy z CAS.
- **Nie robić.** Jeśli już, lekkie „debanding” tylko dla 8-bit na ścieżce shader, i to po pomiarach.

### e) Preset „piksel w piksel”

- P5M już pyta runtime o zalecaną rozdzielczość warstwy (`XR_META_recommended_layer_resolution`) i pokazuje werdykt w wierszu Size panelu („can grow” / „just right” / „too large”, `nitidezVerdict()`).
- Preset 1:1 to automatyczne dobranie `centralAngle`, aż zalecana szerokość ≈ 1920.
- **Zysk:** realny i darmowy: największa ostrość bez migotania krawędzi, mniej pracy dla filtrów.
- **Koszt:** 0 ms.
- **Ryzyko:** niskie. Przy ~25 px/° ekran wyjdzie szeroko (~75–80°), więc to opcja (przycisk), a nie wymuszenie. Odległość ekranu nie zmienia proporcji kątowych.
- **Robić. Pierwsze w kolejce.** Np. przytrzymanie Size (↑) w panelu albo pozycja w profilu Kino.

### f) HDR: MaxCLL zamiast stałych 1000 nitów, albo suwak

- `source_peak_nits_ = 1000` (`tone_mapper.h` l.430), a cel to 240 nitów jako biel panelu. Komentarz P5M mówi, że MaxCLL „chega do lado do chiaki, fora desta biblioteca”. Nie wiadomo, czy dekoder Questa podaje `KEY_HDR_STATIC_INFO`, ani czy PS5 w Remote Play wysyła sensowne MaxCLL (często 0 albo wartość z kalibracji HDR konsoli).
- **Zysk:** umiarkowany, tylko w grach HDR przy 10-bit. **Koszt:** 0 ms (uniform).
- **Ryzyko:** odczyt MaxCLL średnie (zależny od sterownika i strumienia), suwak niskie.
- **Rekomendacja:** suwak „Peak” w 3 krokach (600 / 1000 / 4000 nitów) na żywo, tylko przy 10-bit. Odczyt MaxCLL tylko jako zapis do dziennika, żeby sprawdzić, czy w ogóle przychodzi.

### g) Przełącznik A/B (oryginał / ulepszony)

- **Zysk:** pośredni, ale ważny. Bez niego ocena b–f to zgadywanie. **Koszt:** 0 ms.
- Na direct przełącza bity kompozytora (Off ↔ bieżące), na shader `uSharpen` i przyszłe filtry. **Ryzyko:** niskie.
- **Kłopot:** brak wolnego przycisku. Na Touch zajęte są wszystkie poza Meta (systemowy). Na padzie wolny byłby L3 w panelu. Propozycja: przytrzymanie X (Touch) / □ (pad) przez 0,5 s = A/B, dopóki trzymasz.

### Rekomendacja i kolejność

1. **e) preset 1:1.** Darmowy, największy pewny zysk ostrości.
2. **g) A/B.** Narzędzie do oceny reszty, potrzebne przed c) i f).
3. **f) suwak Peak** dla 10-bit (wchodzi w profil Kino).
4. Korekta planu: filtry kompozytora kosztują GPU; MQSR zostaje tylko w „Automatic”; test A/B Medium vs Automatic na cylindrze.
5. **c) chroma sterowana lumą:** później, tylko jeśli A/B pokaże problem z kolorowymi krawędziami.
6. **Nie robić:** b) EASU/RCAS i d) deblocking.
