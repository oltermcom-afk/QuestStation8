# QuestStation8 — plan

Baza: P5M 0.3.0-beta (github.com/beecrepaldi-afk/P5M, commit `d61c8e6`), AGPL-3.0.
Zasady pracy: `CLAUDE.md`.

## Stan faktyczny

P5M już ma: wykrywanie i rejestrację PS5, wybudzanie + łączenie jednym kliknięciem,
Remote Play przez PSN, wideo zero-copy, generowanie klatek (ekstrapolacja Adreno),
MQSR/CAS, tone mapping HDR, 3D, regulację ekranu, auto bitrate, diagnostykę, build APK
w GitHub Actions. **Nie przepisujemy tego.**

**Tryb domowy przetestowany (2026-11-21) na niezmienionym P5M — działa:**
rejestracja na drugim koncie PSN, DualSense przy PS5, gra zostaje na koncie głównym,
pełna haptyka i spusty, dźwięk w Queście, opóźnienie praktycznie nieodczuwalne.

Problemy znalezione w teście:
1. Panel ustawień w grze otwiera się tylko kombinacją na padzie przy Queście
   (L3+R3+R1). `xr_session.cpp` nie ma żadnego `XrActionSet` — kontrolery Touch nic
   nie robią. W Trybie domowym nie da się zmienić ekranu ani zakończyć sesji.
2. Rejestracja wymaga ręcznego PSN Account ID (base64) zdobytego poza aplikacją.
3. Rejestracja z zaznaczonym „Broadcast” → `Regist failed to send search: Invalid
   argument` (w `regist_search_connect` port zerowany przed `bind` nie jest
   przywracany). Bez Broadcast działa.

Świadomie NIE robimy: interpolacji ani modelu neuronowego do klatek, wariantu 30→40,
FSR, przebudowy renderera/połączenia, pełnego tłumaczenia UI (teraz).

## Etap 0 — fork i build

1. Fork `beecrepaldi-afk/P5M` na koncie Damiana jako `QuestStation8`. Remote
   `upstream` = oryginał. `main` = czyste lustro upstream; pracujemy na `qs8/main`
   (gałąź domyślna). W forku włączyć Actions z uprawnieniami „Read and write”.
2. `CLAUDE.md` → root, ten plik → `docs/QS8-PLAN.md`.
3. Klucz podpisu: workflow czyta sekret `P5M_DEBUG_KEYSTORE_BASE64`
   (`build-apk.yml` ~l.236–251, `app/build.gradle` ~l.190–225). Wygenerować własny
   keystore, ustawić sekrety przez `gh secret set`, hasło przekazać Damianowi do
   zachowania. Keystore nigdy w repo. Bez tego każda aktualizacja = reinstalacja =
   utrata rejestracji konsoli.
4. Tożsamość aplikacji (żeby stała obok P5M):
   - `applicationId` → `pl.olterm.queststation8` (bez zmiany `namespace` i pakietów),
   - authorities w `AndroidManifest.xml` (l.94, l.107) → `${applicationId}.…`;
     `grep -rn "io.github.gblandro.p5m\." app/src` i dopasować stałe w Kotlinie,
   - nazwa w launcherze: „QuestStation8”,
   - `Atualizacao.kt` l.30–31 → releases forka (sprawdzić dopasowanie nazwy assetu APK),
   - sprawdzić, czy `tools/conferir.py` nie waliduje tych wartości sztywno.
5. Lokalnie `tools/conferir.py` + `tools/compilar_nativo.py`, push, CI zielone, APK
   w release `dev` forka.

**Test na Queście:** instalacja z przeglądarki Questa, rejestracja, połączenie
w Trybie domowym i mobilnym, aktualizacja do kolejnego buildu bez utraty rejestracji.

## Etap 1 — Tryb domowy (każdy punkt = osobny PR i APK)

