# QuestStation8 — stan projektu (2026-11-21)

Punkt startowy dla następnej sesji. Zasady: `CLAUDE.md`. Plan: `docs/QS8-PLAN.md`.

## Repozytorium
- Fork: `oltermcom-afk/QuestStation8` (z `beecrepaldi-afk/P5M` @ `d61c8e6`). Klon lokalny: `~/QuestStation8/QuestStation8`.
- `main` = lustro `upstream/main`; `qs8/main` = gałąź domyślna; Actions read/write; Issues włączone.
- Każdy push (każda gałąź) buduje APK do **jednego** rolling release `dev` (`p5m.apk` + `p5m-<wersja>-dev.N.apk`; N = numer runu = versionCode). Ostatni build nadpisuje poprzedni — kolejność pushy ma znaczenie. `[skip ci]` w commicie pomija build.
- Wartość referencyjna: `~/QuestStation8/baseline/p5m-0.3.0-beta.apk` (nie commitować).

## PR
| PR | Stan | Co |
|---|---|---|
| #1 | zmergowany | Etap 0: applicationId `pl.olterm.queststation8`, authorities z `${applicationId}`, nazwa „QuestStation8”, ścieżka CA, updater na fork, CLAUDE.md + plan. |
| #2 | zmergowany | Nowa wersja planu; „Zgłoś problem” → issues forka. |
| #3 | zmergowany | 1F: jednorazowy preset ekranu „Kino” + centrowanie przytrzymaniem Meta. |
| #4 | zmergowany | Aktualizacje z aplikacji z release `dev` (karta: numer, gałąź/PR, tytuł commita). |
| #5 | zmergowany | 1B: kontrolery Touch → panel ustawień (+ ten plik). Przetestowany na dev.10. |
| #6 | **otwarty** | Legenda „Quest / PS5” w panelu, komunikaty Trybu domowego (1C), „Zgłoś problem” bez logowania. |
| #7 | **otwarty** (na #6) | Kliknięcia gałek Touch: lewa = Brightness, prawa = 3D strength. |
| #8 | **otwarty** (na #7) | Logo `ic_qs8` i nazwa QuestStation8 w tekstach widocznych (`qs8/Marka`). |
| #9 | **otwarty** (na #8) | 1E-1: Account ID z logowania PSN (patch 0058 + `qs8/RejestracjaPsn`). APK dev.15 = cały stos. |

Stos PR #6 → #7 → #8 → #9: Damian testuje jeden APK (ostatni build), merge po teście w kolejności #6, #7, #8, #9 (po merge'u #6 zmienić bazę #7 na `qs8/main` itd.). Niczego nie mergować przed testem.

## Przetestowane na Queście (Damian)
- dev.1: instalacja obok P5M, rejestracja, Tryb domowy (drugie konto PSN, DualSense przy PS5).
- dev.4 (1F): aktualizacja na dev.1 bez odinstalowania i bez utraty rejestracji; preset Kino; preset tylko raz; centrowanie Meta.
- „Zgłoś problem”: **częściowo** (patrz znane błędy).
- dev.7 (updater): zainstalowany ręcznie.
- dev.7 → dev.10 z aplikacji: karta się pojawia, instalacja z aplikacji działa, rejestracja konsoli zostaje.
- dev.10 (1B): ≡ otwiera panel, regulacja ekranu Touch działa, trzymane B kończy sesję, przyciski Questa nie wpływają na grę.
  Uwaga z testu: legenda panelu pokazuje tylko przyciski PS5 → myli przy Touch (do poprawy razem z 1C).

## Tylko skompilowane / CI zielone, bez testu na sprzęcie
- **Tryb mobilny (pad przy Queście) nigdy nie był testowany w QS8.**
- PR #6–#9 (dev.15), do testu razem: legenda w wierszach i nagłówku, przypomnienie startowe (z padem / bez), brak „CONTROLLER DISCONNECTED” w Trybie domowym, „Zgłoś problem” bez logowania.

