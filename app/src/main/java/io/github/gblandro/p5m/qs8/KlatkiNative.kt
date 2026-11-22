// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

/** Most do `qs8_klatki.cpp`: limit generowanych klatek (działa od razu, także w trakcie gry). */
object KlatkiNative
{
	init { System.loadLibrary("p5m-vr") }

	external fun nativeSetLimit(do60: Boolean)
}
