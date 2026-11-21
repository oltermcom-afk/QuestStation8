// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.os.Handler
import io.github.gblandro.p5m.ScreenPrefs

/**
 * Rozmiar ekranu „piksel w piksel”: 1 piksel strumienia ≈ 1 piksel panelu.
 *
 * Runtime (XR_META_recommended_layer_resolution) mówi, jakiej szerokości obrazu
 * warstwa potrzebuje w swoim obecnym rozmiarze — P5M już to czyta dla werdyktu
 * „just right” w wierszu Size. Szerokość rośnie mniej więcej proporcjonalnie do
 * kąta ekranu, więc kąt = kąt · (szerokość strumienia / zalecana). Po zmianie
 * odczyt przychodzi z kolejnych klatek, więc dwa razy poprawiamy (ułamki
 * procentu przy krzywiźnie i przy quadzie). Tylko na życzenie (przytrzymanie
 * A / ✕ w panelu), nic nie jest wymuszane.
 */
class PikselWPiksel(
	private val handler: Handler,
	private val ekran: ScreenPrefs,
	private val zalecana: () -> Pair<Int, Int>?,
	private val szerokoscZrodla: () -> Int,
	private val zastosuj: () -> Unit,
	private val komunikat: (String) -> Unit,
	private val dziennik: (String) -> Unit)
{
	private var krok = 0

	private val popraw = object: Runnable
	{
		override fun run()
		{
			val rec = zalecana()
			if(rec == null)
			{
				komunikat("1:1 size is not available on this headset")
				dziennik("QS8: 1:1 size: no recommended layer resolution from the runtime")
				return
			}
			val stosunek = szerokoscZrodla().toFloat() / rec.first
			val przed = ekran.centralAngle
			ekran.centralAngle = przed * stosunek
			zastosuj()
			val stopnie = Math.toDegrees(ekran.centralAngle.toDouble()).toInt()
			val oparte = ekran.centralAngle >= ScreenPrefs.MAX_ANGLE || ekran.centralAngle <= ScreenPrefs.MIN_ANGLE
			krok++
			if(krok < 3 && !oparte && kotlin.math.abs(stosunek - 1f) > 0.02f)
			{
				handler.postDelayed(this, 250L)
				return
			}
			komunikat(if(oparte) "Size 1:1 is outside the range, set to $stopnie°" else "Size 1:1 pixel: $stopnie°")
			dziennik("QS8: 1:1 size: ${rec.first} px recommended for ${szerokoscZrodla()} px, " +
					"angle ${Math.toDegrees(przed.toDouble()).toInt()}° -> $stopnie° (step $krok)")
		}
	}

	fun dopasuj()
	{
		handler.removeCallbacks(popraw)
		krok = 0
		handler.post(popraw)
	}
}
