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
  sesji), X → `BUTTON_X`, Y → `BUTTON_Y`, gripy → `L1`/`R1`, ≡ → `toggleAdjustMode()`.
- **Krytyczne:** zdarzenia idą WYŁĄCZNIE do `toggleAdjustMode`, `handleAdjustKey`,
  `handleQuitHold`, `teclaNoPin` (`VrStreamActivity.kt` ~l.1699–1790) przez jedną nową
  metodę `qs8TouchKey(event)`. Nigdy do `dispatchKeyEvent`/`StreamInput` — inaczej
  kontroler Questa sterowałby konsolą jako gracz 2.
- Odpytywanie na istniejącym zegarze aktywności lub wątku tła, nie blokująco na głównym.

### 1E. Rejestracja jak w Asobi
- Na ekranie rejestracji (`external/chiaki-ng/android/.../regist/RegistActivity.kt`,
  zmiana przez nowy plik w `patches/`) przycisk „Zaloguj przez PSN” → istniejący
  przepływ `PsnAuth`/`PsnRemoteActivity` → pole Account ID wypełnia się
  `Base64.encodeToString(PsnAuth.accountId(ctx), NO_WRAP)`. Logika w `qs8/RejestracjaPsn.kt`.
- Wskazówka: „PIN wygeneruj na PS5 na koncie, na które się logujesz”.
- Możliwość przelogowania na inne konto.
- „Broadcast” domyślnie odznaczony. Poprawkę portu w libchiaki robić tylko, jeśli
  patch `0053` ani aktualny upstream jej nie zawiera.

### 1C. Tryb domowy jako tryb, nie błąd
- `qs8/Qs8Prefs.kt`: `trybDomowy` (domyślnie false). Przy false — zero zmian zachowania.
- Przy true: zamiast ostrzeżenia o braku pada (`VrStreamActivity.kt` ~l.1329,
  diagnostyka) i przypomnienia o L3+R3 (~l.112) — „Menu: przycisk ≡ na lewym kontrolerze”.
- Moduły szukające pada w pętli (`EspelhoDualSense.kt` ~l.402, `GamepadMonitor.kt`)
  nie powinny wtedy obciążać CPU ani dziennika — tylko jeśli to drobna zmiana.

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
| Responsywność | direct | wył. | wył. | — |
| Zrównoważony (domyślny) | direct | wył. | wł. | — |
| Ultra Płynność | shader + frames | wł. | wł. | — |
| Kino | direct | wył. | wł. | duży, przyciemniony |

MQSR włączony wszędzie (kompozytor, bez kosztu GPU). 3D osobnym przełącznikiem.
Profile ekranu Kanapa/Kino/Biurko = zestawy `ScreenPrefs`.

## Później (po pomiarach)

Siła generowania klatek (30→60 vs 30→120 przy 120 Hz), inwentaryzacja i uzupełnienie
diagnostyki, profile gier, tryb awaryjny, polski interfejs.

## Synchronizacja z P5M

Przy nowym release P5M: `main` ← `upstream/main` (fast-forward), `qs8/main` ← `main`.
Konflikty tylko w miejscach `// QS8:`. Po merge'u CI + regresja na Queście.
