# QuestStation8 — stan projektu (2026-11-22)

Punkt startowy dla następnej sesji. Zasady: `CLAUDE.md`. Plan: `docs/QS8-PLAN.md`.
Analizy (profile, jakość obrazu): `docs/QS8-ANALIZY.md`. Podglądy panelu: `docs/podglad/`.

## Repozytorium
- Fork: `oltermcom-afk/QuestStation8` (z `beecrepaldi-afk/P5M` @ `d61c8e6`). Klon: `~/QuestStation8/QuestStation8`.
- `main` = lustro `upstream/main`; `qs8/main` = gałąź domyślna; Actions read/write; Issues włączone.
- Każdy push buduje APK do **jednego** rolling release `dev` (`p5m.apk` + `p5m-<wersja>-dev.N.apk`; N = numer runu = versionCode). Ostatni build nadpisuje poprzedni. `[skip ci]` = bez buildu.
- Wartość referencyjna: `~/QuestStation8/baseline/p5m-0.3.0-beta.apk` (nie commitować). Logo źródłowe: `~/QuestStation8/qs8-logo.png`.

## Stos PR (nic nie zmergowane do `qs8/main` przed testem Damiana)
**Ostatni APK: dev.22** (gałąź `qs8/opoznienie`, cały stos #6–#17). Szczyt stosu: `qs8/opoznienie`.

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
| #17 | `qs8/opoznienie` | #16 | Po teście dev.21: „Automatic” znów z zestawem filtrów P5M (MQSR gubił klatki), profil „Responsywność” usunięty (wniosek okazał się błędny: 35 ms dawało 3D — do decyzji, czy wraca). |

**Merge po teście:** po kolei #6 → #17 (squash). Po zmergowaniu #N zmienić bazę #N+1 na `qs8/main` (`gh pr edit N+1 --base qs8/main`), potem merge. Squash tworzy nowe commity — kolejne PR-y mogą wymagać `git rebase --onto qs8/main <stara-baza> <gałąź>`; prościej: zmergować tylko szczyt (#17) do `qs8/main` jako jeden squash, a #6–#16 zamknąć z linkiem (Damian decyduje). Cofanie pojedynczego zadania przed merge'em: zamknąć PR i przestawić bazę następnego.

## Przetestowane na Queście (Damian)
- dev.1: instalacja obok P5M, rejestracja, Tryb domowy (drugie konto PSN, DualSense przy PS5).
- dev.4 (1F): aktualizacja bez utraty rejestracji; preset Kino raz; centrowanie Meta.
- dev.7 → dev.10 → dev.11 z aplikacji: karta, instalacja, rejestracja zostaje.
- dev.10 / dev.11 (1B): ≡ otwiera panel, regulacja Touch, trzymane B kończy sesję, Touch nie wpływa na grę.
- dev.21 (częściowo, 2026-11-22): przytrzymanie X (A/B) działa. Pomiary kompozytora: 35–36 ms head-to-photon i wyższe CPU/GPU = włączona emulacja 3D (nie 180 Hz); bez 3D: direct 120 Hz 16–17 ms, shader 180 Hz 18–20 ms; MQSR-only w Automatic gubi 0–4 klatki, tryb A (bez filtra) 0 → poprawki w #17.

## Tylko skompilowane / CI zielone, bez testu na sprzęcie
- Reszta PR #6–#17 (APK dev.22). Lista testów: raport końcowy sesji / sekcja niżej.
- „Zgłoś problem” przy niezalogowanym GitHubie (poprawka w #6).
- **Tryb mobilny (pad przy Queście) nigdy nie był testowany w QS8.**
- Podglądy panelu (`docs/podglad/`, skrypt `tools/qs8_podglad_panelu.py`) to rekonstrukcja z kodu i fontów Roboto, nie zrzuty z Questa; wersja polska panelu niepodglądana (szerokości policzone).

## Decyzje i odstępstwa od planu (wszystkie sesje)
- **Keystore:** standardowy format debug Gradle (hasło/alias jak `debug.keystore`), sekret `P5M_DEBUG_KEYSTORE_BASE64`. Workflow niezmieniony (token gh bez scope `workflow`).
- **1F centrowanie:** `REFERENCE_SPACE_CHANGE_PENDING` (LOCAL) ustawia `recenter_orientation_` na yaw 0. **Preset Kino:** pomijany, gdy `p5m_vr_screen` ma jakikolwiek klucz.
- **Updater:** release `dev` forka (`qs8/AktualizacjeDev.kt`), interwał 15 min.
- **1B:** Touch działa zawsze; JNI w `qs8_touch.cpp`; `qs8TouchKey` nie przekazuje UP do funkcji panelu.
- **1C bez flagi `trybDomowy`:** Tryb domowy = w sesji nie było nazwanego pada (`qs8/TrybDomowy`, reguła `acceptsGamepadAxes` z patcha 0024). Komunikaty mówią o ≡ zawsze (decyzja Damiana). Przełącznik trybu dopiero z 1D.
- **Legenda:** `LegendaTouch.komorka` rysuje komórkę przycisku (Quest / PS5), tekst wierszy +26 px; „≡” w tekście nie jest używany (czcionka) — przycisk menu rysowany kreskami.
- **„Zgłoś problem”:** raport tylko w schowku; URL z krótkim szablonem.
- **Gałki Touch:** bity 13/14 → SELECT/START. Bez przycisku Questa: Adaptive triggers (R3), Touchpad click (PS).
- **Rebranding:** etykiety w manifeście wpisane tekstem; `PsnLoginRelay` zmieniony w surowym HTML bez `// QS8:`; TAG-i, dziennik, `_p5m._tcp`, User-Agent bez zmian.
- **1E-1:** brak Online ID (P5M zapisuje tylko numer konta); poprawka portu Broadcast w libchiaki nie robiona.
- **MQSR (#10 → wycofane w #17):** samo `QUALITY_SHARPENING` w Automatic gubiło klatki na dev.21; #17 przywraca zestaw P5M (`AUTO_LAYER_FILTER` + NORMAL/QUALITY_SHARPENING + NORMAL_SUPER_SAMPLING). Z #10 zostaje dziennik bitów warstwy.
- **Profile (#11):** bez bitrate'u / auto bitrate'u / 3D (decyzja Damiana). Profile: Zrównoważony / Ultra płynność / Kino, wszystkie 120 Hz (Responsywność usunięta w #17; 35 ms dawało 3D, nie 180 Hz — do decyzji). Aktywny profil rozpoznawany po wartościach (toneMapped, tenBit, frameExtrapolation, panel180, sharpness), ekran nie wchodzi do porównania; zmiana ostrości w panelu → „Custom”. Kino: `toneMapped=false` + `tenBit=true` (shader przez 10-bit).
- **Przytrzymania w panelu (#12–#14):** A / ✕ = rozmiar 1:1, X / □ = A/B, Y / △ = Peak HDR; próg 0,6 s; krótkie naciśnięcie wykonywane **po puszczeniu** (wcześniej natychmiast). UP pada dalej idzie do konsoli.
- **A/B:** tylko filtry (kompozytor Off, CAS 0), tylko w sesji, bez zapisu.
- **Peak HDR:** `Qs8Prefs.peakHdr` (domyślnie 1000 jak P5M), atomik w `qs8_hdr.cpp`, uniform `uPicoFonte` przy każdym rysowaniu.
- **Tłumaczenia (#16):** tłumacz ekranów 2D w `P5MApp` (OnPreDraw na każdej aktywności, TextView bez EditText/Spanned) zamiast hooków w setkach miejsc; panel/paski przez `t()` w `PainelDeAjuste` (desenhar, cabecalho, secao, medidores, faixa). `conferir.py` pomija `qs8/Tlumaczenia.kt`.

## Znane błędy / otwarte sprawy
- Nieprzetłumaczone (zostają po angielsku): długie teksty Diagnostyki (`DiagnosticActivity`, instrukcje „how to send this”, raport), ekran testów (test bench), kalibracja gałek, długie opisy w Połączeniu zdalnym (PSN) i strona logowania na telefonie, karta „Build dev.N from …” (opis z GitHuba), dynamiczne wartości bez wzoru, ekrany chiaki-ng (rejestracja poza wskazówkami 1E-1, lista konsol, ustawienia chiaki), dziennik (celowo).
- Ikona `ic_qs8` = całe logo z napisem; przy 48–72 px napis nieczytelny (wariant z samym emblematem Q8 do rozważenia).
- Skrypty `tools/` ze starym pakietem `io.github.gblandro.p5m`: `bitrate-latencia.sh`, `instalar-local.sh`, `tools/return/*`, `tools/haptics/*`.
- Tekst release (`build-apk.yml`) mówi „P5M” i o L3+R3+R1 — wymaga scope `workflow`.
- Brak sekretów do tagów `v*` → tag nie przejdzie CI. Klucz tagów ≠ klucz dev.
- Asset `p5m-…-dev.N.apk` kasowany przy kolejnym buildzie → stara karta daje „HTTP 404”; wyłączyć/włączyć sprawdzanie.
- MaxCLL: nie wiadomo, czy dekoder Questa podaje `hdr-static-info` — linia w dzienniku powie.
- Emulacja 3D: 35–36 ms head-to-photon i więcej CPU/GPU (kod P5M). Profil Responsywność wraca, jeśli direct + 180 Hz bez 3D będzie szybsze niż 16–17 ms.

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

## Pliki QS8
- Kotlin `qs8/`: `Qs8Prefs, PresetKino, AktualizacjeDev, TouchNative, TouchMenu, LegendaTouch, TrybDomowy, Marka, RejestracjaPsn, Profile, Przytrzymanie, StanPanelu, PikselWPiksel, PorownanieAB, HdrNative, PeakHdr, Tlumaczenia`.
- Native: `qs8_touch.{h,cpp}`, `qs8_warstwa.h`, `qs8_hdr.{h,cpp}`. Zasoby: `res/mipmap-*/ic_qs8.png`. Patche: `0058` (rejestracja), `0059` (MaxCLL).
- Punkty `// QS8:` (lista: `git grep -n "QS8:"`): `app/build.gradle`, `AndroidManifest.xml`, `CaBundle.kt`, `DiagnosticActivity.kt`, `InstaladorDeAtualizacao.kt`, `Atualizacao.kt`, `LauncherActivity.kt`, `CrashCatcher.kt`, `Estilo.kt`, `PainelDeAjuste.kt`, `LogServer.kt`, `PsnLoginRelay.kt`, `PsnRemoteActivity.kt`, `VrStreamActivity.kt`, `xr_session.cpp`, `tone_mapper.cpp`, `CMakeLists.txt`, `tools/conferir.py`.

## Klucz podpisu
- Lokalna kopia: `~/QuestStation8/klucze/qs8-debug.keystore` (poza repo). W GitHub: `P5M_DEBUG_KEYSTORE_BASE64`.

## Następne kroki (kolejność)
1. Dokończyć test na dev.22 (cały stos #6–#17; dev.21 sprawdzony częściowo) → decyzja o merge'u stosu.
2. Poprawki po teście (nowe PR-y na szczycie stosu albo na `qs8/main` po merge'u).
3. 1D: ekran startowy „Tryb domowy / Tryb mobilny” (+ ostatni profil), z nim flaga Trybu domowego.
4. 1E-2: rejestracja bez PIN-u (`auto_regist` przez JNI) — najpierw opis chiaki-ng desktop i propozycja zakresu.
5. Test trybu mobilnego (pad sparowany z Questem).
6. Później: c) chroma sterowana lumą (jeśli A/B pokaże potrzebę), ikona z samym emblematem, dokończenie tłumaczeń (Diagnostyka, PSN).