### 1B. Kontrolery Questa → panel ustawień (najpierw)
- Native: nowe `app/src/main/cpp/qs8_touch.{h,cpp}`. Action set z profilem
  `/interaction_profiles/oculus/touch_controller`: ≡ lewy (`/user/hand/left/input/menu/click`),
  oba thumbsticki, A/B/X/Y, gripy. Bindings + `xrAttachSessionActionSets` raz po
  utworzeniu sesji; `xrSyncActions` w pętli klatek przy fokusie. W `xr_session.cpp`
  tylko 3 wywołania z `// QS8:` (utworzenie, sync, zniszczenie). Przycisk Meta
  zarezerwowany przez system.
- JNI `nativeReadTouchMenu` w `p5m_jni.cpp` (+ to, co sprawdza `conferir.py`),
  źródło w `CMakeLists.txt`.
- Kotlin `qs8/TouchMenu.kt`: zbocza → syntetyczne `KeyEvent`: thumbstick → `DPAD_*`
  (z `repeatCount` przy trzymaniu), A → `BUTTON_A`, B → `BUTTON_B` (trzymanie = koniec
  sesji), X → `BUTTON_X`, Y → `BUTTON_Y`, gripy → `L1`/`R1`, triggery → `L2`/`R2`
  (kształt ekranu płaski/zakrzywiony i stopień krzywizny), ≡ → `toggleAdjustMode()`.
- Mapowanie pokazać w panelu jako legendę dla kontrolerów Questa (Tryb domowy).
- **Krytyczne:** zdarzenia idą WYŁĄCZNIE do `toggleAdjustMode`, `handleAdjustKey`,
  `handleQuitHold`, `teclaNoPin` (`VrStreamActivity.kt` ~l.1699–1790) przez jedną nową
  metodę `qs8TouchKey(event)`. Nigdy do `dispatchKeyEvent`/`StreamInput` — inaczej
  kontroler Questa sterowałby konsolą jako gracz 2.
- Odpytywanie na istniejącym zegarze aktywności lub wątku tła, nie blokująco na głównym.

### 1E. Rejestracja jak w Asobi — bez Maca, bez ręcznego Account ID
Cel: użytkownik loguje się do PSN w goglach i konsola rejestruje się sama.

Co już istnieje:
- `PsnRemoteActivity.kt`: logowanie Sony w WebView w goglach, przekierowanie z kodem
  przechwytywane automatycznie (`shouldOverrideUrlLoading`); dla kont z passkey
  (bez hasła) awaryjnie `PsnLoginRelay.kt` — logowanie na telefonie przez adres w LAN.
  Po zalogowaniu `PsnAuth.accountId(ctx)` zwraca 8-bajtowy ID.
- libchiaki ma **rejestrację przez PSN bez PIN-u** (`session.c`: `auto_regist`,
  zdarzenie `CHIAKI_EVENT_REGIST` z `ChiakiRegisteredHost`; tak robi chiaki-ng desktop:
  `gui/src/qmlbackend.cpp` → `QmlBackend::autoRegister` / `finishAutoRegister`).
  Port Androida (P5M) tego nie wystawia — brak `auto_regist` w JNI i Kotlinie.

Krok 1E-1 (mały, najpierw):
- Ekran rejestracji (`external/chiaki-ng/android/.../regist/RegistActivity.kt`, zmiana
  przez nowy plik w `patches/`): jeśli użytkownik jest zalogowany do PSN — pole Account ID
  wypełnione automatycznie `Base64.encodeToString(PsnAuth.accountId(ctx), NO_WRAP)`;
  jeśli nie — przycisk „Zaloguj przez PSN” → istniejący przepływ z `PsnRemoteActivity`
  → powrót z wypełnionym polem. Logika w `qs8/RejestracjaPsn.kt`.
- Wskazówka przy PIN-ie: „PIN wygeneruj na PS5 na koncie, na które się logujesz”.
- „Broadcast” domyślnie odznaczony. Poprawkę portu w libchiaki tylko, jeśli patch `0053`
  ani aktualny upstream jej nie zawiera.

Krok 1E-2 (docelowo, jak w Asobi — bez PIN-u):
- Wystawić `auto_regist` przez JNI (patch w `external/chiaki-ng/android/app/src/main/cpp`)
  i obsłużyć `CHIAKI_EVENT_REGIST` → zapis hosta w istniejącej bazie konsol P5M.
