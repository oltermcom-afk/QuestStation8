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

	/** Język aplikacji: domyślnie polski (Horizon OS nie ma polskiego, więc nie z systemu). */
	var jezykPolski: Boolean
		get() = prefs.getBoolean(KLUCZ_JEZYK_PL, true)
		set(value) { prefs.edit().putBoolean(KLUCZ_JEZYK_PL, value).apply() }

	/** Suwak „Peak” HDR: indeks w PeakHdr.NITY (600 / 1000 / 4000), domyślnie 1000 jak w P5M. */
	var peakHdr: Int
		get() = prefs.getInt(KLUCZ_PEAK_HDR, 1)
		set(value) { prefs.edit().putInt(KLUCZ_PEAK_HDR, value).apply() }

	/** Limit generowanych klatek: false = do 120 (jak P5M), true = do 60 (GenerowanieKlatek). */
	var klatkiDo60: Boolean
		get() = prefs.getBoolean(KLUCZ_KLATKI_60, false)
		set(value) { prefs.edit().putBoolean(KLUCZ_KLATKI_60, value).apply() }

	/** MQSR (eksperymentalne): „Automatic” = sam MQSR zamiast zestawu P5M. Domyślnie wyłączone. */
	var mqsr: Boolean
		get() = prefs.getBoolean(KLUCZ_MQSR, false)
		set(value) { prefs.edit().putBoolean(KLUCZ_MQSR, value).apply() }

	/** Poświata wokół ekranu: 0 wył. (domyślnie), 1 słaba, 2 średnia, 3 mocna. */
	var poswiata: Int
		get() = prefs.getInt(KLUCZ_POSWIATA, 0).coerceIn(0, 3)
		set(value) { prefs.edit().putInt(KLUCZ_POSWIATA, value.coerceIn(0, 3)).apply() }

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
		private const val KLUCZ_PEAK_HDR = "peak_hdr"
		private const val KLUCZ_JEZYK_PL = "jezyk_polski"
		private const val KLUCZ_KLATKI_60 = "klatki_do_60"
		private const val KLUCZ_MQSR = "mqsr_eksperymentalne"
		private const val KLUCZ_POSWIATA = "poswiata"
	}
}
