// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

/** Most do `qs8_poswiata.cpp`: poziom poświaty (działa od razu, także w trakcie gry). */
object PoswiataNative
{
	init { System.loadLibrary("p5m-vr") }

	external fun nativeSetPoziom(poziom: Int)
}
