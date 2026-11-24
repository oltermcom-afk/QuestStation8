// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Plan 1B: kontrolery Questa obsługują panel ustawień.
 *
 * Odpytuje maskę z [TouchNative] na zegarze głównego wątku (co ~16 ms, tylko
 * gdy aktywność jest wznowiona) i zamienia zbocza na syntetyczne [KeyEvent]
 * z kodami, które panel P5M już rozumie. Zdarzenia idą WYŁĄCZNIE do [naKlawisz]
 * (`VrStreamActivity.qs8TouchKey`), który podaje je tylko panelowi i klawiaturze
 * PIN-u — nigdy do `dispatchKeyEvent`/`StreamInput`, więc kontroler Questa nie
 * steruje konsolą.
 */
class TouchMenu(private val naKlawisz: (KeyEvent) -> Unit): LifecycleEventObserver
{
	private val handler = Handler(Looper.getMainLooper())
	private var poprzedni = 0
	private var dziala = false
	/** Trzymany klawisz: kiedy wciśnięty, ile powtórzeń, kiedy następne. */
	private class Trzymany(val od: Long, var powtorzen: Int, var nastepne: Long)

	private val wcisniete = HashMap<Int, Trzymany>()

	private val tick = object: Runnable
	{
		override fun run()
		{
			if(!dziala)
				return
			odczytaj()
			handler.postDelayed(this, OKRES_MS)
		}
	}

