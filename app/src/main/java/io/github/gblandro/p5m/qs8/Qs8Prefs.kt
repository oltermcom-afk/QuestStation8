// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import android.content.SharedPreferences

/**
 * Ustawienia QuestStation8, w osobnym pliku, żeby nie mieszać ich
 * z kluczami P5M (te mogą się zmienić przy każdym merge'u z upstreamem).
 */
class Qs8Prefs(context: Context)
{
	private val prefs: SharedPreferences =
		context.getSharedPreferences(PLIK, Context.MODE_PRIVATE)

	/** Czy przy pierwszym uruchomieniu już zdecydowano o presecie „Kino” (zastosowany albo pominięty). */
	var presetKinoRozstrzygniety: Boolean
		get() = prefs.getBoolean(KLUCZ_PRESET_KINO, false)
		set(value) { prefs.edit().putBoolean(KLUCZ_PRESET_KINO, value).apply() }

	/** Opis ostatnio znalezionego builda z release „dev” (dla karty aktualizacji), razem z jego wersją. */
	fun opisAktualizacji(wersja: String): String? =
		if(prefs.getString(KLUCZ_AKT_WERSJA, null) == wersja) prefs.getString(KLUCZ_AKT_OPIS, null) else null

	fun zapiszOpisAktualizacji(wersja: String, opis: String)
	{
		prefs.edit().putString(KLUCZ_AKT_WERSJA, wersja).putString(KLUCZ_AKT_OPIS, opis).apply()
	}

	companion object
	{
		private const val PLIK = "qs8_prefs"
		private const val KLUCZ_PRESET_KINO = "preset_kino_rozstrzygniety"
		private const val KLUCZ_AKT_WERSJA = "aktualizacja_wersja"
		private const val KLUCZ_AKT_OPIS = "aktualizacja_opis"
	}
}
