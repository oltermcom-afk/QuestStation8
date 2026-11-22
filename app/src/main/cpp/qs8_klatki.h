// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: limit generowanych klatek ("do 120" / "do 60") dla przewidywania klatek
// (GL_QCOM_frame_extrapolation w tone_mapper.cpp).
#pragma once

#include <cstdint>

namespace qs8 {

// Krok przewidywania po limicie. Wolane raz na rysowanie (odswiezenie ekranu),
// teraz_ns = czas tego rysowania (tylko do licznika rytmu w dzienniku).
// "Do 120" (domyslnie) zwraca skale bez zmian. "Do 60" zaokragla ja w dol do
// wielokrotnosci 1/60 s wyrazonej w okresach zrodla: przy grze 30 fps skala
// 0,25 -> 0 (prawdziwa klatka), 0,5 i 0,75 -> 0,5, czyli jedna przewidziana
// klatka na prawdziwa, widoczna przez dwa odswiezenia (120 Hz). Przy grze
// 60 fps "do 60" = bez przewidywania miedzy klatkami.
float SkalaPrzewidywania(float skala, int64_t okres_zrodla_ns, int64_t teraz_ns);

}  // namespace qs8
