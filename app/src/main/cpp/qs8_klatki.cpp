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

// Okres odswiezania ekranu mierzony z odstepow miedzy rysowaniami. Odstep
// dwa razy dluzszy (zgubiona klatka kompozytora) nie psuje sredniej; 30
// odrzuconych pod rzad = zmiana czestotliwosci panelu, pomiar od nowa.
int64_t g_poprzednie_ns = 0;
double g_odswiezanie_ns = 0.0;
int g_odrzucone = 0;

void MierzOdswiezanie(int64_t teraz_ns)
{
	const int64_t d = g_poprzednie_ns > 0 ? teraz_ns - g_poprzednie_ns : 0;
	g_poprzednie_ns = teraz_ns;
	if(d < 2500000 || d > 20000000)
		return;
	if(g_odswiezanie_ns <= 0.0 || g_odrzucone >= 30)
	{
		g_odswiezanie_ns = (double)d;
		g_odrzucone = 0;
		return;
	}
	if(d > g_odswiezanie_ns * 0.7 && d < g_odswiezanie_ns * 1.4)
	{
		g_odswiezanie_ns += ((double)d - g_odswiezanie_ns) / 16.0;
		g_odrzucone = 0;
	}
	else
		g_odrzucone++;
}

// Licznik rytmu do dziennika (co 10 s, tylko przy "do 60"): ile odswiezen
// wisial kazdy obraz i co ile odswiezen przychodzila prawdziwa klatka.
// Obraz krotszy lub dluzszy niz plan bierze sie tylko z prawdziwej klatki,
// ktora przyszla poza rytmem (gra lub siec), nie z zaokraglania.
long g_poprzednie_n = -1;
long g_poprzednie_k = -1;
int g_trwa = 0;
int g_obrazy_rowne = 0, g_obrazy_krotsze = 0, g_obrazy_dluzsze = 0;
int g_odstepy[8] = {};  // odstep prawdziwych klatek w odswiezeniach: 1..6, 7 = wiecej
int64_t g_okno_ns = 0;

void Licz(long n, long k, long na_obraz, int64_t teraz_ns)
{
	const bool nowa_prawdziwa = g_poprzednie_n >= 0 && n < g_poprzednie_n;
	const bool nowy_obraz = nowa_prawdziwa || k != g_poprzednie_k;
	if(nowy_obraz && g_poprzednie_k >= 0)
	{
		if(g_trwa == na_obraz) g_obrazy_rowne++;
		else if(g_trwa < na_obraz) g_obrazy_krotsze++;
		else g_obrazy_dluzsze++;
		g_trwa = 0;
	}
	if(nowa_prawdziwa)
		g_odstepy[std::min<long>(std::max<long>(g_poprzednie_n + 1, 1), 7)]++;
	g_trwa++;
	g_poprzednie_n = n;
	g_poprzednie_k = k;

	if(g_okno_ns == 0)
		g_okno_ns = teraz_ns;
	if(teraz_ns - g_okno_ns < 10000000000LL)
		return;
	g_okno_ns = teraz_ns;
	LOGI("QS8: frames rhythm (up to 60, panel %.0f Hz, %ld refreshes per image): images even %d, shorter %d, "
			"longer %d; real frame every 1:%d 2:%d 3:%d 4:%d 5:%d 6:%d 7+:%d refreshes",
			1e9 / g_odswiezanie_ns, na_obraz, g_obrazy_rowne, g_obrazy_krotsze, g_obrazy_dluzsze,
			g_odstepy[1], g_odstepy[2], g_odstepy[3], g_odstepy[4], g_odstepy[5], g_odstepy[6], g_odstepy[7]);
	g_obrazy_rowne = g_obrazy_krotsze = g_obrazy_dluzsze = 0;
	std::fill(std::begin(g_odstepy), std::end(g_odstepy), 0);
}
}  // namespace

float SkalaPrzewidywania(float skala, int64_t okres_zrodla_ns, int64_t teraz_ns)
{
	MierzOdswiezanie(teraz_ns);
	if(!g_do60.load(std::memory_order_relaxed))
	{
		g_poprzednie_n = g_poprzednie_k = -1;
		g_trwa = 0;
		g_okno_ns = 0;
		return skala;
	}
	if(okres_zrodla_ns <= 0 || skala < 0.0f || g_odswiezanie_ns <= 0.0)
		return skala;
	// Pelne odswiezenia od ostatniej prawdziwej klatki. Zaokraglenie, nie
	// obciecie: chwila rysowania i sredni okres zrodla drgaja o ulamek
	// milisekundy, a granica 1/60 s wypada dokladnie na odswiezeniu. Obciecie
	// dawalo wtedy rzadko rytm 3+1 zamiast 2+2 (szarpniecie, test dev.23).
	// Z zaokragleniem zapas to pol odswiezenia (4 ms przy 120 Hz).
	const double uplyw_ns = (double)skala * (double)okres_zrodla_ns;
	const long n = std::lround(uplyw_ns / g_odswiezanie_ns);
	// Odswiezen na obraz: 2 przy 120 Hz, 3 przy 180 Hz, 2 przy 90 Hz (tam 1/60 s
	// to 1,5 odswiezenia, rytmu rownego sie nie da), 1 przy 72 Hz i ponizej.
	const long na_obraz = std::max(1L, std::lround(kOkres60Ns / g_odswiezanie_ns));
	const long k = (n / na_obraz) * na_obraz;
	Licz(n, k, na_obraz, teraz_ns);
	return (float)((double)k * g_odswiezanie_ns / (double)okres_zrodla_ns);
}

}  // namespace qs8

// Czytane przez watek renderu przy kazdym rysowaniu, wiec zmiana dziala od razu.
extern "C" JNIEXPORT void JNICALL
Java_io_github_gblandro_p5m_qs8_KlatkiNative_nativeSetLimit(JNIEnv *, jobject, jboolean do60)
{
	qs8::g_do60.store(do60 == JNI_TRUE, std::memory_order_relaxed);
	LOGI("QS8: generated frames limit %s", do60 == JNI_TRUE ? "up to 60 fps" : "up to 120 fps (P5M)");
}
