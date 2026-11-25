// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import io.github.gblandro.p5m.PainelDeAjuste

/**
 * Przyciski kontrolerów Questa obok przycisków PS5 w panelu ustawień i w
 * przypomnieniu na starcie sesji („A / ✕  Recenter”).
 *
 * Panel P5M rysuje przy każdej akcji tylko przycisk pada. Przy kontrolerach
 * Questa (plan 1B) to myliło: regulacje robi się Touch, a legenda pokazywała
 * pad. Oznaczenia PS5 zostają, bo te same akcje działają z padem sparowanym
 * z Questem.
 *
 * Wygląd przycisków PS5 skopiowany z [PainelDeAjuste] (tam jest prywatny),
 * w mniejszym rozmiarze, żeby para zmieściła się w komórce wiersza.
 */
object LegendaTouch
{
	/** O tyle wszystkie wiersze panelu przesuwają tekst w prawo (szersza komórka z dwoma przyciskami). */
	const val PRZESUNIECIE = 26f

	private val TEKST = Color.rgb(238, 241, 247)
	private val SZARY = Color.rgb(142, 152, 173)
	private val KOR_KRZYZYK = Color.rgb(127, 180, 238)
	private val KOR_KOLKO = Color.rgb(255, 122, 122)
	private val KOR_KWADRAT = Color.rgb(230, 144, 207)
	private val KOR_TROJKAT = Color.rgb(67, 214, 178)

	/** Szerokość komórki na parę przycisków: od x0-10 do x0+104 (tekst zaczyna się na x0+112). */
	private const val LEWA = -10f
	private const val PRAWA = 104f

	private sealed class Element
	{
		/** Okrągły przycisk Questa z literą (A, B, X, Y). */
		data class Litera(val litera: String): Element()
		/** Thumbstick Questa (wychylenie którejkolwiek). */
		object Galka: Element()
		/** Wciśnięcie lewego ("L") albo prawego ("R") thumbsticka Questa. */
		data class KlikGalki(val strona: String): Element()
		/** Przycisk ≡ Questa. */
		object Menu: Element()
		/** Przycisk Questa w pigułce z nazwą (gripy, triggery). */
		data class QuestNazwa(val tekst: String): Element()
		object Ukosnik: Element()
		/** Przycisk PS5: kształt, krzyżak albo pigułka z nazwą. */
		data class Ps(val botao: PainelDeAjuste.Botao): Element()
	}

	/** Przycisk Questa dla przycisku PS5 z wiersza panelu; null = akcja tylko z pada. */
	private fun questDla(botao: PainelDeAjuste.Botao): Element? = when(botao)
	{
		PainelDeAjuste.Botao.Cruz -> Element.Litera("A")
		PainelDeAjuste.Botao.Circulo -> Element.Litera("B")
		PainelDeAjuste.Botao.Quadrado -> Element.Litera("X")
		PainelDeAjuste.Botao.Triangulo -> Element.Litera("Y")
		PainelDeAjuste.Botao.DirecionalVertical, PainelDeAjuste.Botao.DirecionalHorizontal -> Element.Galka
		is PainelDeAjuste.Botao.Nome -> when(botao.texto.replace(" ", ""))
		{
			"L1R1" -> Element.QuestNazwa("Grip")
			"L2" -> Element.QuestNazwa("LT")
			"R2" -> Element.QuestNazwa("RT")
			"Share" -> Element.KlikGalki("L")
			"Options" -> Element.KlikGalki("R")
			// R3, PS: TouchMenu ich nie wysyła (PS = touchpad konsoli, nigdy z Touch).
			else -> null
		}
	}

	/**
	 * Komórka przycisku w wierszu panelu (w miejsce rysunku P5M): przycisk Questa,
	 * ukośnik, przycisk PS5, wyrównane do prawej krawędzi komórki. Zwraca
	 * [PRZESUNIECIE] tekstu wiersza.
	 */
	fun komorka(canvas: Canvas, x0: Float, cy: Float, botao: PainelDeAjuste.Botao): Float
	{
		val quest = questDla(botao)
		val elementy = if(quest != null) listOf(quest, Element.Ukosnik, Element.Ps(botao)) else listOf(Element.Ps(botao))
		rzad(canvas, elementy, x0 + PRAWA, cy, PRAWA - LEWA, 17f)
		return PRZESUNIECIE
	}