- Przepływ: „Zaloguj przez PSN” → lista konsol z konta → „Połącz” → rejestracja
  automatyczna przy pierwszym połączeniu. PIN zostaje jako opcja awaryjna.
- Najpierw przeczytać i opisać, jak robi to chiaki-ng desktop, potem zaproponować
  Damianowi minimalny zakres. Działanie wymaga testu na Queście (dla PS5; dla PS4
  Sony pozwala tylko na konsolę główną konta).

- Oba kroki: możliwość wylogowania i zalogowania na inne konto (drugie konto PSN do
  Trybu domowego), z czytelną informacją, które konto jest aktywne.

### 1C. Tryb domowy jako tryb, nie błąd
- `qs8/Qs8Prefs.kt`: `trybDomowy` (domyślnie false). Przy false — zero zmian zachowania.
- Przy true: zamiast ostrzeżenia o braku pada (`VrStreamActivity.kt` ~l.1329,
  diagnostyka) i przypomnienia o L3+R3 (~l.112) — „Menu: przycisk ≡ na lewym kontrolerze”.
- Moduły szukające pada w pętli (`EspelhoDualSense.kt` ~l.402, `GamepadMonitor.kt`)
  nie powinny wtedy obciążać CPU ani dziennika — tylko jeśli to drobna zmiana.

### 1F. Ekran „Kino” jak w Asobi + lepsze centrowanie
Życzenie Damiana: zakrzywiony ekran, wokół czarno (bez pokoju), ekran zawsze przed nim.
Wszystko to P5M już umie (`ScreenPrefs.kt` + panel: Trójkąt cyklicznie przyciemnia
passthrough aż do wyłączenia = czarne tło, L2 płaski/zakrzywiony, R2 krzywizna, D-pad
rozmiar/odległość, L1/R1 wysokość, Krzyżyk centruje). Domyślnie P5M ma passthrough
włączony i ekran płaski.
- Przy pierwszym uruchomieniu QS8 (flaga w `Qs8Prefs`) jednorazowo ustaw preset „Kino”:
  passthrough wył., kształt cylinder, krzywizna ~0.6, rozmiar/odległość jak domyślne.
  Przez istniejące settery `ScreenPrefs`, **bez** zmiany wartości domyślnych w pliku P5M.
- Centrowanie: `xr_session.cpp` nie obsługuje `XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING`,
  a P5M trzyma własną orientację (`recenter_orientation_`), więc systemowe przytrzymanie
  przycisku Meta nie przestawia ekranu. Obsłużyć to zdarzenie wywołaniem `Recenter()`
  (punkt `// QS8:` w pętli `xrPollEvent`). Po tym centrowanie działa systemowo i przyciskiem A.
- Test: start sesji → zakrzywiony ekran na czarnym tle; przytrzymanie Meta i A
  ustawiają ekran przed wzrokiem.

### 1D. Ekran startowy
- Dwa duże przyciski „Tryb domowy” / „Tryb mobilny” w `LauncherActivity.kt`,
  zapamiętany wybór, „Połącz z ostatnią konsolą” na istniejącym mechanizmie (patch `0039`).

**Test na Queście po każdym punkcie:** ≡ otwiera panel; thumbstick reguluje ekran;
A centruje; trzymanie B kończy sesję czysto; przyciski Questa poza panelem nie wpływają
na grę; tryb mobilny działa jak w P5M.

## Etap 2 — profile

Najpierw tabela do akceptacji Damiana: profil → istniejące klucze
`StreamQualityPrefs`/`ScreenPrefs` → wartości → czy zmiana wymaga przełączenia ścieżki
renderowania. Potem `qs8/Profile.kt` ustawiający wartości przez istniejące settery
i linia „Profil ◀ ▶” w panelu.

| Profil | Ścieżka | Klatki | Auto bitrate | Ekran |
|---|---|---|---|---|
| Zrównoważony (domyślny) | direct | wył. | wł. | — |
| Ultra Płynność | shader + frames | wł. | wł. | — |
| Kino | direct | wył. | wł. | duży, przyciemniony |

