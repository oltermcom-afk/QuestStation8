// SPDX-License-Identifier: AGPL-3.0-only
#include "qs8_klatki.h"
#include "log.h"

#include <jni.h>
#include <atomic>
#include <algorithm>
#include <cmath>
#include <iterator>

namespace qs8 {
namespace {
std::atomic<bool> g_do60{false};
constexpr double kOkres60Ns = 1e9 / 60.0;

// Ponizsze tylko z watku renderu (jedno rysowanie na odswiezenie ekranu).

// Ponizej tylko licznik do dziennika: nie zmienia obrazu. Rytm "do 60" to
// obciecie czasu do 1/60 s z dev.23. Proba z dev.25 (pelne odswiezenia z
// zaokragleniem i wlasnym pomiarem odswiezania) dala na Quescie wiecej
// artefaktow i chrupniec przy szybkich obrotach (120 i 180 Hz, 3D) i zostala
// cofnieta; licznik ma pokazac, skad bierze sie rzadkie szarpniecie z dev.23.

// Okres odswiezania z odstepow miedzy rysowaniami. Odstep wyraznie dluzszy =
// odswiezenie, w ktorym aplikacja nie zdazyla (liczone osobno); 30 takich pod
// rzad = zmiana czestotliwosci panelu, pomiar od nowa.
int64_t g_poprzednie_ns = 0;
double g_odswiezanie_ns = 0.0;
int g_odrzucone = 0;
int g_spoznione = 0;

void MierzOdswiezanie(int64_t teraz_ns)
{
	const int64_t d = g_poprzednie_ns > 0 ? teraz_ns - g_poprzednie_ns : 0;
	g_poprzednie_ns = teraz_ns;
	if(d < 2500000 || d > 100000000)
		return;
	if(g_odswiezanie_ns <= 0.0 || g_odrzucone >= 30)
	{
		g_odswiezanie_ns = (double)std::min<int64_t>(d, 20000000);
		g_odrzucone = 0;
		return;
	}
	if(d > g_odswiezanie_ns * 0.7 && d < g_odswiezanie_ns * 1.4)
	{
		g_odswiezanie_ns += ((double)d - g_odswiezanie_ns) / 16.0;
		g_odrzucone = 0;
	}
	else
	{
		g_odrzucone++;
		if(d >= g_odswiezanie_ns * 1.4)
			g_spoznione++;
	}
}

// Co 10 s (tylko przy "do 60"): ile rysowan trwal kazdy obraz (rowno = 1/60 s
// w odswiezeniach), co ile rysowan przychodzila prawdziwa klatka i ile razy
// aplikacja nie zdazyla na odswiezenie.
long g_poprzedni_krok = -1;
float g_poprzednia_skala = -1.0f;
int g_trwa = 0;
int g_od_prawdziwej = 0;
int g_obrazy_rowne = 0, g_obrazy_krotsze = 0, g_obrazy_dluzsze = 0;
int g_odstepy[8] = {};  // odstep prawdziwych klatek w rysowaniach: 1..6, 7 = wiecej
int64_t g_okno_ns = 0;

void Zeruj()
{
	g_poprzedni_krok = -1;
	g_poprzednia_skala = -1.0f;
	g_trwa = g_od_prawdziwej = 0;
	g_okno_ns = 0;
}

void Licz(float skala, long krok, int64_t teraz_ns)
{
	const long na_obraz = g_odswiezanie_ns > 0.0
			? std::max(1L, std::lround(kOkres60Ns / g_odswiezanie_ns)) : 2;
	const bool nowa_prawdziwa = g_poprzednia_skala >= 0.0f && skala < g_poprzednia_skala;
	if((nowa_prawdziwa || krok != g_poprzedni_krok) && g_poprzedni_krok >= 0)
	{
		if(g_trwa == na_obraz) g_obrazy_rowne++;
		else if(g_trwa < na_obraz) g_obrazy_krotsze++;
		else g_obrazy_dluzsze++;
		g_trwa = 0;
	}
	if(nowa_prawdziwa)
	{
		g_odstepy[std::min(std::max(g_od_prawdziwej, 1), 7)]++;
		g_od_prawdziwej = 0;
	}
	g_trwa++;
	g_od_prawdziwej++;
	g_poprzedni_krok = krok;
	g_poprzednia_skala = skala;

	if(g_okno_ns == 0)
	{
		g_okno_ns = teraz_ns;
		g_spoznione = 0;
	}
	if(teraz_ns - g_okno_ns < 10000000000LL)
		return;
	g_okno_ns = teraz_ns;
	LOGI("QS8: frames rhythm (up to 60, panel %.0f Hz, %ld refreshes per image): images even %d, shorter %d, "
			"longer %d; real frame every 1:%d 2:%d 3:%d 4:%d 5:%d 6:%d 7+:%d draws; late draws %d",
			g_odswiezanie_ns > 0.0 ? 1e9 / g_odswiezanie_ns : 0.0, na_obraz,
			g_obrazy_rowne, g_obrazy_krotsze, g_obrazy_dluzsze,
			g_odstepy[1], g_odstepy[2], g_odstepy[3], g_odstepy[4], g_odstepy[5], g_odstepy[6], g_odstepy[7],
			g_spoznione);
	g_obrazy_rowne = g_obrazy_krotsze = g_obrazy_dluzsze = 0;
	g_spoznione = 0;
	std::fill(std::begin(g_odstepy), std::end(g_odstepy), 0);
}
}  // namespace

float SkalaPrzewidywania(float skala, int64_t okres_zrodla_ns, int64_t teraz_ns)
{
	MierzOdswiezanie(teraz_ns);
	if(!g_do60.load(std::memory_order_relaxed) || okres_zrodla_ns <= 0 || skala < 0.0f)
	{
		Zeruj();
		return skala;
	}
	// Jak w dev.23: krok w jednostkach okresu zrodla (0,5 przy 30 fps, ~0,67
	// przy 40 fps, 1 przy 60 fps), skala obcieta w dol do jego wielokrotnosci.
	const double krok = std::min(1.0, kOkres60Ns / (double)okres_zrodla_ns);
	const long ile = (long)std::floor((double)skala / krok + 1e-4);
	Licz(skala, ile, teraz_ns);
	return (float)((double)ile * krok);
}

}  // namespace qs8

// Czytane przez watek renderu przy kazdym rysowaniu, wiec zmiana dziala od razu.
extern "C" JNIEXPORT void JNICALL
Java_io_github_gblandro_p5m_qs8_KlatkiNative_nativeSetLimit(JNIEnv *, jobject, jboolean do60)
{
	qs8::g_do60.store(do60 == JNI_TRUE, std::memory_order_relaxed);
	LOGI("QS8: generated frames limit %s", do60 == JNI_TRUE ? "up to 60 fps" : "up to 120 fps (P5M)");
}
