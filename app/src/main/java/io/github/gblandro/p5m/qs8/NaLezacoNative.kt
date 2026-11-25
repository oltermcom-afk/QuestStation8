// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

/** Most do `qs8_lezaco.cpp`: tryb „na leżąco”, działa od razu. */
object NaLezacoNative
{
	init { System.loadLibrary("p5m-vr") }

	external fun nativeSetTryb(tryb: Int)

	external fun nativeTryb(): Int
}