	override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event)
	{
		when(event)
		{
			Lifecycle.Event.ON_RESUME -> start()
			Lifecycle.Event.ON_PAUSE -> stop()
			Lifecycle.Event.ON_DESTROY -> { stop(); source.lifecycle.removeObserver(this) }
			else -> {}
		}
	}

	private fun start()
	{
		if(dziala)
			return
		dziala = true
		handler.post(tick)
	}

	/** Puszcza wszystko, co trzymane, żeby panel nie został z „wciśniętym” B. */
	private fun stop()
	{
		dziala = false
		handler.removeCallbacks(tick)
		val teraz = SystemClock.uptimeMillis()
		for((kod, stan) in wcisniete.entries.toList())
			naKlawisz(KeyEvent(stan.od, teraz, KeyEvent.ACTION_UP, kod, 0))
		wcisniete.clear()
		poprzedni = 0
	}

	private fun odczytaj()
	{
		val stan = try { TouchNative.nativeReadTouchState() }
		catch(e: Throwable) { 0 }
		val teraz = SystemClock.uptimeMillis()
		for((bit, kod) in MAPA)
		{
			val jest = stan and bit != 0
			val byl = poprzedni and bit != 0
			when
			{
				jest && !byl ->
				{
					wcisniete[kod] = Trzymany(teraz, 0, teraz + ViewConfiguration.getKeyRepeatTimeout())
					Log.i(TAG, "QS8 touch: ${KeyEvent.keyCodeToString(kod)} (panel only)")
					naKlawisz(KeyEvent(teraz, teraz, KeyEvent.ACTION_DOWN, kod, 0))
				}
				!jest && byl ->
				{
					val s = wcisniete.remove(kod)
					naKlawisz(KeyEvent(s?.od ?: teraz, teraz, KeyEvent.ACTION_UP, kod, 0))
				}
				jest && kod in POWTARZANE ->
				{
					val s = wcisniete[kod] ?: continue
					if(teraz >= s.nastepne)
					{
						s.powtorzen += 1
						s.nastepne = teraz + ViewConfiguration.getKeyRepeatDelay()
						naKlawisz(KeyEvent(s.od, teraz, KeyEvent.ACTION_DOWN, kod, s.powtorzen))
					}
				}
			}
		}
		poprzedni = stan
	}

	companion object
	{
		private const val TAG = "P5MVR"
		private const val OKRES_MS = 16L

		// Bity jak w qs8_touch.h (qs8::TouchBit).
		private val MAPA = listOf(
			(1 shl 0) to KeyEvent.KEYCODE_MENU,        // ≡ lewy: otwiera/zamyka panel
			(1 shl 1) to KeyEvent.KEYCODE_BUTTON_A,    // centrowanie
			(1 shl 2) to KeyEvent.KEYCODE_BUTTON_B,    // zamknij; trzymaj = koniec sesji
			(1 shl 3) to KeyEvent.KEYCODE_BUTTON_X,    // ostrość
			(1 shl 4) to KeyEvent.KEYCODE_BUTTON_Y,    // pokój (passthrough)
			(1 shl 5) to KeyEvent.KEYCODE_BUTTON_L1,   // grip lewy: wysokość w dół
			(1 shl 6) to KeyEvent.KEYCODE_BUTTON_R1,   // grip prawy: wysokość w górę
			(1 shl 7) to KeyEvent.KEYCODE_BUTTON_L2,   // trigger lewy: płaski/zakrzywiony
			(1 shl 8) to KeyEvent.KEYCODE_BUTTON_R2,   // trigger prawy: krzywizna
			(1 shl 9) to KeyEvent.KEYCODE_DPAD_UP,     // thumbstick: rozmiar
			(1 shl 10) to KeyEvent.KEYCODE_DPAD_DOWN,
			(1 shl 11) to KeyEvent.KEYCODE_DPAD_LEFT,  // thumbstick: odległość
			(1 shl 12) to KeyEvent.KEYCODE_DPAD_RIGHT,
			(1 shl 13) to KeyEvent.KEYCODE_BUTTON_SELECT, // klik lewej gałki: jasność (jak Share)
			(1 shl 14) to KeyEvent.KEYCODE_BUTTON_START   // klik prawej gałki: siła 3D (jak Options)
		)

		/** Jak AJUSTES_QUE_REPETEM w VrStreamActivity: kroki, które się powtarza trzymając. */
		private val POWTARZANE = setOf(
			KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
			KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
			KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1)

		/** Przyciski są przy każdym wierszu (LegendaTouch); tu tylko, co znaczą skróty i czego Quest nie robi. */
		private const val LEGENDA = "Quest / PS5 controller  ·  LT RT  Quest triggers  ·  Grip  grip buttons  ·  " +
				"stick: either one, L / R: press the stick  ·  triggers, touchpad: PS5 only"

		/**
		 * Objaśnienie legendy kontrolerów Questa w dolnym pasku panelu (pod trzecim rzędem,
		 * w kolumnach ustawień, przed kolumną odczytów). Geometria z
		 * PainelDeAjuste: margines 52, pięć kolumn, odstęp 44.
		 */
		/** Lewa krawędź kolumny odczytów (LIVE) panelu: geometria PainelDeAjuste. */
		private fun prawaKolumna(bitmap: Bitmap): Float
		{
			val kolumna = (bitmap.width - 2 * 52f - 4 * 44f) / 5
			return 52f + 4 * (kolumna + 44f)
		}

		fun dorysujLegende(bitmap: Bitmap, jakosc: io.github.gblandro.p5m.StreamQualityPrefs, padPrzyQuescie: Boolean)
		{
			// Aktywny profil (tylko podgląd) pod kolumną odczytów.
			Canvas(bitmap).drawText(Tlumaczenia.t("Profile: " + Profile.nazwa(jakosc)), prawaKolumna(bitmap), bitmap.height - 22f,
					Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(142, 152, 173); textSize = 18f })
			val w = bitmap.width.toFloat()
			val margines = 52f
			val kolumna = (w - 2 * margines - 4 * 44f) / 5
			val prawa = margines + 4 * kolumna + 3 * 44f
			val farba = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(142, 152, 173); textSize = 17f }
			val plotno = Canvas(bitmap)
			val legenda = Tlumaczenia.t(LEGENDA)
			while(farba.textSize > 13f && farba.measureText(legenda) > prawa - margines)
				farba.textSize -= 1f
			LegendaTouch.przytrzymania(plotno, margines, prawa, bitmap.height - 46f, padPrzyQuescie)
			plotno.drawText(legenda, margines, bitmap.height - 16f, farba)
		}
	}
}