	/**
	 * Prawa strona nagłówka panelu: „≡ / L3+R3+R1  or  B / ○ close · B / ○ hold to
	 * end the session”. Rysuje też kreskę pod nagłówkiem (P5M robi ją na końcu
	 * swojego nagłówka, który QS8 pomija).
	 */
	fun naglowek(canvas: Canvas, w: Float, atalho: String)
	{
		val dica = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SZARY; textSize = 22f; textAlign = Paint.Align.RIGHT }
		var x = w - 52f
		val cy = 66f
		fun tekst(en: String)
		{
			val t = Tlumaczenia.t(en)
			canvas.drawText(t, x, 74f, dica)
			x -= dica.measureText(t) + 14f
		}
		fun para(quest: Element, ps: PainelDeAjuste.Botao)
		{
			x -= rzad(canvas, listOf(quest, Element.Ukosnik, Element.Ps(ps)), x, cy, 1000f, 19f) + 14f
		}
		tekst("hold to end the session")
		para(Element.Litera("B"), PainelDeAjuste.Botao.Circulo)
		x -= 12f
		tekst("close  ·")
		para(Element.Litera("B"), PainelDeAjuste.Botao.Circulo)
		x -= 2f
		tekst("or")
		para(Element.Menu, PainelDeAjuste.Botao.Nome(atalho))

