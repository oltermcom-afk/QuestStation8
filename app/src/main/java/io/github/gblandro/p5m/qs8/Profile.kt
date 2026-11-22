// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import io.github.gblandro.p5m.ScreenPrefs
import io.github.gblandro.p5m.StreamQualityPrefs
import io.github.gblandro.p5m.Trace

/**
 * Etap 2: profile obrazu. Profil to zestaw wartości istniejących ustawień P5M,
 * wpisywanych przez ich settery — nic nie jest blokowane ani zapamiętywane
 * osobno. Aktywny profil rozpoznajemy po wartościach: gdy żaden nie pasuje
 * (ktoś zmienił coś ręcznie), wyświetla się „Custom”.
 *
 * Profile NIE ustawiają bitrate'u ani auto bitrate'u (decyzja Damiana: to
 * zależy od sieci, nie od gry) ani 3D (osobny przełącznik).
 *
 * Profil „Responsive” (direct + 180 Hz) usunięty w PR #17 po pomiarze 35–36 ms
 * na dev.21 (22.11.2026). Później okazało się, że te 35 ms daje włączona
 * emulacja 3D (osobny przełącznik, profile go nie ruszają), a nie 180 Hz —
 * powrót profilu zależy od pomiaru direct + 180 Hz bez 3D.
 *
 * Ścieżka, 10-bit i częstotliwość panelu działają od następnego połączenia;
 * wyostrzanie i ekran od razu.
 */
object Profile
{
	class Profil(
		val nazwa: String,
		val opis: String,
		val sciezkaShader: Boolean,   // StreamQualityPrefs.toneMapped
		val dziesiecBit: Boolean,     // tenBit (wymusza ścieżkę shader)
		val przewidywanie: Boolean,   // frameExtrapolation
		val panel180: Boolean,
		val wyostrzanie: Int,         // sharpness
		val ekranKino: Boolean)       // przy wyborze: cylinder, krzywizna 0,6, pokój ukryty

	val LISTA = listOf(
		Profil("Balanced", "Direct video path at 120 Hz. The default.",
				false, false, false, false, StreamQualityPrefs.SHARPNESS_MEDIUM, false),
		Profil("Ultra smooth", "Shader path with predicted frames between the console's: smoother motion, " +
				"some artifacts in fast scenes.", true, false, true, false, StreamQualityPrefs.SHARPNESS_LIGHT, false),
		Profil("Cinema", "10-bit color (smooth gradients, PS5 only) and a curved screen in the dark. " +
				"One extra GPU pass, a little more latency.", false, true, false, false, StreamQualityPrefs.SHARPNESS_LIGHT, true))

	const val WLASNY = "Custom"

	fun aktywny(p: StreamQualityPrefs): Profil? = LISTA.firstOrNull {
		p.toneMapped == it.sciezkaShader && p.tenBit == it.dziesiecBit &&
				p.frameExtrapolation == it.przewidywanie && p.panel180 == it.panel180 &&
				p.sharpness == it.wyostrzanie
	}

	fun nazwa(p: StreamQualityPrefs) = aktywny(p)?.nazwa ?: WLASNY

	fun opis(p: StreamQualityPrefs) = aktywny(p)?.let { it.opis + " Takes effect on the next connection." }
		?: "Settings changed by hand. Choose a profile to set picture, path and panel rate at once " +
				"(bitrate and 3D stay as they are)."

	/** Indeks aktywnego profilu dla kropek w launcherze (Custom = -1). */
	fun indeks(p: StreamQualityPrefs) = LISTA.indexOf(aktywny(p))

	fun nastepny(context: Context, p: StreamQualityPrefs) = zastosuj(context, p, LISTA[(indeks(p) + 1) % LISTA.size])

	fun poprzedni(context: Context, p: StreamQualityPrefs) =
		zastosuj(context, p, LISTA[(indeks(p).let { if(it < 0) 0 else it } + LISTA.size - 1) % LISTA.size])

	private fun zastosuj(context: Context, p: StreamQualityPrefs, profil: Profil)
	{
		p.toneMapped = profil.sciezkaShader
		p.tenBit = profil.dziesiecBit
		p.frameExtrapolation = profil.przewidywanie
		p.panel180 = profil.panel180
		p.sharpness = profil.wyostrzanie
		if(profil.ekranKino)
			PresetKino.ustawEkran(ScreenPrefs(context))
		Trace.log(context, "QS8: profile ${profil.nazwa} (shader=${profil.sciezkaShader}, 10-bit=${profil.dziesiecBit}, " +
				"predicted frames=${profil.przewidywanie}, panel ${if(profil.panel180) 180 else 120} Hz, " +
				"sharpness ${StreamQualityPrefs.SHARPNESS_NAMES[profil.wyostrzanie]}" +
				(if(profil.ekranKino) ", cinema screen" else "") + ")")
	}
}
