// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.os.Handler
import android.view.KeyEvent

/**
 * Przytrzymanie przycisku w panelu ustawień jako druga funkcja tego samego
 * przycisku (A / ✕ i X / □; kody Androida są wspólne dla Touch i pada).
 *
 * Krótkie naciśnięcie robi to, co w P5M ([krotko] = handleAdjustKey z tym samym
 * DOWN), ale dopiero po puszczeniu — inaczej trzymanie X najpierw zmieniałoby
 * ostrość, a potem przełączało A/B. Przytrzymanie przez [PROG_MS] wywołuje
 * [dlugo] i puszczenie już nic nie robi.
 *
 * [przechwyc] zwraca true dla DOWN (i powtórzeń) obsługiwanych klawiszy. Dla UP
 * zwraca false, żeby P5M dalej oddał puszczenie konsoli (pad: przycisk mógł być
 * wciśnięty przed otwarciem panelu).
 */
class Przytrzymanie(
	private val handler: Handler,
	private val krotko: (KeyEvent) -> Unit,
	private val dlugo: (Int) -> Unit)
{
	private val obslugiwane = HashSet<Int>()
	private var trzymany: KeyEvent? = null
	private var pomin = false

	private val poProgu = Runnable {
		val e = trzymany ?: return@Runnable
		trzymany = null
		dlugo(e.keyCode)
	}

	fun dodaj(kod: Int) { obslugiwane += kod }

	fun przechwyc(event: KeyEvent): Boolean
	{
		if(pomin || event.keyCode !in obslugiwane)
			return false
		when(event.action)
		{
			KeyEvent.ACTION_DOWN ->
			{
				if(event.repeatCount == 0)
				{
					handler.removeCallbacks(poProgu)
					trzymany = event
					handler.postDelayed(poProgu, PROG_MS)
				}
				return true
			}
			KeyEvent.ACTION_UP ->
			{
				val e = trzymany
				if(e != null && e.keyCode == event.keyCode)
				{
					handler.removeCallbacks(poProgu)
					trzymany = null
					pomin = true
					try { krotko(e) } finally { pomin = false }
				}
				return false
			}
		}
		return false
	}

	/** Panel zamknięty albo pauza: nic nie czeka na próg. */
	fun anuluj()
	{
		handler.removeCallbacks(poProgu)
		trzymany = null
	}

	companion object
	{
		const val PROG_MS = 600L
	}
}
