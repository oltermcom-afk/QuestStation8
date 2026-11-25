// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.gblandro.p5m.PainelDeAjuste
import io.github.gblandro.p5m.ScreenPrefs
import io.github.gblandro.p5m.StreamQualityPrefs
import io.github.gblandro.p5m.XrBridge

/**
 * Druga strona panelu w grze: „Zaawansowane” (kolejka 2026-11-22, pkt 2).
 *
 * Wejście i powrót: przytrzymanie ≡ (kontroler Questa; przy zamkniętym panelu
 * otwiera go od razu na tej stronie) albo Options (tylko pad sparowany z Questem). Krzyżak
 * / drążek góra-dół wybiera wiersz, lewo-prawo albo ✕ / A zmienia wartość,
 * ○ / B zamyka panel jak na stronie 1.
 *
 * Każda zmiana zapisuje się w tych samych ustawieniach co launcher. Wiersz
 * mówi uczciwie, kiedy zadziała: na żywo (od razu), od następnego połączenia
 * (P5M ustawia to przy starcie sesji: ścieżka, kolor, włączenie 3D) albo
 * wcale w tej sesji (np. siła 3D w sesji bez 3D). Stan sesji to zdjęcie
 * ustawień z chwili pierwszego otwarcia tej strony — zmienia je tylko ta
 * strona, więc wcześniej są takie jak przy połączeniu.
 *
 * Bitrate celowo poza panelem: w trakcie sesji zmienia go automatyczny
 * bitrate P5M, ręczna zmiana mogłaby z nim kolidować.
 */
