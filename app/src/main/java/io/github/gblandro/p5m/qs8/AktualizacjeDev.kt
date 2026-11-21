// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import io.github.gblandro.p5m.Atualizacao
import io.github.gblandro.p5m.Trace
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aktualizacje QS8 z rolling release „dev” forka.
 *
 * P5M szuka tylko wydań z tagiem `vX.Y` i w buildach -dev nie pokazuje karty.
 * QS8 nie ma (na razie) wydań z tagiem: każdy push buduje APK do jednego
 * release „dev”. Ten obiekt zastępuje tylko źródło oferty w [Atualizacao];
 * karta, „Skip this version”, wyłącznik i pobieranie z instalatorem
 * ([io.github.gblandro.p5m.InstaladorDeAtualizacao]) zostają z P5M.
 *
 * Workflow buduje każdą gałąź do tego samego release, więc karta mówi, co to
 * za build: numer, gałąź, PR i tytuł commita. Dwa zapytania do publicznego API
 * (release + przebieg Actions dla commita), bez logowania.
 */
object AktualizacjeDev
{
	private const val API = "https://api.github.com/repos/oltermcom-afk/QuestStation8"

	/** Wersjonowany asset z workflow: `p5m-<wersja>-dev.<numer builda>.apk`. */
	private val ASSET = Regex("""^p5m-(\d+\.\d+\.\d+-dev\.(\d+))\.apk$""")
	private val COMMIT = Regex("""Built from commit ([0-9a-f]{40})""")

	/** Opis wyłącznika „Check for updates” w launcherze. */
	const val OPIS_WYLACZNIKA = "Looks for a new QuestStation8 build (the fork's dev release) when the " +
			"app opens, at most every 15 minutes. Update downloads it and opens the installer."

	/**
	 * Build z release „dev”, do porównania z zainstalowanym przez
	 * [Atualizacao.comparar] (numer builda dev.N jest ostatnim, liczbowym
	 * członem wersji). null, gdy release nie ma wersjonowanego APK.
	 */
	fun buscar(context: Context): Atualizacao.Oferta?
	{
		val release = JSONObject(pobierz("$API/releases/tags/dev") ?: return null)
		val assets = release.optJSONArray("assets") ?: return null
		var oferta: Atualizacao.Oferta? = null
		var numer = 0
		for(i in 0 until assets.length())
		{
			val a = assets.getJSONObject(i)
			val m = ASSET.matchEntire(a.optString("name")) ?: continue
			val n = m.groupValues[2].toInt()
			if(n <= numer)
				continue
			oferta = Atualizacao.Oferta(m.groupValues[1], a.optString("browser_download_url"))
			numer = n
		}
		if(oferta == null)
			return null

		val sha = COMMIT.find(release.optString("body"))?.groupValues?.get(1)
		val opis = try { opisBuilda(numer, sha) }
		catch(e: Exception)
		{
			Trace.log(context, "QS8 update: no build details (${e.javaClass.simpleName}: ${e.message})")
			"Build dev.$numer" + (sha?.let { ", commit ${it.take(7)}" } ?: "")
		}
		Qs8Prefs(context).zapiszOpisAktualizacji(oferta.versao, opis)
		Trace.log(context, "QS8 update: dev release is ${oferta.versao} — $opis")
		return oferta
	}

	/** Tekst karty aktualizacji w launcherze. */
	fun tekstKarty(context: Context, wersja: String, zainstalowana: String): String
	{
		val opis = Qs8Prefs(context).opisAktualizacji(wersja) ?: "Build $wersja"
		return "${Marka.NAZWA} $wersja is available. You have $zainstalowana.\n" +
				"$opis\nUpdate downloads it and opens the installer."
	}

	/** „Build dev.7 from qs8/xyz (PR #5): tytuł commita (commit abc1234)”. */
	private fun opisBuilda(numer: Int, sha: String?): String
	{
		if(sha == null)
			return "Build dev.$numer"
		val tekst = pobierz("$API/actions/runs?head_sha=$sha&per_page=20")
				?: return "Build dev.$numer, commit ${sha.take(7)}"
		val runs = JSONObject(tekst).optJSONArray("workflow_runs")
		var run: JSONObject? = null
		for(i in 0 until (runs?.length() ?: 0))
		{
			val r = runs!!.getJSONObject(i)
			if(run == null || r.optInt("run_number") == numer)
				run = r
		}
		if(run == null)
			return "Build dev.$numer, commit ${sha.take(7)}"

		val galaz = run.optString("head_branch").ifEmpty { "?" }
		val prs = run.optJSONArray("pull_requests")
		val pr = if(prs != null && prs.length() > 0)
			" (PR #" + (0 until prs.length()).joinToString(", #") { prs.getJSONObject(it).optInt("number").toString() } + ")"
		else ""
		val tytul = run.optJSONObject("head_commit")?.optString("message")?.lineSequence()?.firstOrNull()
				?.ifEmpty { null } ?: run.optString("display_title")
		return "Build dev.$numer from $galaz$pr: $tytul (commit ${sha.take(7)})"
	}

	/** Treść odpowiedzi API; null przy 404 (brak release „dev”). */
	private fun pobierz(adres: String): String?
	{
		val c = URL(adres).openConnection() as HttpURLConnection
		try
		{
			c.connectTimeout = 15_000
			c.readTimeout = 15_000
			c.setRequestProperty("Accept", "application/vnd.github+json")
			c.setRequestProperty("User-Agent", "QuestStation8-Quest")
			val kod = c.responseCode
			if(kod == 404)
				return null
			if(kod != 200)
				throw java.io.IOException("GitHub answered HTTP $kod")
			return c.inputStream.bufferedReader().use { it.readText() }
		}
		finally { c.disconnect() }
	}
}
