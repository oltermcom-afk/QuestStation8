// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import android.util.Log
import io.github.gblandro.p5m.StreamQualityPrefs

/**
 * Suwak „Peak” HDR: jaki szczyt jasności źródła krzywa tonów (tone_mapper.cpp)
 * zakłada przy 10-bit. P5M ma stałe 1000 nitów. Mniej = jaśniejsze światła w
 * grach, które nie sięgają 1000; więcej = mniej przepaleń w grach jasnych.
 * Działa w trakcie gry (uniform przy każdym rysowaniu). Bez 10-bit nie ma
 * krzywej PQ, więc ustawienie nic nie zmienia — komunikat to mówi.
 */
object PeakHdr
{
	val NITY = intArrayOf(600, 1000, 4000)

	fun wartosc(context: Context) = NITY[Qs8Prefs(context).peakHdr.coerceIn(0, NITY.size - 1)]

	fun indeks(context: Context) = Qs8Prefs(context).peakHdr.coerceIn(0, NITY.size - 1)

	/** Na starcie sesji i po każdej zmianie. */
	fun zastosuj(context: Context)
	{
		try { HdrNative.nativeSetPeak(wartosc(context).toFloat()) }
		catch(e: Throwable) { Log.w("P5MVR", "QS8: HDR peak not applied", e) }
	}

	fun nastepny(context: Context)
	{
		val p = Qs8Prefs(context)
		p.peakHdr = (indeks(context) + 1) % NITY.size
		zastosuj(context)
	}

	fun poprzedni(context: Context)
	{
		val p = Qs8Prefs(context)
		p.peakHdr = (indeks(context) + NITY.size - 1) % NITY.size
		zastosuj(context)
	}

	fun komunikat(context: Context, jakosc: StreamQualityPrefs) =
		"HDR peak ${wartosc(context)} nits" + if(jakosc.tenBitNaSessao) "" else " — only with 10-bit color"
}
