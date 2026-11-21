// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import io.github.gblandro.p5m.StreamQualityPrefs

/**
 * Porównanie A/B: obraz oryginalny / ulepszony (przytrzymanie X / □ w panelu).
 *
 * „Ulepszony” to obecne wyostrzanie (filtry kompozytora z MQSR albo CAS w
 * shaderze). „Oryginał” to ten sam obraz bez żadnego filtra: do kompozytora i
 * shadera idzie Off / 0. Ustawienia nie są zmieniane — przełącznik żyje tylko
 * w tej sesji ([StanPanelu.oryginal]) i wraca do ulepszonego po ponownym
 * przytrzymaniu albo w następnej sesji. Jasność i reszta zostają bez zmian, żeby
 * porównanie dotyczyło tylko filtrów.
 */
object PorownanieAB
{
	fun ostrosc(stan: StanPanelu, wartosc: Int) = if(stan.oryginal) StreamQualityPrefs.SHARPNESS_OFF else wartosc

	fun sila(stan: StanPanelu, wartosc: Float) = if(stan.oryginal) 0f else wartosc

	fun przelacz(stan: StanPanelu, jakosc: StreamQualityPrefs, dziennik: (String) -> Unit)
	{
		stan.oryginal = !stan.oryginal
		val filtr = StreamQualityPrefs.SHARPNESS_NAMES[jakosc.sharpness]
		if(stan.oryginal)
			stan.pokaz("A · ORIGINAL — no sharpening")
		else
			stan.pokaz("B · ENHANCED — sharpness $filtr")
		dziennik("QS8: A/B ${if(stan.oryginal) "original (no filter)" else "enhanced (sharpness $filtr)"}")
	}
}
