// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import android.util.Log
import io.github.gblandro.p5m.StreamQualityPrefs

/**
 * MQSR (eksperymentalne) — kolejka 2026-11-22, pkt 3.
 *
 * Ostrość „Automatic” P5M daje kompozytorowi Questa do wyboru zwykłe i MQSR
 * wyostrzanie oraz supersampling. Przełącznik zostawia mu sam MQSR
 * (`QUALITY_SHARPENING`) — wariant z dev.21. Tam, przy włączonej głębi
 * neuronowej, gubił 0–4 klatek (bez filtra: 0), więc w #17 wrócił zestaw P5M.
 * Opcja jest po to, żeby sprawdzić MQSR bez głębi neuronowej.
 *
 * Działa tylko przy ostrości „Automatic” (w trybie immersyjnym) i na żywo:
 * bity warstwy ustawiane są co klatkę (`qs8_mqsr.cpp`). Wariantu z jawnie
 * wymuszonym MQSR bez filtra automatycznego (jasny dysk za głową w P5M) nie
 * używamy.
 */
object Mqsr
{
	fun wlaczone(context: Context) = Qs8Prefs(context).mqsr

	/** Czy przy obecnej ostrości przełącznik ma na co działać. */
	fun aktywne(jakosc: StreamQualityPrefs) = jakosc.sharpness == StreamQualityPrefs.SHARPNESS_AUTO

	const val NAZWA = "MQSR (experimental)"

	fun opis(context: Context) = if(wlaczone(context))
		"Automatic sharpness lets the headset use only MQSR, its best upscaling filter. Watch dropped frames " +
				"and latency in the panel; it costs more GPU. Works only with Sharpness: Automatic."
	else
		"Off: Automatic sharpness picks between normal sharpening, MQSR and supersampling, as in P5M. " +
				"Turn on to test MQSR alone (works only with Sharpness: Automatic)."

	/** Na starcie sesji i po każdej zmianie. */
	fun zastosuj(context: Context)
	{
		try { MqsrNative.nativeSetMqsr(wlaczone(context)) }
		catch(e: Throwable) { Log.w("P5MVR", "QS8: MQSR switch not applied", e) }
	}

	fun przelacz(context: Context)
	{
		val p = Qs8Prefs(context)
		p.mqsr = !p.mqsr
		zastosuj(context)
	}
}
