// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: limit generowanych klatek ("do 120" / "do 60") dla przewidywania klatek
// (GL_QCOM_frame_extrapolation w tone_mapper.cpp).
#pragma once

#include <cstdint>

namespace qs8 {

// Krok przewidywania po limicie. "Do 120" (domyslnie) zwraca skale bez zmian.
// "Do 60" zaokragla ja w dol do wielokrotnosci 1/60 s wyrazonej w okresach
// zrodla: przy grze 30 fps skala 0,25 -> 0 (prawdziwa klatka), 0,5 i 0,75 -> 0,5,
// czyli jedna przewidziana klatka na prawdziwa, widoczna przez dwa odswiezenia.
// Przy grze 60 fps "do 60" = bez przewidywania miedzy klatkami.
float SkalaPrzewidywania(float skala, int64_t okres_zrodla_ns);

}  // namespace qs8