class PanelZaawansowany(
	private val activity: Activity,
	private val jakosc: StreamQualityPrefs,
	private val stan: StanPanelu,
	private val xr: () -> XrBridge?,
	private val zastosujJakosc: () -> Unit,
	private val padPrzyQuescie: () -> Boolean,
	private val dziennik: (String) -> Unit)
{
	enum class Kiedy { NA_ZYWO, NASTEPNE_POLACZENIE, NIE_W_TEJ_SESJI, NIEAKTYWNE }

	private class Wiersz(
		val sekcja: Int,
		val nazwa: String,
		val wartosc: () -> String,
		val kiedy: () -> Kiedy,
		val uwaga: () -> String?,
		val zmien: (Int) -> Unit)

	// Zdjęcie sesji (patrz opis klasy).
	private val sesjaShader = jakosc.toneMapped || jakosc.syntheticStereo || jakosc.tenBitNaSessao
	private val sesja3D = jakosc.syntheticStereo
	private val sesja10 = jakosc.tenBitNaSessao
	private val ekran = ScreenPrefs(activity)

	/** Sieć głębi w tej sesji; stan początkowy jak przy starcie sesji (VrStreamActivity). */
	private val neuronowa = GlebiaNeuronowa(activity, sesja3D && jakosc.neuralDepth)

	private var wybrany = 0
	private var hatX = 0
	private var hatY = 0

	private fun krok5(v: Float, kierunek: Int) = (((v * 4f + 0.5f).toInt() + kierunek + 5) % 5) / 4f
	private fun pct(v: Float) = "${(v * 100).toInt()}%"
	private fun wlacz(b: Boolean) = if(b) "On" else "Off"

	private val wiersze: List<Wiersz> = listOf(
		// --- KLATKI I PANEL
		Wiersz(0, "Generated frames",
				{ if(!jakosc.frameExtrapolation) "Off" else GenerowanieKlatek.nazwa(activity) },
				{ if(sesjaShader) Kiedy.NA_ZYWO else Kiedy.NASTEPNE_POLACZENIE },
				{ if(sesjaShader) null else "Needs the shader path: from the next connection." },
				{ k ->
					val obecny = if(!jakosc.frameExtrapolation) 0 else if(GenerowanieKlatek.do60(activity)) 1 else 2
					val nowy = (obecny + k + 3) % 3
					if(nowy == 0)
						jakosc.frameExtrapolation = false
					else
					{
						// Jak w launcherze: przewidywanie wymaga ścieżki shader.
						if(!jakosc.immersiveUsesShader)
							jakosc.toneMapped = true
						jakosc.frameExtrapolation = true
						Qs8Prefs(activity).klatkiDo60 = nowy == 1
					}
					GenerowanieKlatek.zastosuj(activity)
					zastosujJakosc()
				}),
		Wiersz(0, "Panel rate",
				{ if(jakosc.panel180) "180 Hz" else "120 Hz" },
				{ Kiedy.NA_ZYWO }, { null },
				{ _ ->
					jakosc.panel180 = !jakosc.panel180
					xr()?.selectDisplayRefreshRate(jakosc.sourceFps, jakosc.panelMaxHz)
				}),
		Wiersz(0, "HDR peak",
				{ "${PeakHdr.wartosc(activity)} nits" },
				{ if(sesja10) Kiedy.NA_ZYWO else Kiedy.NIE_W_TEJ_SESJI },
				{ if(sesja10) null else "Only with 10-bit color." },
				{ k -> if(k < 0) PeakHdr.poprzedni(activity) else PeakHdr.nastepny(activity) }),
		Wiersz(0, Mqsr.NAZWA,
				{ wlacz(Mqsr.wlaczone(activity)) },
				{ if(Mqsr.aktywne(jakosc)) Kiedy.NA_ZYWO else Kiedy.NIEAKTYWNE },
				{ if(Mqsr.aktywne(jakosc)) "Watch dropped frames and latency on page 1." else "Only with Sharpness: Automatic (set it on page 1)." },
				{ _ -> Mqsr.przelacz(activity) }),
		Wiersz(0, Poswiata.NAZWA,
				{ Poswiata.nazwa(activity) },
				{ when
					{
						!sesjaShader -> Kiedy.NIE_W_TEJ_SESJI
						!Poswiata.ciemnyPokoj(ekran) -> Kiedy.NIEAKTYWNE
						else -> Kiedy.NA_ZYWO
					} },
				{ when
					{
						!sesjaShader -> "Needs the shader path."
						!Poswiata.ciemnyPokoj(ekran) -> "Only in a dark room: set Room on page 1 to hidden or cinema."
						else -> null
					} },
				{ k -> Poswiata.zmien(activity, k) }),
		// --- 3D
		Wiersz(1, "3D strength",
				{ if(jakosc.stereoStrength <= 0f) "0% (2D)" else pct(jakosc.stereoStrength) },
				{ if(sesja3D) Kiedy.NA_ZYWO else Kiedy.NIE_W_TEJ_SESJI },
				{ if(sesja3D) null else "Only in a 3D session: turn on Emulated 3D." },
				{ k ->
					jakosc.stereoStrength = krok5(jakosc.stereoStrength, k)
					xr()?.setStereoTuning(jakosc.stereoStrength, jakosc.stereoConvergence)
				}),
		Wiersz(1, "Screen depth",
				{ pct(jakosc.stereoConvergence) },
				{ if(sesja3D) Kiedy.NA_ZYWO else Kiedy.NIE_W_TEJ_SESJI },
				{ if(sesja3D) null else "Only in a 3D session: turn on Emulated 3D." },
				{ k ->
					jakosc.stereoConvergence = krok5(jakosc.stereoConvergence, k)
					xr()?.setStereoTuning(jakosc.stereoStrength, jakosc.stereoConvergence)
				}),
		Wiersz(1, "Pop-out",
				{ wlacz(jakosc.stereoPopOut) },
				{ if(sesja3D) Kiedy.NA_ZYWO else Kiedy.NIE_W_TEJ_SESJI },
				{ if(sesja3D) null else "Only in a 3D session: turn on Emulated 3D." },
				{ _ ->
					jakosc.stereoPopOut = !jakosc.stereoPopOut
					xr()?.setStereoPopOut(jakosc.stereoPopOut)
				}),
		Wiersz(1, "Neural depth",
				{ wlacz(jakosc.neuralDepth) },
				{ if(sesja3D) Kiedy.NA_ZYWO else Kiedy.NIE_W_TEJ_SESJI },
				{ if(!sesja3D) "Only in a 3D session: turn on Emulated 3D."
					else "Measured: about 35 ms latency with it, 17 ms without." },
				{ _ ->
					jakosc.neuralDepth = !jakosc.neuralDepth
					if(sesja3D)
						neuronowa.ustaw(jakosc.neuralDepth)
				}),
		// --- OD NASTĘPNEGO POŁĄCZENIA
		Wiersz(2, "Video path",
				{ when
					{
						jakosc.syntheticStereo -> "shader (3D)"
						jakosc.tenBit -> "shader (10-bit)"
						jakosc.toneMapped -> "shader"
						else -> "direct"
					} },
				{ Kiedy.NASTEPNE_POLACZENIE },
				{ if(jakosc.syntheticStereo || jakosc.tenBit) "3D and 10-bit always use the shader." else null },
				{ _ ->
					if(!jakosc.syntheticStereo && !jakosc.tenBit)
					{
						jakosc.toneMapped = !jakosc.toneMapped
						// Jak w launcherze: bez shadera nie ma przewidywania klatek.
						if(!jakosc.toneMapped)
							jakosc.frameExtrapolation = false
					}
				}),
		Wiersz(2, "Color",
				{ if(jakosc.tenBit) "10-bit" else "8-bit" },
				{ Kiedy.NASTEPNE_POLACZENIE }, { null },
				{ _ -> jakosc.tenBit = !jakosc.tenBit }),
		Wiersz(2, "Emulated 3D",
				{ wlacz(jakosc.syntheticStereo) },
				{ Kiedy.NASTEPNE_POLACZENIE }, { null },
				{ _ -> jakosc.syntheticStereo = !jakosc.syntheticStereo }),
		// --- EKRAN (pod „od następnego połączenia”)
		Wiersz(3, NaLezaco.NAZWA,
				{ NaLezaco.nazwa() },
				{ Kiedy.NA_ZYWO },
				{ if(NaLezaco.wlaczony()) "Look where you want the screen and recenter. Off returns to normal."
					else "The screen goes where you look, also up. This session only." },
				{ k ->
					NaLezaco.zmien(k)
					stan.pokaz(NaLezaco.komunikat(), 1500L)
				})
	)

	/** Koniec sesji, przed zatrzymaniem sieci głębi przez P5M. */
	fun zamknij() = neuronowa.zamknij()

	/** Przytrzymanie ≡ / Options: strona 1 ↔ strona 2. */
	fun przelacz()
	{
		stan.strona2 = !stan.strona2
		hatX = 0
		hatY = 0
		dziennik("QS8: settings panel page ${if(stan.strona2) "2 (advanced)" else "1"}")
	}

	/**
	 * Klawisz na stronie 2 (wołane tylko, gdy [StanPanelu.strona2]). true =
	 * obsłużony (nie idzie do P5M). Puszczenia (UP) zawsze false: P5M oddaje je
	 * konsoli. PS (klik touchpada) zostaje w P5M.
	 */
	fun klawisz(event: KeyEvent): Boolean
	{
		if(event.action != KeyEvent.ACTION_DOWN || event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE)
			return false
		val pionowo = event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
		if(event.repeatCount > 0 && !pionowo)
			return true
		when(event.keyCode)
		{
			KeyEvent.KEYCODE_DPAD_UP -> przesun(-1)
			KeyEvent.KEYCODE_DPAD_DOWN -> przesun(1)
			KeyEvent.KEYCODE_DPAD_LEFT -> zmien(-1)
			KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_A -> zmien(1)
		}
		// Reszta przycisków na tej stronie nic nie robi: nie zmienia po cichu
		// ustawień ze strony 1, których tu nie widać.
		return true
	}

	/** Krzyżak DualSense przychodzi jako oś HAT, nie klawisz. true = coś się zmieniło. */
	fun hat(event: MotionEvent): Boolean
	{
		fun krok(v: Float) = if(v > 0.5f) 1 else if(v < -0.5f) -1 else 0
		val x = krok(event.getAxisValue(MotionEvent.AXIS_HAT_X))
		val y = krok(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
		var zmiana = false
		if(y != hatY) { hatY = y; if(y != 0) { przesun(y); zmiana = true } }
		if(x != hatX) { hatX = x; if(x != 0) { zmien(x); zmiana = true } }
		return zmiana
	}

	private fun przesun(o: Int)
	{
		wybrany = (wybrany + o + wiersze.size) % wiersze.size
	}

	private fun zmien(kierunek: Int)
	{
		val w = wiersze[wybrany]
		w.zmien(kierunek)
		val kiedy = when(w.kiedy())
		{
			Kiedy.NA_ZYWO -> "live"
			Kiedy.NASTEPNE_POLACZENIE -> "from the next connection"
			Kiedy.NIE_W_TEJ_SESJI -> "saved, not in this session"
			Kiedy.NIEAKTYWNE -> "saved, inactive with current settings"
		}
		dziennik("QS8: advanced ${w.nazwa} = ${w.wartosc()} ($kiedy)")
	}

	// ------------------------------------------------------------------ rysowanie

	private val TEKST = Color.rgb(238, 241, 247)
	private val SUAVE = Color.rgb(142, 152, 173)
	private val CABECALHO = Color.rgb(125, 135, 156)
	private val DESTAQUE = Color.rgb(140, 188, 255)
	private val AVISO = Color.rgb(217, 184, 112)
	private val ZYWO = Color.rgb(95, 211, 141)

	private val SEKCJE = arrayOf("PICTURE", "3D", "NEXT CONNECTION", "SCREEN")

	private fun t(s: String) = Tlumaczenia.t(s)

	fun rysuj(bitmap: Bitmap)
	{
		val c = Canvas(bitmap)
		c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
		val w = bitmap.width.toFloat()
		val h = bitmap.height.toFloat()
		PainelDeAjuste.fundo(c, RectF(8f, 8f, w - 8f, h - 8f))

		val p = Paint(Paint.ANTI_ALIAS_FLAG)
		val margem = 52f
		val vao = 44f

		// Nagłówek: tytuł, podpowiedź sterowania, uwaga do wybranego wiersza.
		p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
		p.textSize = 34f
		p.color = TEKST
		c.drawText(t("ADVANCED") + "  ·  2/2", margem, 66f, p)
		p.typeface = Typeface.DEFAULT
		LegendaTouch.naglowekStrony2(c, w, padPrzyQuescie())

		// Komunikat (A/B, rozmiar 1:1, Peak HDR ze strony 1) zajmuje na chwilę wiersz stanu.
		val komunikat = stan.aktualny()
		if(komunikat != null)
			pigulka(c, komunikat, margem)
		else
			stanWiersza(c, p, margem)

		val szer = (w - 2 * margem - 2 * vao) / 3
		val gora = 168f
		val wys = 56f  // pięć wierszy w kolumnie „Obraz”
		for(s in SEKCJE.indices)
		{
			// Czwarta sekcja ma jeden wiersz i stoi na dole trzeciej kolumny.
			val x0 = margem + minOf(s, 2) * (szer + vao)
			val y0 = if(s < 3) gora else gora + 4 * wys
			p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
			p.textSize = 22f
			p.color = CABECALHO
			p.letterSpacing = 0.08f
			c.drawText(t(SEKCJE[s]), x0, y0 - 16f, p)
			p.letterSpacing = 0f
			for((j, wiersz) in wiersze.filter { it.sekcja == s }.withIndex())
			{
				val indeks = wiersze.indexOf(wiersz)
				val y = y0 + j * wys
				val r = RectF(x0 - 14f, y + 4f, x0 + szer + 14f, y + wys - 4f)
				val aktywny = indeks == wybrany
				if(aktywny)
				{
					c.drawRoundRect(r, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 140, 188, 255) })
					c.drawRoundRect(r, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
						style = Paint.Style.STROKE; strokeWidth = 3f; color = DESTAQUE })
				}
				val wygaszony = wiersz.kiedy() == Kiedy.NIE_W_TEJ_SESJI || wiersz.kiedy() == Kiedy.NIEAKTYWNE
				val linia = y + wys / 2 + 10f
				p.typeface = Typeface.DEFAULT
				p.textSize = 28f
				p.color = if(wygaszony) Color.argb(150, 142, 152, 173) else SUAVE
				c.drawText(t(wiersz.nazwa), x0, linia, p)
				val pozniej = wiersz.kiedy() == Kiedy.NASTEPNE_POLACZENIE
				val wartosc = t(wiersz.wartosc())
				p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
				p.textSize = 30f
				p.color = when
				{
					wygaszony -> Color.argb(150, 238, 241, 247)
					aktywny -> DESTAQUE
					else -> TEKST
				}
				val prawa = x0 + szer - if(pozniej) 36f else 0f
				c.drawText(wartosc, prawa - p.measureText(wartosc), linia, p)
				if(pozniej)
					ponownie(c, x0 + szer - 12f, y + wys / 2)
			}
		}
	}

	/** Kiedy zadziała wybrany wiersz i uwaga do niego. */
	private fun stanWiersza(c: Canvas, p: Paint, x0: Float)
	{
		val sel = wiersze[wybrany]
		val (opisKiedy, kolorKiedy) = when(sel.kiedy())
		{
			Kiedy.NA_ZYWO -> t("Changes live") to ZYWO
			Kiedy.NASTEPNE_POLACZENIE -> t("Saved. Takes effect from the next connection.") to AVISO
			Kiedy.NIE_W_TEJ_SESJI -> t("Saved. No effect in this session.") to AVISO
			Kiedy.NIEAKTYWNE -> t("Saved. Not active with the current settings.") to AVISO
		}
		p.textSize = 27f
		p.color = kolorKiedy
		c.drawText(opisKiedy, x0, 116f, p)
		val x = x0 + p.measureText(opisKiedy)
		sel.uwaga()?.let {
			p.color = SUAVE
			c.drawText("  ·  " + t(it), x, 116f, p)
		}
	}

	private fun pigulka(c: Canvas, tekst: String, x0: Float)
	{
		val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.rgb(16, 24, 40)
			textSize = 26f
			typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
		}
		c.drawRoundRect(RectF(x0, 88f, x0 + p.measureText(tekst) + 48f, 128f), 20f, 20f,
				Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DESTAQUE })
		c.drawText(tekst, x0 + 24f, 117f, p)
	}

	/** Strzałka w kółko (od następnego połączenia), rysowana: znak z czcionki może nie istnieć w goglach. */
	private fun ponownie(c: Canvas, cx: Float, cy: Float)
	{
		val r = 9f
		val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = AVISO
			style = Paint.Style.STROKE
			strokeWidth = 3f
			strokeCap = Paint.Cap.ROUND
		}
		c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -20f, 290f, false, p)
		// Koniec łuku: 270 stopni, czyli góra kółka; ruch w prawo.
		p.style = Paint.Style.FILL
		c.drawPath(Path().apply {
			moveTo(cx - 1f, cy - r - 6f)
			lineTo(cx + 7f, cy - r)
			lineTo(cx - 1f, cy - r + 6f)
			close()
		}, p)
	}
}
