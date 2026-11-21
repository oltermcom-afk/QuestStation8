// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import com.metallic.chiaki.session.StreamInput

/**
 * Plan 1C: Tryb domowy (pad przy konsoli, nie przy Queście) to normalny tryb,
 * nie błąd.
 *
 * Na razie bez przełącznika: Tryb domowy rozpoznajemy po tym, że w tej sesji
 * aplikacja nie widziała żadnego pada (tylko kontrolery Touch, które Android
 * zgłasza bez nazwy HID, „Device 0x…”). Przełącznik trybu przyjdzie z ekranem
 * startowym (plan 1D).
 */
object TrybDomowy
{
	/**
	 * Od początku sesji nie było pada przy Queście.
	 *
	 * Korzysta z reguły GamepadMonitor (patch 0024): bez wybranego pada osie
	 * przyjmowane są od wszystkich urządzeń tylko wtedy, gdy nazwany pad nigdy
	 * się nie pojawił. Pad, który był i się rozłączył, daje tu false.
	 */
	fun bezPadaWSesji(input: StreamInput?): Boolean =
		input != null && input.preferredGamepadId == null && input.acceptsGamepadAxes(-1)

	const val DZIENNIK = "Home mode: no PS controller on the headset (normal when it is paired with the console). " +
			"Settings panel: menu button on the left Quest controller"

	const val ROZLACZONY = "Press the PS button to reconnect. Settings panel: menu button on the left Quest controller."
}