## Decyzje i odstępstwa od planu
- **Keystore:** standardowy format debug Gradle (hasło i alias jak domyślny `debug.keystore`), bo workflow P5M wkłada sekret do `~/.android/debug.keystore` i podpisuje domyślną konfiguracją debug. Własne hasło wymagałoby zmian w workflow i tokenu gh ze scope `workflow` (obecny go nie ma). Ochrona = sam plik. Sekret: `P5M_DEBUG_KEYSTORE_BASE64`. SHA-256 certyfikatu: `07:B7:A8:BD:…:1D:4C:49:0F:D3`.
- **Workflow (`build-apk.yml`) niezmieniony** — token gh nie ma scope `workflow`; zmiany workflow wymagają `gh auth refresh -s workflow` przez Damiana.
- **1F centrowanie:** zamiast `Recenter()` zdarzenie `REFERENCE_SPACE_CHANGE_PENDING` (LOCAL) ustawia `recenter_orientation_` na yaw 0. `Recenter()` mierzy głowę w najbliższej klatce, która może wypaść przed `changeTime` (stara przestrzeń).
- **1F preset:** pomijany, jeśli plik `p5m_vr_screen` ma jakikolwiek klucz (zapisuje go tylko panel = ręczne ustawienie). Wartości domyślne P5M nietknięte.
- **Updater:** źródło oferty = release `dev` forka (`qs8/AktualizacjeDev.kt`); buildy `-dev` dostają kartę (lokalne `-local` nie); interwał 15 min (było 6 h); opis z API Actions (`runs?head_sha=`), bez logowania, 2 zapytania. Wyłącznik + „Wyłącz/włącz” wymusza sprawdzenie. „Skip this version” bez zmian.
- **1B — Touch działa zawsze** (flaga Trybu domowego dopiero w 1C). Przy zamkniętym panelu reaguje tylko ≡.
- **1B — JNI w `qs8_touch.cpp`, nie w `p5m_jni.cpp`;** `tools/conferir.py` sprawdza też `qs8/*.kt` (klasa JNI z prefiksem `qs8_`).
- **1C bez flagi `trybDomowy`:** Tryb domowy = w sesji nie było nazwanego pada (`qs8/TrybDomowy.kt`, reguła `acceptsGamepadAxes` z patcha 0024). Przełącznik trybu dopiero z 1D. Damian chciał, by komunikaty mówiły o ≡ zawsze (także z padem).
- **Legenda:** `LegendaTouch.komorka` rysuje całą komórkę przycisku (Quest / PS5) zamiast P5M, tekst wierszy przesunięty o 26 px; nagłówek z ≡ / akord i B / ○. Znak „≡” w tekście nie jest używany (czcionka może go nie mieć) — przycisk menu rysowany jako trzy kreski.
- **„Zgłoś problem”:** raport już nie idzie w URL; szablon + schowek. `REPORT_URL_BUDGET` w P5M zostaje nieużywany (ostrzeżenie kompilatora, bez skutków).
- **1B — `qs8TouchKey` nie przekazuje UP** do funkcji panelu i nie woła `toggleAdjustMode` (patrz pułapki).

## Znane błędy / otwarte sprawy
- „Zgłoś problem” bez zalogowania: poprawka w PR #6 (krótki URL, raport w schowku) — czeka na test.
- Skrypty `tools/` ze starym pakietem `io.github.gblandro.p5m`: `bitrate-latencia.sh`, `instalar-local.sh`, `tools/return/*`, `tools/haptics/*`.
- Tekst release (`build-apk.yml`) mówi „P5M” i o L3+R3+R1 — wymaga scope `workflow`.
- Brak sekretów do tagów `v*` (`P5M_KEYSTORE_BASE64`, `P5M_KEYSTORE_PASSWORD`) → tag nie przejdzie CI. Klucz tagów ≠ klucz dev (przejście = reinstalacja).
- Asset `p5m-…-dev.N.apk` jest kasowany przy kolejnym buildzie → karta ze starym N daje „HTTP 404”; wyłączyć/włączyć sprawdzanie.
- Tytuł commita na karcie dev.10 to commit z poprawką kompilacji (gałąź `qs8/1b-touch` jest poprawna).
- Ikona `ic_qs8` = całe logo z napisem; przy 48–72 px napis nieczytelny (wariant z samym emblematem Q8 do rozważenia).
- Rebranding: etykiety aktywności w manifeście wpisane tekstem (manifest nie czyta stałej `Marka.NAZWA`); strona logowania PSN na telefonie (`PsnLoginRelay`) zmieniona w surowym HTML bez komentarza `// QS8:`.
- 1E-1: brak nazwy konta (Online ID) — P5M zapisuje tylko numer konta; poprawka portu Broadcast w libchiaki nie robiona (Broadcast domyślnie odznaczony).
- Akcje bez przycisku Questa: Adaptive triggers (R3), Touchpad click (PS).
- Teksty UI QS8 po angielsku (jak P5M); tłumaczenie później wg planu.