		canvas.drawRect(52f, 112f, w - 52f, 113f, Paint().apply { color = Color.argb(18, 255, 255, 255) })
	}

	/**
	 * Linia nad legendą strony 1: co robią przytrzymania. Przyciski są rysowane, bo
	 * czcionka nie ma znaków ≡ ✕ □ △; A, X, Y to te same przyciski co ✕, □, △ pada
	 * (pary stoją przy wierszach wyżej). Mieści się między [x0] a [x1].
	 */
	fun przytrzymania(canvas: Canvas, x0: Float, x1: Float, cy: Float, padPrzyQuescie: Boolean)
	{
		val grupy = listOf(
			elementyStrony(padPrzyQuescie) to "page 2, Advanced",
			listOf<Element>(Element.Litera("A")) to "size 1:1",
			listOf<Element>(Element.Litera("X")) to "original / enhanced",
			listOf<Element>(Element.Litera("Y")) to "HDR peak",
			(if(padPrzyQuescie) listOf(Element.QuestNazwa("LT"), Element.Ukosnik, Element.Ps(PainelDeAjuste.Botao.Nome("L2")))
				else listOf<Element>(Element.QuestNazwa("LT"))) to "lying down")
			.map { (e, en) -> e to Tlumaczenia.t(en) }
		val wstep = Tlumaczenia.t("Hold:")
		val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(140, 188, 255); textSize = 20f }
		fun szer() = p.measureText(wstep) + 14f + grupy.sumOf { (e, t) ->
			(e.sumOf { szerokosc(it, 17f).toDouble() } + 8f + p.measureText(t) + 28f) }.toFloat()
		while(p.textSize > 13f && szer() > x1 - x0)
			p.textSize -= 1f
		canvas.drawText(wstep, x0, cy + 7f, p)
		var x = x0 + p.measureText(wstep) + 14f
		for((e, t) in grupy)
		{
			x += rzadOdLewej(canvas, e, x, cy, 17f) + 8f
			canvas.drawText(t, x, cy + 7f, p)
			x += p.measureText(t) + 28f
		}
	}

	/** Przycisk strony panelu: ≡, a z padem sparowanym z Questem też Options (w Trybie domowym idzie do konsoli). */
	private fun elementyStrony(padPrzyQuescie: Boolean) =
		if(padPrzyQuescie) listOf(Element.Menu, Element.Ukosnik, Element.Ps(PainelDeAjuste.Botao.Nome("Options")))
		else listOf<Element>(Element.Menu)

	/** Prawa strona nagłówka strony 2: wybór, zmiana, powrót na stronę 1, zamknięcie. Same rysunki przycisków. */
	fun naglowekStrony2(canvas: Canvas, w: Float, padPrzyQuescie: Boolean)
	{
		val dica = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SZARY; textSize = 22f; textAlign = Paint.Align.RIGHT }
		var x = w - 52f
		val cy = 58f
		fun tekst(en: String)
		{
			val t = Tlumaczenia.t(en)
			canvas.drawText(t, x, 66f, dica)
			x -= dica.measureText(t) + 14f
		}
		fun przyciski(elementy: List<Element>, odstep: Float)
		{
			x -= rzad(canvas, elementy, x, cy, 1000f, 19f) + odstep
		}
		tekst("close")
		przyciski(listOf(Element.Litera("B"), Element.Ukosnik, Element.Ps(PainelDeAjuste.Botao.Circulo)), 26f)
		tekst("page 1  ·")
		przyciski(elementyStrony(padPrzyQuescie), 10f)
		tekst("hold")
		x -= 12f
		tekst("change  ·")
		przyciski(listOf(Element.Litera("A"), Element.Ukosnik, Element.Ps(PainelDeAjuste.Botao.Cruz)), 10f)
		przyciski(listOf(Element.Galka, Element.Ukosnik, Element.Ps(PainelDeAjuste.Botao.DirecionalHorizontal)), 26f)
		tekst("choose  ·")
		przyciski(listOf(Element.Galka, Element.Ukosnik, Element.Ps(PainelDeAjuste.Botao.DirecionalVertical)), 0f)
	}

	/**
	 * Przypomnienie na starcie sesji (w miejsce faixy P5M o samym akordzie).
	 * [padPrzyQuescie]: pad sparowany z goglami. Bez niego to Tryb domowy — pad
	 * gra przez konsolę, a akord na nim nie dociera do aplikacji, więc pierwszy
	 * jest przycisk ≡ i nic nie wygląda jak błąd.
	 */
	fun przypomnienie(bitmap: Bitmap, akord: String, padPrzyQuescie: Boolean)
	{
		val canvas = Canvas(bitmap)
		canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
		val w = bitmap.width.toFloat()
		val r = RectF(w / 2f - 720f, 8f, w / 2f + 720f, 200f)
		PainelDeAjuste.fundo(canvas, r)

		val tytul = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = TEKST
			textSize = 34f
			typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
		}
		val zwykly = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SZARY; textSize = 26f }
		val x = r.left + 48f
		val cy = r.top + 56f
		val linie: List<String>
		var xTytulu: Float
		if(padPrzyQuescie)
		{
			xTytulu = x + rzadOdLewej(canvas, listOf(Element.Ps(PainelDeAjuste.Botao.Nome(akord))), x, cy, 24f) + 16f
			val lub = Tlumaczenia.t("or")
			canvas.drawText(lub, xTytulu, r.top + 66f, zwykly)
			xTytulu += zwykly.measureText(lub) + 16f
			xTytulu += rzadOdLewej(canvas, listOf(Element.Menu), xTytulu, cy, 24f) + 20f
			canvas.drawText(Tlumaczenia.t("opens the settings panel"), xTytulu, r.top + 68f, tytul)
			linie = listOf(
				"The menu button on the left Quest controller works too. Same button closes the panel.",
				"Move, resize and sharpen the screen, and click the touchpad.")
		}
		else
		{
			xTytulu = x + rzadOdLewej(canvas, listOf(Element.Menu), x, cy, 24f) + 20f
			canvas.drawText(Tlumaczenia.t("on the left Quest controller opens the settings panel"), xTytulu, r.top + 68f, tytul)
			linie = listOf(
				"Home mode: your PS5 controller is connected to the console, as it should be.",
				if(Tlumaczenia.polski) "Gdy pad jest sparowany z Questem, panel otwiera też $akord."
				else "With a controller paired to the Quest instead, $akord opens the panel too.")
		}
		var y = r.top + 120f
		for(en in linie)
		{
			val linia = Tlumaczenia.t(en)
			val p = Paint(zwykly).apply { textSize = 27f }
			while(p.textSize > 18f && p.measureText(linia) > r.width() - 96f)
				p.textSize -= 1f
			canvas.drawText(linia, x, y, p)
			y += 40f
		}
	}

	/** Rysuje elementy od lewej od [x]; zwraca zajętą szerokość. */
	private fun rzadOdLewej(canvas: Canvas, elementy: List<Element>, x: Float, cy: Float, czcionka: Float): Float
	{
		val szer = elementy.sumOf { szerokosc(it, czcionka).toDouble() }.toFloat()
		return rzad(canvas, elementy, x + szer, cy, 1000f, czcionka)
	}

	/**
	 * Rysuje elementy tak, by kończyły się na [prawa]; czcionkę pigułek zmniejsza,
	 * aż całość zmieści się w [maks]. Zwraca zajętą szerokość.
	 */
	private fun rzad(canvas: Canvas, elementy: List<Element>, prawa: Float, cy: Float, maks: Float,
			czcionka: Float): Float
	{
		var f = czcionka
		fun suma() = elementy.sumOf { szerokosc(it, f).toDouble() }.toFloat()
		while(f > 12f && suma() > maks)
			f -= 1f
		val calosc = suma()
		var x = prawa - calosc
		for(e in elementy)
		{
			val s = szerokosc(e, f)
			rysuj(canvas, e, x + s / 2f, cy, f)
			x += s
		}
		return calosc
	}

	private fun pigulka(czcionka: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = TEKST
		textSize = czcionka
		textAlign = Paint.Align.CENTER
		typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
	}

	private fun polowaWysokosci(czcionka: Float) = maxOf(16f, czcionka * 0.85f)

	private fun szerPigulki(tekst: String, czcionka: Float, min: Float) =
		maxOf(min, pigulka(czcionka).measureText(tekst) + czcionka * 0.9f)

	private fun szerokosc(e: Element, f: Float): Float = when(e)
	{
		is Element.Litera, Element.Galka -> 32f
		is Element.KlikGalki -> 44f
		Element.Menu -> 40f
		is Element.QuestNazwa -> szerPigulki(e.tekst, f, 32f)
		Element.Ukosnik -> 20f
		is Element.Ps -> when(val b = e.botao)
		{
			is PainelDeAjuste.Botao.Nome -> szerPigulki(b.texto, f, 36f)
			else -> 38f
		}
	}

	private fun rysuj(canvas: Canvas, e: Element, cx: Float, cy: Float, f: Float)
	{
		// Przyciski Questa: jasnoszare tło (jak fizyczne przyciski Touch), PS5: kolorowe kształty i ciemniejsze pigułki.
		val tloQuest = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(64, 255, 255, 255) }
		when(e)
		{
			is Element.Litera ->
			{
				canvas.drawCircle(cx, cy, 16f, tloQuest)
				canvas.drawText(e.litera, cx, cy + 7f, pigulka(19f))
			}
			Element.Galka ->
			{
				canvas.drawCircle(cx, cy, 16f, tloQuest)
				canvas.drawCircle(cx, cy, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
					style = Paint.Style.STROKE; strokeWidth = 2.5f; color = TEKST
				})
				canvas.drawCircle(cx, cy, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEKST })
			}
			is Element.KlikGalki ->
			{
				// Gałka z kropką (wciśnięta) i literą strony obok.
				val g = cx - 6f
				canvas.drawCircle(g, cy, 16f, tloQuest)
				canvas.drawCircle(g, cy, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
					style = Paint.Style.STROKE; strokeWidth = 2.5f; color = TEKST
				})
				canvas.drawCircle(g, cy, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEKST })
				canvas.drawText(e.strona, cx + 16f, cy + 6f, Paint(pigulka(16f)))
			}
			Element.Menu ->
			{
				canvas.drawRoundRect(RectF(cx - 20f, cy - 16f, cx + 20f, cy + 16f), 16f, 16f, tloQuest)
				val kreska = Paint(Paint.ANTI_ALIAS_FLAG).apply {
					color = TEKST; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
				}
				for(dy in floatArrayOf(-7f, 0f, 7f))
					canvas.drawLine(cx - 9f, cy + dy, cx + 9f, cy + dy, kreska)
			}
			is Element.QuestNazwa ->
			{
				val s = szerPigulki(e.tekst, f, 32f)
				val h = polowaWysokosci(f)
				canvas.drawRoundRect(RectF(cx - s / 2f, cy - h, cx + s / 2f, cy + h), h, h, tloQuest)
				canvas.drawText(e.tekst, cx, cy + f * 0.36f, pigulka(f))
			}
			Element.Ukosnik ->
				canvas.drawText("/", cx, cy + 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
					color = SZARY; textSize = 22f; textAlign = Paint.Align.CENTER
				})
			is Element.Ps -> ps(canvas, e.botao, cx, cy, f)
		}
	}

	private fun ps(canvas: Canvas, b: PainelDeAjuste.Botao, cx: Float, cy: Float, f: Float)
	{
		when(b)
		{
			is PainelDeAjuste.Botao.Nome ->
			{
				val s = szerPigulki(b.texto, f, 36f)
				val h = polowaWysokosci(f)
				canvas.drawRoundRect(RectF(cx - s / 2f, cy - h, cx + s / 2f, cy + h), 9f, 9f,
						Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(26, 255, 255, 255) })
				canvas.drawText(b.texto, cx, cy + f * 0.36f, pigulka(f))
			}
			PainelDeAjuste.Botao.DirecionalVertical, PainelDeAjuste.Botao.DirecionalHorizontal ->
			{
				val a = 6.5f
				val d = 18f
				val zgaszony = Paint().apply { color = Color.argb(56, 255, 255, 255) }
				canvas.drawRect(cx - a, cy - d, cx + a, cy + d, zgaszony)
				canvas.drawRect(cx - d, cy - a, cx + d, cy + a, zgaszony)
				val zapalony = Paint().apply { color = TEKST }
				if(b == PainelDeAjuste.Botao.DirecionalVertical)
				{
					canvas.drawRect(cx - a, cy - d, cx + a, cy - a, zapalony)
					canvas.drawRect(cx - a, cy + a, cx + a, cy + d, zapalony)
				}
				else
				{
					canvas.drawRect(cx - d, cy - a, cx - a, cy + a, zapalony)
					canvas.drawRect(cx + a, cy - a, cx + d, cy + a, zapalony)
				}
			}
			else ->
			{
				canvas.drawCircle(cx, cy, 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(18, 255, 255, 255) })
				val r = 14f
				val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
					style = Paint.Style.STROKE
					strokeWidth = 3.2f
					strokeCap = Paint.Cap.ROUND
					strokeJoin = Paint.Join.ROUND
				}
				when(b)
				{
					PainelDeAjuste.Botao.Circulo -> { p.color = KOR_KOLKO; canvas.drawCircle(cx, cy, r * 0.8f, p) }
					PainelDeAjuste.Botao.Cruz ->
					{
						p.color = KOR_KRZYZYK
						val d = r * 0.7f
						canvas.drawLine(cx - d, cy - d, cx + d, cy + d, p)
						canvas.drawLine(cx + d, cy - d, cx - d, cy + d, p)
					}
					PainelDeAjuste.Botao.Quadrado ->
					{
						p.color = KOR_KWADRAT
						val d = r * 0.65f
						canvas.drawRect(cx - d, cy - d, cx + d, cy + d, p)
					}
					PainelDeAjuste.Botao.Triangulo ->
					{
						p.color = KOR_TROJKAT
						canvas.drawPath(Path().apply {
							moveTo(cx, cy - r * 0.8f)
							lineTo(cx + r * 0.8f, cy + r * 0.55f)
							lineTo(cx - r * 0.8f, cy + r * 0.55f)
							close()
						}, p)
					}
					else -> {}
				}
			}
		}
	}
}
