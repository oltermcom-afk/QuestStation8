// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import io.github.gblandro.p5m.ScreenPrefs
import io.github.gblandro.p5m.Trace

/**
 * Plan 1F: ekran „Kino” przy pierwszym uruchomieniu QS8.
 *
 * Zakrzywiony ekran na czarnym tle zamiast domyślnego P5M (płaski ekran,
 * passthrough włączony). Ustawiany raz, przez istniejące settery [ScreenPrefs];
 * wartości domyślne P5M zostają nietknięte.
 *
 * Ustawienia użytkownika mają pierwszeństwo: jeśli plik ekranu P5M ma już
 * jakikolwiek zapisany klucz, ekran był ustawiany ręcznie w panelu (tylko panel
 * w [com.metallic.chiaki.stream.VrStreamActivity] zapisuje [ScreenPrefs]) —
 * wtedy preset jest pomijany. Dotyczy to też aktualizacji z dev.1, w której
 * flagi [Qs8Prefs] jeszcze nie było.
 */
object PresetKino
{
	/**
	 * Nazwa pliku z [ScreenPrefs] (tam jest prywatna). Gdyby upstream ją
	 * zmienił, ten test widziałby pusty plik i nadpisałby ręczne ustawienia —
	 * przy merge'u z P5M sprawdzić, czy nazwa jest ta sama.
	 */
	private const val PLIK_EKRANU_P5M = "p5m_vr_screen"

	/** Krzywizna „średnia” — jeden ze stopni, które cyklicznie ustawia R2 w panelu. */
	private const val KRZYWIZNA = 0.6f

	/** Ekran „Kino”: pokój ukryty, cylinder, krzywizna 0,6 (też z profilu Cinema). */
	fun ustawEkran(ekran: ScreenPrefs)
	{
		ekran.passthroughEnabled = false
		ekran.layerShape = ScreenPrefs.SHAPE_CYLINDER
		ekran.curvature = KRZYWIZNA
	}

	fun zastosujRaz(context: Context)
	{
		val qs8 = Qs8Prefs(context)
		if(qs8.presetKinoRozstrzygniety)
			return

		val zapisane = context.getSharedPreferences(PLIK_EKRANU_P5M, Context.MODE_PRIVATE).all.keys
		if(zapisane.isEmpty())
		{
			ustawEkran(ScreenPrefs(context))
			// Rozmiar, odległość i wysokość zostają domyślne P5M (nic nie zapisujemy).
			Trace.log(context, "QS8: first run, cinema screen preset applied " +
					"(passthrough off, curved, curvature $KRZYWIZNA)")
		}
		else
		{
			Trace.log(context, "QS8: cinema screen preset skipped, the screen was already " +
					"set by hand (${zapisane.sorted().joinToString()})")
		}
		qs8.presetKinoRozstrzygniety = true
	}
}
