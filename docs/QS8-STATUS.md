# QuestStation8 — stan projektu (2026-11-25)

Punkt startowy dla następnej sesji. Zasady: `CLAUDE.md`. Plan: `docs/QS8-PLAN.md`.
Analizy (profile, jakość obrazu): `docs/QS8-ANALIZY.md`. Podglądy panelu: `docs/podglad/`.

## Repozytorium
- Fork: `oltermcom-afk/QuestStation8` (z `beecrepaldi-afk/P5M` @ `d61c8e6`). Klon: `~/QuestStation8/QuestStation8`.
- `main` = lustro `upstream/main`; `qs8/main` = gałąź domyślna; Actions read/write; Issues włączone.
- Każdy push buduje APK do rolling release `dev` (`p5m.apk` + `p5m-<wersja>-dev.N.apk`; N = numer runu = versionCode) — z niego aktualizuje się aplikacja. `[skip ci]` = bez buildu.
- **Od PR #20 każdy build zostaje na stałe** jako wydanie `qs8-dev.N` (APK, gałąź, PR, commit). Są `qs8-dev.26`–`31` (dev.26 dodane ręcznie); starsze APK skasowane (do odtworzenia powrotem). Tworzenie wydań sprawdzone w CI (dev.27–31). Przetestowane: `gh release edit qs8-dev.N --repo oltermcom-afk/QuestStation8 --title "QS8 dev.N ✅ przetestowana"`.
- **Powrót do wersji:** Actions → Build APK → Run workflow → gałąź ze szczytu stosu (`qs8/main` dopiero po merge'u #20) → `powrot_do` = N. Buduje kod dev.N jako `dev.<nowy>.r<N>` (w launcherze „dev.31 (25)”), instaluje się jak aktualizacja, rejestracja zostaje. **Ścieżka `powrot_do` nigdy nie była uruchomiona.** Ma działać dla każdego udanego builda z historii Actions. Następny zwykły push znów publikuje najnowszy kod w `dev`.
- Wartość referencyjna: `~/QuestStation8/baseline/p5m-0.3.0-beta.apk` (nie commitować). Logo źródłowe: `~/QuestStation8/qs8-logo.png`.

## Stos PR (nic nie zmergowane do `qs8/main` przed testem Damiana)
**Ostatni APK: dev.32** (gałąź `qs8/na-lezaco`, cały stos #6–#18, #20–#25). Szczyt stosu: `qs8/na-lezaco`. `qs8/main` = `ebc6ecb` (#1–#5). **Nowe funkcje wstrzymane** do czasu przetestowania i zmergowania stosu (decyzja Damiana 2026-11-24); jedyny wyjątek: tryb „na leżąco” (#25, Damian 2026-11-25).

| PR | Gałąź | Baza | Co (jedno zdanie) |
|---|---|---|---|
| #1–#5 | — | — | zmergowane: Etap 0, plan, 1F (Kino + centrowanie Meta), aktualizacje z aplikacji, 1B (Touch → panel). |
| #6 | `qs8/teksty-touch` | `qs8/main` | Legenda „przycisk Questa / PS5” w panelu, komunikaty Trybu domowego (1C, bez flagi), „Zgłoś problem” bez logowania (krótki URL + schowek). |
| #7 | `qs8/galki-touch` | #6 | Kliknięcia gałek Touch: lewa = Brightness, prawa = 3D strength. |
| #8 | `qs8/rebranding` | #7 | Logo `ic_qs8` i nazwa QuestStation8 w tekstach widocznych (`qs8/Marka`), Help: „Based on P5M by gblandro”. |
| #9 | `qs8/1e1-account-id` | #8 | 1E-1: Account ID z logowania PSN w goglach (patch 0058 + `qs8/RejestracjaPsn`), Broadcast domyślnie wył. |
| #10 | `qs8/mqsr-auto` | #9 | Sharpness „Automatic” = `AUTO_LAYER_FILTER` + tylko `QUALITY_SHARPENING` (MQSR); bity filtrów w dzienniku (`qs8_warstwa.h`). Filtry wycofane w #17. |
| #11 | `qs8/profile` | #10 | Profile Responsive / Balanced / Ultra smooth / Cinema (bez bitrate'u), „Custom”, podgląd w panelu (`qs8/Profile`). Responsive usunięty w #17. |
| #12 | `qs8/piksel` | #11 | Rozmiar „piksel w piksel”: przytrzymanie A / ✕ w panelu (`qs8/PikselWPiksel`, `qs8/Przytrzymanie`, `qs8/StanPanelu`). |
| #13 | `qs8/ab` | #12 | A/B obraz oryginalny / ulepszony: przytrzymanie X / □ (`qs8/PorownanieAB`). |
| #14 | `qs8/hdr-peak` | #13 | Peak HDR 600/1000/4000 w trakcie gry (przytrzymanie Y / △, wiersz w launcherze), MaxCLL do dziennika (patch 0059). |
| #15 | `qs8/plan-jakosc` | #14 | Plan: filtry kompozytora kosztują GPU; c) chroma „może później”, b) EASU i d) deblocking „nie robimy” (tylko docs). |
| #16 | `qs8/polski` | #15 | Wersja polska / angielska (domyślnie polski, Help › Język), słownik `qs8/Tlumaczenia.kt`. |
| #17 | `qs8/opoznienie` | #16 | Po teście dev.21: „Automatic” znów z zestawem filtrów P5M (MQSR gubił klatki), profil „Responsywność” usunięty (180 Hz: 17 ms wobec 16–17 ms przy 120 Hz — brak zysku). |
| #18 | `qs8/klatki-60` | #17 | Ustawienie „Generowane klatki: do 120 / do 60” dla shader + frames (`qs8/GenerowanieKlatek`, `qs8_klatki.cpp`). |
| #20 | `qs8/wersje` | #18 | Stałe wydanie `qs8-dev.N` dla każdego builda + powrót do wersji (`powrot_do` w workflow, `dev.N.rM`, „dev.N (M)” w launcherze i karcie aktualizacji). (#19 = automatyczne zgłoszenie z nieudanego CI 24, zamknięte 2026-11-24.) |
| #21 | `qs8/panel-zaawansowany` | #20 | Strona 2 panelu w grze „Zaawansowane” (`qs8/PanelZaawansowany`): przytrzymanie Options / ≡; klatki, Hz, HDR peak, siła i głębia 3D, pop-out, głębia neuronowa na żywo; ścieżka, kolor, emulacja 3D od następnego połączenia. |
| #22 | `qs8/mqsr-eksperymentalne` | #21 | Przełącznik „MQSR (eksperymentalne)”: przy ostrości Automatic sam `QUALITY_SHARPENING` (wariant dev.21), na żywo; launcher (pod Sharpness) i strona 2 panelu (`qs8/Mqsr`, `qs8_mqsr.cpp`). |
| #23 | `qs8/poswiata` | #22 | Poświata wokół ekranu (wył./słaba/średnia/mocna): strefy 16×9 z krawędzi obrazu, wygładzone ~0,25 s, warstwa pod ekranem; tylko shader i ciemny pokój; launcher (Ekran) i strona 2 panelu (`qs8_poswiata.{h,cpp}`, `qs8/Poswiata`). |
| #24 | `qs8/poprawki-panelu` | #23 | Poprawki po przeglądzie dev.30: tłumaczenie jasności („Bright” → poświata „Vivid”), rysowane przyciski zamiast ≡ ↕ ↔ ↻, linia „Przytrzymaj” z wejściem na stronę 2, komunikat w wierszu stanu, Options tylko z padem przy Queście, wyścigi głębi neuronowej (`qs8/GlebiaNeuronowa`). |
| #25 | `qs8/na-lezaco` | #24 | Tryb „na leżąco” (na plecach / na boku): ekran według pełnej orientacji i pozycji głowy; przytrzymanie LT / L2 w panelu albo wiersz na stronie 2; tylko bieżąca sesja (`qs8_lezaco.{h,cpp}`, `qs8/NaLezaco`). |

**Merge (decyzja Damiana 2026-11-24):** po jego testach, **PR po PR, squash każdego** (#6 → #18, #20 → #25), żeby dało się cofnąć pojedynczą funkcję. Nie jeden squash całości. Po zmergowaniu #N: sprawdzić, że `qs8/main` jest identyczny z czubkiem #N, `git merge -s ours qs8/main` na następnej gałęzi (`[skip ci]`, bez force-pusha; `rebase --onto` daje to samo), `gh pr edit N+1 --base qs8/main`, dopiero potem merge. Próba na kopii (2026-11-24, #6–#24): zero konfliktów; bez tej synchronizacji 13 z 18 PR-ów ma konflikty. **Squash z jawnym tytułem i opisem**: `[skip ci]` we wszystkich oprócz ostatniego (inaczej każdy merge wystawi w `dev` niepełny kod z wyższym numerem), a w ostatnim bez niego (domyślny opis zbiera komunikaty commitów i przeniósłby `[skip ci]`). `git revert` pojedynczego squasha na końcowym kodzie przeszedł czysto tylko dla #24; #15, #17, #20 mają konflikt tylko w docs, reszta w kodzie. Po merge'ach kodu już przetestowanego nie trzeba nowego testu na Queście; jeśli rebase wymagał ręcznego rozwiązania konfliktu w kodzie, porównać wynik z czubkiem stosu (`git diff`).

## Przetestowane na Queście (Damian)
- dev.1: instalacja obok P5M, rejestracja, Tryb domowy (drugie konto PSN, DualSense przy PS5).
- dev.4 (1F): aktualizacja bez utraty rejestracji; preset Kino raz; centrowanie Meta.
- dev.7 → dev.10 → dev.11 z aplikacji: karta, instalacja, rejestracja zostaje.
- dev.10 / dev.11 (1B): ≡ otwiera panel, regulacja Touch, trzymane B kończy sesję, Touch nie wpływa na grę. **#21 zmienił krótkie ≡ na „po puszczeniu” — wymaga ponownego testu.**
- dev.21 (częściowo, 2026-11-22): przytrzymanie X (A/B) działa. Pomiary: patrz „Ustalenia: 35 ms”.
- dev.23 (2026-11-22): „Generowane klatki: do 60” w grze 30 fps — mniej artefaktów niż „do 120”, bardzo rzadkie szarpnięcie.
- dev.25 (2026-11-22): poprawka rytmu „do 60” gorsza (artefakty, chrupnięcia przy szybkich obrotach; 120 i 180 Hz, 3D bez głębi neuronowej) → cofnięta w dev.26.
- **Wyników dla dev.26–dev.32 nie ma** (Damian przetestuje wszystko razem).

## Tylko skompilowane / CI zielone, bez testu na sprzęcie
- **#17:** brak zgubionych klatek po powrocie filtrów P5M (dev.22+). Pomiar „Zrównoważony 16–17 ms, 0 zgubionych” jest z dev.21.
- **#18 (dev.26+):** „do 60” = logika dev.23 + licznik `QS8: frames rhythm` w dzienniku.
- **#20:** „dev.31 (25)” w launcherze i karcie; `powrot_do`.
- **#21:** strona 2 panelu; przełączanie na żywo (klatki, Hz, 3D, głębia neuronowa); krótkie Options / ≡ po puszczeniu.
- **#22:** MQSR. **#23:** poświata — shadery GLSL kompilują się dopiero na Queście, orientacja góra/dół niepewna. **#24:** poprawki.
- **#25:** cały tryb „na leżąco”. Matematyka i maszyna stanów sprawdzone na atrapie runtime'u (63 936 orientacji, Meta, utrata śledzenia). Niewiadome: czy i jak Horizon OS zmienia LOCAL przy centrowaniu Meta na leżąco, zachowanie po zdjęciu gogli, krótkie L2 / LT po puszczeniu.
- Wcześniejsze: #6–#9, #12, #14, #16, reszta #11 (profili używano tylko podczas pomiarów dev.21).
- „Zgłoś problem” przy niezalogowanym GitHubie (poprawka w #6).
- **Tryb mobilny (pad przy Queście) nigdy nie był testowany w QS8.**
- Podglądy panelu (`docs/podglad/`, `tools/qs8_podglad_panelu.py`) to rekonstrukcja z kodu i fontów Roboto, nie zrzuty z Questa. Układ strony 2 i linii „Przytrzymaj” z #24 sprawdzony tak samo (obrazów nie ma w repo).

## Decyzje i odstępstwa od planu (wszystkie sesje)
- **2026-11-24 (Damian):** nowe funkcje wstrzymane do czasu przetestowania i zmergowania stosu; merge PR po PR (squash każdego); priorytet po merge'u = zamrożony obraz; 35 ms = głębia neuronowa, **3D z siecią tylko jako świadomy wybór**. Uwaga: `neuralDepth` jest w P5M domyślnie **włączone**, więc włączenie samego 3D włącza też sieć. Zmiana domyślnej wartości nie jest zrobiona (po merge'u, do ustalenia); dziś strona 2 pokazuje zmierzony koszt.
- **35 ms:** wcześniejsze hipotezy (MQSR, 180 Hz, samo 3D) były błędne; żadnej poprawki w kodzie, to kod P5M.
- **MQSR:** przyczyna zgubionych klatek z dev.21 nie jest wyizolowana (głębia neuronowa mogła być włączona; Balanced ma inną ostrość niż Automatic) → przełącznik #22.
- **„Do 60”:** diagnoza z dev.25 była nietrafiona (symulacja z niezmierzonymi drganiami). Dalej tylko z linią `QS8: frames rhythm`; jeśli to rytm — `predictedDisplayPeriod` z OpenXR.
- **Wersje (#20):** każdy build zostaje; powrót = nowy wyższy numer (`dev.31.r25`), bez odinstalowania.
- **Strona 2 (#21):** wejście ≡ / Options (△ zajęte przez Peak HDR); bez bitrate'u (automatyczny bitrate P5M).
- **Poświata (#23):** wył./słaba/średnia/mocna; tylko shader i ciemny pokój.
- **Profile gier (odłożone):** obraz + ekran, bez bitrate'u, wbudowane zostają.
- **Limit 3 plików P5M na PR:** #18 i #22 ruszyły po 4 (`CMakeLists.txt`, `tone_mapper.cpp`/`xr_session.cpp`, `VrStreamActivity.kt`, `LauncherActivity.kt`); dla #22 bez pytania Damiana. #23: zgoda na 4. Ponad 3 miały też #8 (9 plików), #14 i #16 (po 4) — zgody nie odnotowano. #25: 3.
- **Keystore:** standardowy format debug Gradle (hasło/alias jak `debug.keystore`), sekret `P5M_DEBUG_KEYSTORE_BASE64`. Token `gh` ma od 2026-11-22 scope `workflow`; workflow zmieniony w #20.
- **1F centrowanie:** `REFERENCE_SPACE_CHANGE_PENDING` (LOCAL) ustawia `recenter_orientation_` na yaw 0. **Preset Kino:** pomijany, gdy `p5m_vr_screen` ma jakikolwiek klucz.
- **Updater:** release `dev` forka (`qs8/AktualizacjeDev.kt`), interwał 15 min.
- **1B:** Touch działa zawsze; JNI w `qs8_touch.cpp`; `qs8TouchKey` nie przekazuje UP do funkcji panelu.
- **1C bez flagi `trybDomowy`:** Tryb domowy = w sesji nie było nazwanego pada (`qs8/TrybDomowy`, reguła `acceptsGamepadAxes` z patcha 0024). Komunikaty mówią o ≡ zawsze (decyzja Damiana). Przełącznik trybu dopiero z 1D.
- **Znaki w panelu:** Roboto nie ma ≡ ↕ ↔ ↻ ✕ □ △; w panelu rysować przyciski (`LegendaTouch`). Teksty launchera z ✕ □ △ (np. opis HDR peak) zostały — czcionka zastępcza Questa, nieweryfikowane.
- **Legenda:** `LegendaTouch.komorka` rysuje komórkę przycisku (Quest / PS5), tekst wierszy +26 px; „≡” w tekście nie jest używany (czcionka) — przycisk menu rysowany kreskami.
- **„Zgłoś problem”:** raport tylko w schowku; URL z krótkim szablonem.
- **Gałki Touch:** bity 13/14 → SELECT/START. Bez przycisku Questa: Adaptive triggers (R3), Touchpad click (PS).
- **Rebranding:** etykiety w manifeście wpisane tekstem; `PsnLoginRelay` zmieniony w surowym HTML bez `// QS8:`; TAG-i, dziennik, `_p5m._tcp`, User-Agent bez zmian.
- **1E-1:** brak Online ID (P5M zapisuje tylko numer konta); poprawka portu Broadcast w libchiaki nie robiona.
- **MQSR (#10 → wycofane w #17):** samo `QUALITY_SHARPENING` w Automatic gubiło klatki na dev.21; #17 przywraca zestaw P5M (`AUTO_LAYER_FILTER` + NORMAL/QUALITY_SHARPENING + NORMAL_SUPER_SAMPLING). Z #10 zostaje dziennik bitów warstwy.
- **Profile (#11):** bez bitrate'u / auto bitrate'u / 3D (decyzja Damiana). Profile: Zrównoważony / Ultra płynność / Kino, wszystkie 120 Hz (Responsywność usunięta w #17: 180 Hz bez zysku). Aktywny profil rozpoznawany po wartościach (toneMapped, tenBit, frameExtrapolation, panel180, sharpness), ekran nie wchodzi do porównania; zmiana ostrości w panelu → „Custom”. Kino: `toneMapped=false` + `tenBit=true` (shader przez 10-bit).
- **Przytrzymania w panelu (#12–#14, #21):** A / ✕ = rozmiar 1:1, X / □ = A/B, Y / △ = Peak HDR, ≡ (Touch) lub Options (pad przy Queście; na Touch kliknięta prawa gałka) = strona 2; próg 0,6 s; krótkie naciśnięcie wykonywane **po puszczeniu** (wcześniej natychmiast). UP pada dalej idzie do konsoli.
- **Na leżąco (#25):** nie rusza matematyki P5M — warstwy i poza głowy (dźwięk) idą w przestrzeni LOCAL przesuniętej do pozy głowy; `recenter_orientation_` zostaje na powrót. „Na plecach”: do 45° w górę / w dół ekran równo względem pokoju, dalej góra ekranu = góra głowy; „na boku”: zawsze według głowy. Zdarzenie zmiany LOCAL = ponowne centrowanie od `changeTime`. Nie zapisywane; koniec sesji kasuje. Przytrzymanie LT / L2 (krótkie = kształt, po puszczeniu) działa na obu stronach panelu.
- **A/B:** tylko filtry (kompozytor Off, CAS 0), tylko w sesji, bez zapisu.
- **Peak HDR:** `Qs8Prefs.peakHdr` (domyślnie 1000 jak P5M), atomik w `qs8_hdr.cpp`, uniform `uPicoFonte` przy każdym rysowaniu.
- **Tłumaczenia (#16):** tłumacz ekranów 2D w `P5MApp` (OnPreDraw na każdej aktywności, TextView bez EditText/Spanned) zamiast hooków w setkach miejsc; panel/paski przez `t()` w `PainelDeAjuste` (desenhar, cabecalho, secao, medidores, faixa). `conferir.py` pomija `qs8/Tlumaczenia.kt`.

## Ustalenia: 35 ms (pomiary Damiana, dev.21, 2026-11-22)
Head-to-photon: direct 120 Hz 16–17 ms; direct 180 Hz 17 ms, 0 zgubionych; shader (10-bit) 180 Hz 18–20 ms; 3D bez sieci — bez skoku; **3D z głębią z sieci neuronowej 35–36 ms i wyższe CPU/GPU**.

## Znane błędy / otwarte sprawy
- **ZAMROŻONY OBRAZ (priorytet po merge'u; odłożony przez Damiana 2026-11-24).** Objaw: pad zgasł i został włączony ponownie, obraz w goglach stoi, TV działa; pomaga wyjście i „Graj”. Także w starszych wersjach; build nieznany, dziennika brak, **przyczyna niepotwierdzona**.
  - Luka (z czytania kodu): `lib/src/streamconnection.c` obsługuje `CHIAKI_TAKION_EVENT_TYPE_DISCONNECT` tylko w `STATE_TAKION_CONNECT`, więc śmierć wątku odbioru w trakcie gry nie kończy sesji; `takion_recv` bez limitu ciszy; po 25 próbach wysyłki tylko wpis w logu; `ctrl_failed` nie przerywa trwającego strumienia. Żaden patch tego nie zmienia.
  - Warianty: A) martwa sesja (pad przy PS5), B) sesja żyje, stoi dekoder/wyświetlanie (pad przy Queście), C) PS5 wstrzymał obraz. Niesprawdzone: `MovimentoDualSense`, `EspelhoDualSense`, haptyka przy powrocie pada.
  - Plan: (1) strażnik obrazu QS8 — wiek ostatniej zdekodowanej klatki (np. w `p5m_video_wait_for_display`, woła go każda klatka na obu ścieżkach), po ~3 s napis i wpis, czy pakiety przychodzą; (2) nowy patch na lukę w `streamconnection.c`; (3) **`display_sink`:** Android go nie ustawia, a `ctrl.c` (linie ~995, 1005, 1467) woła `cantdisplay_cb` bez sprawdzenia — sesja jest zerowana `memset`, więc możliwe wywołanie NULL (crash), gdy PS5 pokaże ekran, którego nie wolno strumieniować; (4) automatyczne łączenie dopiero z dziennikiem.
  - Od Damiana potrzebne: pad przy PS5 czy Queście; czy zdjęte gogle i na jak długo; czy był dźwięk i działał panel (≡); „Zgłoś problem” przed kolejnym „Graj”.
- **Rzadkie szarpnięcie „do 60”:** przyczyna nieustalona (gra/sieć albo rytm).
- Nieprzetłumaczone (zostają po angielsku): długie teksty Diagnostyki (`DiagnosticActivity`, instrukcje „how to send this”, raport), ekran testów (test bench), kalibracja gałek, długie opisy w Połączeniu zdalnym (PSN) i strona logowania na telefonie, karta „Build dev.N from …” (opis z GitHuba), dynamiczne wartości bez wzoru, ekrany chiaki-ng (rejestracja poza wskazówkami 1E-1, lista konsol, ustawienia chiaki), dziennik (celowo).
- Ikona `ic_qs8` = całe logo z napisem; przy 48–72 px napis nieczytelny (wariant z samym emblematem Q8 do rozważenia).
- Skrypty `tools/` ze starym pakietem `io.github.gblandro.p5m`: `bitrate-latencia.sh`, `instalar-local.sh`, `tools/return/*`, `tools/haptics/*`.
- Tekst rolującego release `dev` (`build-apk.yml`) mówi „P5M” i o L3+R3+R1 (scope `workflow` już jest).
- Brak sekretów do tagów `v*` → tag nie przejdzie CI. Klucz tagów ≠ klucz dev.
- W rolującym `dev` asset `p5m-…-dev.N.apk` jest kasowany przy kolejnym buildzie → stara karta daje „HTTP 404”; wyłączyć/włączyć sprawdzanie. W wydaniu `qs8-dev.N` zostaje.
- MaxCLL: nie wiadomo, czy dekoder Questa podaje `hdr-static-info` — linia w dzienniku powie.
- Głębia z sieci neuronowej (kod P5M): koszt jak w „Ustalenia: 35 ms”. Na stronie 2 jest informacja; w launcherze nie ma.

## Pułapki w kodzie P5M
- `VrStreamActivity`: `handleAdjustKey`, `handleQuitHold`, `teclaNoPin` przekazują **ACTION_UP do `StreamInput`** (konsola). Każde nowe źródło wejścia „tylko dla panelu” podaje im wyłącznie DOWN. `toggleAdjustMode` → `releaseChordButtons` → konsola.
- `handleAdjustKey`: `KEYCODE_BUTTON_MODE` → `clickTouchpad()` (konsola). Nie emitować z Touch.
- `qs8Hold.przechwyc` stoi w `handleAdjustKey` przed blokiem UP: dla UP zwraca false (P5M oddaje UP konsoli), dla DOWN A/X/Y zwraca true.
- `teclaNoPin` → zatwierdzenie wysyła `setLoginPin` — zamierzone.
- `CURL_CA_BUNDLE` i `CaBundle.COMPILED_PATH` muszą się zgadzać z applicationId (`conferir.py`).
- `conferir.py`: JNI tylko w `p5m/*.kt` i `qs8/*.kt` (klasa JNI z prefiksem `qs8_`); „Interface em ingles” szuka portugalskiego (słów i akcentów, np. „ó”) w stringach — polskie teksty tylko w `qs8/Tlumaczenia.kt`.
- `compilar_nativo.py` kompiluje każdy `.cpp` z `app/src/main/cpp`; Kotlin sprawdza dopiero CI (Kotlin 1.8 / AGP 7.4 / JDK 17). Lokalnie brak JDK w PATH.
- Nowy patch chiaki-ng: nałożyć po kolei wszystkie `patches/*.patch` (`git apply`) w `external/chiaki-ng`, zmienić, `git diff --src-prefix=a/ --dst-prefix=b/ > patches/00NN-….patch`, potem `git reset --hard && git clean -fd` w submodule.
- Launcher porównuje **wartości z lambd** (`v == "Off"`), nie tekst widoków — dlatego tłumacz działa na TextView, a nie na lambdach.
- `PresetKino` zna nazwę pliku `p5m_vr_screen` z `ScreenPrefs` — przy merge'u z upstreamem sprawdzić.
- Geometria panelu (`PainelDeAjuste`: margines 52, 5 kolumn, odstęp 44, wiersze 170/250/330) jest powielona w `LegendaTouch`, `TouchMenu`, `StanPanelu` i `tools/qs8_podglad_panelu.py`.

- Klasa JNI musi mieć własny plik o tej samej nazwie (`conferir.py`); GCC w CI jest surowszy od lokalnego clanga (`<iterator>`, dev.24).
- `Tlumaczenia.t` szuka najpierw dokładnego klucza, potem wariantu z małej litery: nowy klucz „Xyz” przejmuje istniejące „xyz” wyświetlane z wielkiej (błąd „Bright”, #24). Przed dodaniem sprawdzić warianty wielkości liter.
- Po buildzie `powrot_do` następny zwykły push nadpisuje rolujące `dev`.
- `ProfundidadeNeural.stop` czeka najwyżej 1 s, a wczytanie modelu trwa dłużej — start w trakcie sesji tylko przez `qs8/GlebiaNeuronowa` (zna nazwę wątku `p5m-neural-depth`; sprawdzić przy merge'u z upstreamem).
- Poświata: stan w `qs8_poswiata.cpp` jest statyczny (jeden wątek pętli klatek); sprzątanie po `tone_mapper_.Destroy()`.

## Pliki QS8
- Kotlin `qs8/`: `Qs8Prefs, PresetKino, AktualizacjeDev, TouchNative, TouchMenu, LegendaTouch, TrybDomowy, Marka, RejestracjaPsn, Profile, Przytrzymanie, StanPanelu, PikselWPiksel, PorownanieAB, HdrNative, PeakHdr, Tlumaczenia, KlatkiNative, GenerowanieKlatek, PanelZaawansowany, Mqsr, MqsrNative, Poswiata, PoswiataNative, GlebiaNeuronowa, NaLezaco, NaLezacoNative`.
- Native: `qs8_touch.{h,cpp}`, `qs8_warstwa.h`, `qs8_hdr.{h,cpp}`, `qs8_klatki.{h,cpp}`, `qs8_mqsr.cpp`, `qs8_poswiata.{h,cpp}`, `qs8_lezaco.{h,cpp}`. Zasoby: `res/mipmap-*/ic_qs8.png`. Patche: `0058` (rejestracja), `0059` (MaxCLL).
- Punkty `// QS8:` / `# QS8:` (lista: `git grep -n "QS8:"`): `.github/workflows/build-apk.yml`, `app/build.gradle`, `AndroidManifest.xml`, `CaBundle.kt`, `DiagnosticActivity.kt`, `InstaladorDeAtualizacao.kt`, `Atualizacao.kt`, `LauncherActivity.kt`, `CrashCatcher.kt`, `Estilo.kt`, `PainelDeAjuste.kt`, `LogServer.kt`, `PsnLoginRelay.kt`, `PsnRemoteActivity.kt`, `VrStreamActivity.kt`, `xr_session.cpp`, `tone_mapper.cpp`, `CMakeLists.txt`, `tools/conferir.py`.

## Klucz podpisu
- Lokalna kopia: `~/QuestStation8/klucze/qs8-debug.keystore` (poza repo). W GitHub: `P5M_DEBUG_KEYSTORE_BASE64`.

## Następne kroki (kolejność)
1. Test Damiana na **dev.32** (jeden APK, cały stos). Kolejność: zwykła gra i krótkie ≡ → strona 2 → poświata (orientacja!) → MQSR bez głębi neuronowej → „do 60” z dziennikiem → na leżąco (lista w PR #25) → wcześniejsze #6–#16 → tryb mobilny.
2. Poprawki po teście (małe PR-y na szczycie stosu). Bez nowych funkcji.
3. Jedna próba `powrot_do` (uruchamia Damian albo za jego zgodą).
4. Merge PR po PR (squash każdego), patrz „Merge”.
5. **Zamrożony obraz:** strażnik obrazu + patch na `streamconnection.c` + `display_sink`.
6. Domyślna głębia neuronowa („świadomy wybór”) — ustalić z Damianem.
7. Odłożone funkcje: nazwane profile gier (PLAN, „Kolejka 2026-11-22”); poświata: czarne pasy, barwienie pokoju.
8. 1D: ekran startowy „Tryb domowy / Tryb mobilny” (+ ostatni profil), z nim flaga Trybu domowego.
9. 1E-2: rejestracja bez PIN-u (`auto_regist` przez JNI) — najpierw opis chiaki-ng desktop i propozycja zakresu.
10. Później: c) chroma sterowana lumą (jeśli A/B pokaże potrzebę), ikona z samym emblematem, dokończenie tłumaczeń (Diagnostyka, PSN).
