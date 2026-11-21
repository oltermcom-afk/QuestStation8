// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

/**
 * Nazwa aplikacji w tekstach widocznych dla użytkownika (w jednym miejscu).
 *
 * Nie dotyczy pakietów, klas, TAG-ów ani linii dziennika (te zostają „P5M”,
 * żeby merge z upstreamem i porównywanie dzienników z P5M były proste).
 * Etykiety aktywności w AndroidManifest.xml nie mogą czytać stałej Kotlina —
 * tam nazwa jest wpisana tekstem.
 */
object Marka
{
	const val NAZWA = "QuestStation8"

	/** Projekt, na którym opiera się QuestStation8 (AGPL-3.0 wymaga wskazania źródła). */
	const val P5M_REPO = "https://github.com/beecrepaldi-afk/P5M"
	const val P5M_ROTULO = "Based on P5M by gblandro"
	const val P5M_OPIS = "$NAZWA is built on P5M, licensed under AGPL-3.0. Opens the original project and its source code."
}