Filtry kompozytora (MQSR = bit `QUALITY_SHARPENING`, zwykłe wyostrzanie, supersampling)
**kosztują GPU kompozytora** (Meta: „requires more GPU resources”), więc nie są „za darmo”.
MQSR tylko w trybie Sharpness „Automatic” (runtime decyduje, kiedy go użyć; jawne MQSR dawało
w P5M plamę za głową). Profile nie ustawiają bitrate'u (zależy od sieci, nie od gry).
3D osobnym przełącznikiem.
Profile ekranu Kanapa/Kino/Biurko = zestawy `ScreenPrefs`.

**Pomiar dev.21 (2026-11-22, Damian), head-to-photon z kompozytora:**
- direct + 120 Hz: 16–17 ms; direct + 180 Hz: 17 ms; shader (10-bit) + 180 Hz: 18–20 ms; zawsze 0 zgubionych klatek;
- 3D bez głębi z sieci neuronowej: bez wyraźnego wzrostu;
- **głębia z sieci neuronowej (`profundidade_neural`, P5M): 35–36 ms i wyraźnie wyższe CPU/GPU.**

Wniosek: 180 Hz nie daje mierzalnego zysku, więc profil „Responsywność” usunięty (PR #17) i nie wraca.
Przełącznik „Panel rate” P5M zostaje. Do rozważenia później: informacja o koszcie opóźnienia przy
przełączniku głębi neuronowej.

## Jakość obrazu — decyzje (2026-11-21, analiza: `docs/QS8-ANALIZY.md`)

Kolejność: preset „piksel w piksel” → przełącznik A/B → suwak Peak HDR → MQSR tylko w „Automatic”
(zrobione w PR #10–#14).

**MQSR tylko w „Automatic” — wycofane (PR #17, 2026-11-22).** Na dev.21 z samym `QUALITY_SHARPENING`
kompozytor gubił 0–4 klatki; w trybie A (bez filtra) 0. „Automatic” znowu daje Mecie wybór
z zestawu P5M (zwykłe i MQSR wyostrzanie, supersampling). Zostaje dziennik bitów warstwy.

- **c) chroma 4:2:0 sterowana lumą — może później.** Fundament jest (sonda `GL_EXT_YUV_target`),
  ale zysk dotyczy tylko kolorowych krawędzi, a koszt to ~0,3–0,5 ms na ścieżce shader i ryzyko
  przesunięcia kolorów. Wracamy, jeśli A/B pokaże problem.
- **b) EASU/RCAS (FSR 1) — nie robimy.** Swapchain jest 1:1 ze strumieniem (nie ma czego
  powiększać), kompozytor i tak filtruje warstwę, EASU wzmacnia artefakty kompresji, a RCAS to
  praktycznie istniejący CAS.
- **d) deblocking w shaderze — nie robimy.** HEVC ma deblocking i SAO w dekoderze; widoczne pasma
  w gradientach lepiej usuwa 10-bit, a dodatkowe rozmycie walczyłoby z CAS.

## Generowane klatki „do 120 / do 60” (PR #18, 2026-11-22)

Ustawienie w launcherze (Zaawansowane, widoczne przy „shader + frames”), domyślnie „do 120” = P5M.
„Do 60” zaokrągla krok przewidywania w dół do 1/60 s (`qs8_klatki.cpp`, jedna linia w
`tone_mapper.cpp`): gra 30 fps → jedna przewidziana klatka (½ okresu) na prawdziwą, każda przez dwa
odświeżenia (120 Hz); gra 60 fps → bez przewidywania.
Test dev.23: mniej artefaktów niż „do 120”, rzadkie szarpnięcie. Próba poprawki w dev.25 (pełne
odświeżenia z zaokrągleniem i własnym pomiarem odświeżania) była na Queście gorsza: więcej artefaktów
i chrupnięć przy szybkich obrotach (120 i 180 Hz, 3D bez głębi neuronowej) → cofnięta, logika z dev.23.
Hipoteza: przy 3D aplikacja chwilami nie zdąża na odświeżenie i własny pomiar odświeżania się mylił;
dev.23 bierze prawdziwy upływ czasu. Został sam licznik w dzienniku co 10 s (`QS8: frames rhythm …`,
nie zmienia obrazu): obrazy równe / krótsze / dłuższe, co ile rysowań prawdziwa klatka, ile rysowań
spóźnionych. Dalej dopiero z danymi z dziennika; jeśli to rytm — okres i chwila wyświetlenia z OpenXR
(`predictedDisplayPeriod`), nie z własnego pomiaru.
Automatu 60/120 nie robimy: brak wiarygodnego pomiaru
błędu przewidywania w trakcie gry.

