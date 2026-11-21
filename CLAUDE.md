# QuestStation8 — zasady pracy dla Claude Code

QuestStation8 (QS8) to cienka nakładka na **P5M** (github.com/beecrepaldi-afk/P5M),
klienta PS5 Remote Play na Meta Quest 3, zbudowanego na libchiaki z chiaki-ng.
Właściciel: Damian. Komunikacja po polsku. Plan prac: `docs/QS8-PLAN.md`.

## Zasady nadrzędne

1. **Nie przepisujemy P5M.** Wszystko, co już działa (połączenie, wybudzanie, PSN,
   generowanie klatek, MQSR/CAS, tone mapping, 3D, ekran, diagnostyka), zostaje.
2. **Fork ma być cienki.** Nowy kod trafia do nowych plików
   (`app/src/main/java/io/github/gblandro/p5m/qs8/`, `app/src/main/cpp/qs8_*.cpp`).
   W istniejących plikach P5M tylko krótkie, wyraźnie oznaczone punkty zaczepienia:
   `// QS8:` + jedno zdanie po co. Każdy taki punkt utrudnia merge z upstreamem.
3. **Nie zmieniamy nazw pakietów, klas ani plików P5M.** Nie formatujemy cudzego kodu.
4. **Nie ruszamy `external/chiaki-ng`.** Zmiany w libchiaki tylko jako nowy plik w
   `patches/` (tak jak robi P5M), i tylko gdy nie da się inaczej.
5. **Tryb mobilny (pad sparowany z Questem) musi działać identycznie jak w P5M.**
   Każda zmiana Trybu domowego jest za flagą i domyślnie nie wpływa na tryb mobilny.
6. **Żadnych pozornych funkcji.** Nie dodajemy przycisku ani opcji bez działającej
   implementacji.
7. **Nie twierdzimy, że coś działa na Queście, jeśli tylko się skompilowało.**
   Raport rozróżnia: „skompilowane”, „sprawdzone w CI”, „wymaga testu na Queście”.
8. **Bez obchodzenia zabezpieczeń PSN/Sony.** Tryb domowy opiera się na legalnym
   połączeniu z drugiego konta PSN.
9. **Sekrety nigdy w repo:** keystore, hasła, tokeny PSN wyłącznie jako GitHub Secrets.

## Cykl każdej zmiany

1. Przeczytaj kod, którego dotyczy zmiana (P5M ma obszerne komentarze po portugalsku,
   są wiarygodnym źródłem intencji).
2. Ustal minimalny zakres; jeśli wymaga zmiany w >3 istniejących plikach P5M,
   najpierw zaproponuj to Damianowi.
3. Zmiana na osobnej gałęzi `qs8/<krótka-nazwa>` → PR do `qs8/main`.
4. Lokalnie przed pushem: `python3 tools/conferir.py` i `python3 tools/compilar_nativo.py`.
5. Push → GitHub Actions buduje APK (release `dev` w forku).
6. Raport dla Damiana (format niżej) + lista testów na Queście.
7. Następny krok dopiero po potwierdzeniu testu na sprzęcie.

## Format raportu po każdym kroku

- **Co zmieniono** (pliki, 1–3 zdania na plik).
- **Status:** skompilowane / CI zielone / APK do pobrania: <link>.
- **Do przetestowania na Queście:** ponumerowane kroki, oczekiwany wynik, co zrobić,
  jeśli wynik jest inny (np. „Zgłoś problem” w aplikacji → wklej dziennik).
- **Ryzyko dla trybu mobilnego:** brak / opis.
- **Jak cofnąć:** revert PR #...

## Repozytorium

- `origin` = fork Damiana, `upstream` = beecrepaldi-afk/P5M.
- `main` = czyste lustro `upstream/main` (nigdy własnych commitów).
- `qs8/main` = nasza gałąź główna; regularnie `git merge upstream/main`.
