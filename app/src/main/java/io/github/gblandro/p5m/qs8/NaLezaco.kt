// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.util.Log

/**
 * Tryb „na leżąco” (zamówienie Damiana z 2026-11-25): ekran staje tam, gdzie
 * patrzy głowa, także na suficie, zwrócony przodem do patrzącego.
 *
 * Włącza i wyłącza go przytrzymanie LT (Quest) / L2 (pad przy Queście) w panelu
 * albo wiersz „Lying down” na stronie 2. Samo włączenie od razu centruje; potem
 * A / ✕ i przytrzymanie Meta centrują według pełnej orientacji głowy.
 *
 * - na plecach: góra ekranu według pokoju, a przy patrzeniu ponad 45 stopni
 *   w górę (lub w dół) według głowy;
 * - na boku: zawsze według głowy, razem z jej przechyleniem.
 *
 * Tryb żyje tylko w pamięci `qs8_lezaco.cpp` i kasuje go koniec sesji, więc
 * każda nowa sesja zaczyna się normalnie. Wyłączenie stawia ekran tam, gdzie stał.
 */
object NaLezaco
{
	const val NAZWA = "Lying down"

	private const val NORMALNY = 0
	private const val PLECY = 1

	// Wartości jak qs8::TrybLezenia.
	private val NAZWY = listOf("Off", "On back", "On side")
	private val KOMUNIKATY = listOf("Normal mode", "Lying down: on back", "Lying down: on side")

	/** Odmiana dla przytrzymania LT / L2: ostatnio używana, na początku „na plecach”. */
	private var ostatnia = PLECY

	fun tryb(): Int = try { NaLezacoNative.nativeTryb().coerceIn(0, NAZWY.size - 1) }
		catch(e: Throwable) { NORMALNY }

	fun wlaczony() = tryb() != NORMALNY

	fun nazwa() = NAZWY[tryb()]

	fun komunikat() = KOMUNIKATY[tryb()]

	/** Wiersz na stronie 2: wył. → na plecach → na boku. */
	fun zmien(kierunek: Int) = ustaw((tryb() + kierunek + NAZWY.size) % NAZWY.size)

	/** Przytrzymanie LT / L2: jednym ruchem włącza i wraca do trybu normalnego. */
	fun przelacz() = ustaw(if(wlaczony()) NORMALNY else ostatnia)

	private fun ustaw(tryb: Int)
	{
		if(tryb != NORMALNY)
			ostatnia = tryb
		try { NaLezacoNative.nativeSetTryb(tryb) }
		catch(e: Throwable) { Log.w("P5MVR", "QS8: lying mode not applied", e) }
	}
}
