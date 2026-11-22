// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import android.util.Log
import io.github.gblandro.p5m.ScreenPrefs
import io.github.gblandro.p5m.StreamQualityPrefs

/**
 * Poświata wokół ekranu (jak Ambilight) — kolejka 2026-11-22, pkt 5.
 *
 * Kolory stref przy krawędziach obrazu gry, wygładzone w czasie (~0,25 s),
 * jako miękka warstwa pod ekranem (`qs8_poswiata.cpp`). Tylko ścieżka shader
 * (w direct obraz nie przechodzi przez aplikację) i tylko w ciemnym pokoju:
 * pokój ukryty albo przyciemniony trybem kina — przy zwykłym passthrough
 * czeka. Opóźnienie gry bez zmian. Działa na żywo.
 */
object Poswiata
{
	val NAZWY = listOf("Off", "Faint", "Moderate", "Bright")

	const val NAZWA = "Glow"

	fun poziom(context: Context) = Qs8Prefs(context).poswiata

	fun nazwa(context: Context) = NAZWY[poziom(context)]

	/** Pokój ukryty albo przyciemniony (kino): tu poświata ma sens. */
	fun ciemnyPokoj(ekran: ScreenPrefs) = !ekran.passthroughEnabled || ekran.cinema > 0

	fun opis(context: Context) =
		"Light around the screen in the colors of its edges, like Ambilight. Only in a dark room (room hidden " +
				"or dimmed for cinema) and with the shader video path. No added latency."

	/** Na starcie sesji i po każdej zmianie. */
	fun zastosuj(context: Context)
	{
		try { PoswiataNative.nativeSetPoziom(poziom(context)) }
		catch(e: Throwable) { Log.w("P5MVR", "QS8: glow setting not applied", e) }
	}

	fun zmien(context: Context, kierunek: Int)
	{
		val p = Qs8Prefs(context)
		p.poswiata = (p.poswiata + kierunek + NAZWY.size) % NAZWY.size
		zastosuj(context)
	}

	fun shader(jakosc: StreamQualityPrefs) = jakosc.immersiveUsesShader
}