## Kolejka 2026-11-22 (uzgodniona z Damianem)

1. **Wersje i powrót (PR #20).** Każdy build → stałe wydanie `qs8-dev.N`. Powrót do dev.M: workflow_dispatch
   `powrot_do` = M buduje kod tamtego commita jako `0.3.0-dev.<run>.r<M>` — wyższy numer, więc instaluje się jak
   aktualizacja (zejście na niższy versionCode wymagałoby odinstalowania = utrata rejestracji). W launcherze i
   karcie aktualizacji „dev.31 (25)”. Aplikacje sprzed #20 nie rozpoznają APK `….rM` (brak oferty w aplikacji;
   wtedy pobrać ze stałego wydania).
2. **Druga strona panelu w grze „Zaawansowane”.** Lista (krzyżak/drążek): klatki wył./do 60/do 120, 120/180 Hz,
   3D (2D = siła 0 w sesji z 3D), siła 3D, głębia neuronowa, pop-out, głębia ekranu, HDR peak; czego nie da się
   zmienić w sesji — przyciemnione z dopiskiem. Zapis do ustawień. **Zrobione w PR #21:** wejście przytrzymaniem
   Options (DualSense; krótkie Options = siła 3D po puszczeniu) albo ≡ (Touch; krótkie ≡ = panel po puszczeniu,
   przytrzymanie przy zamkniętym panelu otwiera od razu stronę 2). Siła 3D 0% = 2D (osobnego „3D wył.” nie ma).
   Bez bitrate'u (automatyczny bitrate P5M zmienia go w sesji). Stan sesji (shader / 3D / 10-bit) = zdjęcie z
   pierwszego otwarcia strony 2.
3. **MQSR (eksperymentalne).** Przełącznik przy „Automatic”, domyślnie wył.: `AUTO_LAYER_FILTER` + sam
   `QUALITY_SHARPENING` (wariant z dev.21), na żywo (bity warstwy co klatkę), także na stronie 2. Zgubione klatki
   z dev.21 były przy włączonej głębi neuronowej — test bez niej: 120/180 Hz, direct/shader, 3D; klatki,
   opóźnienie, „dysk za głową”, ostrość vs Automatic.
4. **Nazwane profile gier.** Zapisz jako… (nazwa z klawiatury Questa), nadpisz, zmień nazwę, usuń; „BF6
   (zmieniony)”. Zawartość: obraz, klatki, Hz, 3D, ekran (rozmiar, odległość, wysokość, kształt, krzywizna,
   pokój); bez bitrate'u. Wbudowane (Balanced, Ultra smooth, Cinema) zostają. W grze: wiersz Profil i „Zapisz do
   profilu” na stronie 2.
5. **Poświata wokół ekranu (Ambilight).** Kolory krawędzi obrazu ~10–15 razy/s, wygładzone, jako miękka warstwa
   za ekranem w ciemnym pokoju; tylko ścieżka shader (w direct obraz nie przechodzi przez aplikację). Najpierw
   rozpoznanie warstw i pokoju w P5M, osobny plan.

## Później (po pomiarach)

inwentaryzacja i uzupełnienie
diagnostyki, profile gier, tryb awaryjny, polski interfejs.

## Synchronizacja z P5M

Przy nowym release P5M: `main` ← `upstream/main` (fast-forward), `qs8/main` ← `main`.
Konflikty tylko w miejscach `// QS8:`. Po merge'u CI + regresja na Queście.
