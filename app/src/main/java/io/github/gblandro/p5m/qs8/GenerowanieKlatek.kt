// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import android.util.Log

/**
 * Limit generowanych klatek dla ścieżki „shader + frames” P5M.
 *
 * P5M przewiduje klatkę przy każdym odświeżeniu panelu między prawdziwymi
 * klatkami z konsoli: gra 30 fps przy 120 Hz = 3 przewidziane na 1 prawdziwą
 * (do ¾ okresu naprzód), czyli najpłynniej, ale z największymi artefaktami.
 * „Do 60” zaokrągla krok przewidywania w dół do 1/60 s (`qs8_klatki.cpp`):
 * przy 30 fps jedna przewidziana klatka (½ okresu) na prawdziwą, każda przez
 * dwa odświeżenia — ruch jak w grze 60 fps, mniej i mniejsze błędy. Przy grze
 * 60 fps „do 60” = bez przewidywania między klatkami. Opóźnienie bez zmian
 * (to przewidywanie, nie interpolacja).
 */
object GenerowanieKlatek
{
	fun do60(context: Context) = Qs8Prefs(context).klatkiDo60

	fun nazwa(context: Context) = if(do60(context)) "Up to 60 fps" else "Up to 120 fps"

	fun opis(context: Context) = if(do60(context))
		"One predicted frame per console frame in 30 fps games: motion like 60 fps, fewer and smaller " +
				"artifacts. In 60 fps games no frames are predicted. No added latency."
	else
		"A predicted frame on every panel refresh: 30 fps games get three per console frame. The smoothest, " +
				"with the most artifacts in fast motion. No added latency."

	/** Na starcie sesji i po każdej zmianie. */
	fun zastosuj(context: Context)
	{
		try { KlatkiNative.nativeSetLimit(do60(context)) }
		catch(e: Throwable) { Log.w("P5MVR", "QS8: generated frames limit not applied", e) }
	}

	fun przelacz(context: Context)
	{
		val p = Qs8Prefs(context)
		p.klatkiDo60 = !p.klatkiDo60
		zastosuj(context)
	}
}
