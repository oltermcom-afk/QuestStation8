// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: limit generowanych klatek ("do 120" / "do 60") dla przewidywania klatek
// (GL_QCOM_frame_extrapolation w tone_mapper.cpp).
#pragma once

#include <cstdint>

namespace qs8 {

// Krok przewidywania po limicie. Wolane raz na odswiezenie ekranu (kazde
// rysowanie), teraz_ns = czas tego rysowania. "Do 120" (domyslnie) zwraca skale
// bez zmian. "Do 60" liczy pelne odswiezenia od ostatniej prawdziwej klatki
// i zmienia obraz co tyle odswiezen, ile trwa 1/60 s (2 przy 120 Hz, 3 przy
// 180 Hz). Przy grze 30 fps: jedna przewidziana klatka na prawdziwa, kazdy
// obraz rowno przez 2 (3) odswiezenia. Przy grze 60 fps: bez przewidywania
// miedzy klatkami.
float SkalaPrzewidywania(float skala, int64_t okres_zrodla_ns, int64_t teraz_ns);

}  // namespace qs8