## Pułapki w kodzie P5M
- `VrStreamActivity`: `handleAdjustKey`, `handleQuitHold`, `teclaNoPin` przekazują **ACTION_UP do `StreamInput`** (konsola), żeby pad nie zostawił wciśniętego przycisku. `toggleAdjustMode` → `releaseChordButtons` → `setControllerState` (konsola). Każde nowe źródło wejścia „tylko dla panelu” musi podawać im wyłącznie DOWN.
- `handleAdjustKey`: `KEYCODE_BUTTON_MODE` → `clickTouchpad()` (konsola). Nie emitować go z Touch.
- `teclaNoPin` → zatwierdzenie wysyła `setLoginPin` (kod logowania do konsoli) — zamierzone.
- `CURL_CA_BUNDLE` w `app/build.gradle` i `CaBundle.COMPILED_PATH` muszą się zgadzać z applicationId (`conferir.py` to sprawdza).
- `conferir.py` bada JNI tylko w `p5m/*.kt` i `qs8/*.kt`; „Interface em ingles” szuka portugalskiego w UI.
- `compilar_nativo.py` kompiluje każdy `.cpp` z `app/src/main/cpp` (prawdziwe nagłówki OpenXR/GL), Kotlin sprawdza dopiero CI.
- Kotlin 1.8 / AGP 7.4 / JDK 17 w CI; lokalnie brak JDK w PATH (`/opt/homebrew/opt/openjdk@21` do keytool/apksigner).
- `PresetKino` zna nazwę pliku `p5m_vr_screen` z `ScreenPrefs` — przy merge'u z upstreamem sprawdzić, czy się nie zmieniła.

## Pliki QS8
- Nowe: `qs8/{Qs8Prefs,PresetKino,AktualizacjeDev,TouchNative,TouchMenu,LegendaTouch,TrybDomowy,Marka,RejestracjaPsn}.kt`, `res/mipmap-*/ic_qs8.png`, `patches/0058-qs8-rejestracja-z-kontem-psn.patch`; podgląd panelu: `tools/qs8_podglad_panelu.py` → `docs/podglad/`, `cpp/qs8_touch.{h,cpp}`.
- Punkty `// QS8:` w: `app/build.gradle`, `AndroidManifest.xml`, `CaBundle.kt`, `DiagnosticActivity.kt`, `InstaladorDeAtualizacao.kt`, `PainelDeAjuste.kt`, `Estilo.kt`, `LogServer.kt`, `PsnLoginRelay.kt`, `PsnRemoteActivity.kt`, `Atualizacao.kt`, `LauncherActivity.kt`, `CrashCatcher.kt` (`P5MApp`), `xr_session.cpp`, `CMakeLists.txt`, `VrStreamActivity.kt`, `tools/conferir.py`. Lista: `git grep -n "QS8:"`.

## Klucz podpisu
- Lokalna kopia: `~/QuestStation8/klucze/qs8-debug.keystore` (poza repo; `*.keystore` w `.gitignore`).
- W GitHub: sekret `P5M_DEBUG_KEYSTORE_BASE64`.

## Następne kroki
1. Test dev.15 (PR #6–#9) → merge stosu.
2. Decyzje z `docs/QS8-ANALIZY.md` (profile, jakość obrazu).
3. 1E-1 zrobione; dalej (Account ID z logowania PSN), potem 1D; Etap 2.
