// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

/**
 * Most do `qs8_touch.cpp`: maska przycisków kontrolerów Touch z ostatniej
 * klatki (bity jak w `qs8::TouchBit`). 0, gdy nie ma sesji OpenXR.
 */
object TouchNative
{
	init { System.loadLibrary("p5m-vr") }

	external fun nativeReadTouchState(): Int
}
