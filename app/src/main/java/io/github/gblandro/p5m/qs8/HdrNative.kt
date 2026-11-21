// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

/** Most do `qs8_hdr.cpp`: szczyt jasności źródła HDR dla krzywej tonów (działa w trakcie gry). */
object HdrNative
{
	init { System.loadLibrary("p5m-vr") }

	external fun nativeSetPeak(nity: Float)
}
