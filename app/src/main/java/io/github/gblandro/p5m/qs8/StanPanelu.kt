// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock

/**
 * Stan QS8 jednej sesji, który rysuje się na panelu: krótki komunikat
 * (A/B, rozmiar 1:1) i to, czy porównanie A/B pokazuje obraz oryginalny.
 * Jeden obiekt na VrStreamActivity, więc nowa sesja zaczyna od zera.
 */
class StanPanelu
{
	/** Porównanie A/B: true = obraz bez wyostrzania (oryginał), do końca sesji albo kolejnego przełączenia. */
	var oryginal = false

	private var komunikat: String? = null
	private var komunikatDo = 0L

	fun pokaz(tekst: String, ms: Long = 2500L)
	{
		komunikat = tekst
		komunikatDo = SystemClock.uptimeMillis() + ms
	}

	/** Komunikat na środku nagłówka panelu, dopóki nie minie jego czas. */
	fun dorysuj(bitmap: Bitmap)
	{
		val tekst = Tlumaczenia.t(komunikat ?: return)
		if(SystemClock.uptimeMillis() > komunikatDo)
		{
			komunikat = null
			return
		}
		val canvas = Canvas(bitmap)
		val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.rgb(16, 24, 40)
			textSize = 28f
			textAlign = Paint.Align.CENTER
			typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
		}
		val w = p.measureText(tekst) + 48f
		val cx = bitmap.width / 2f
		canvas.drawRoundRect(RectF(cx - w / 2f, 126f, cx + w / 2f, 170f), 22f, 22f,
				Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(140, 188, 255) })
		canvas.drawText(tekst, cx, 158f, p)
	}
}
