// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: tryb "na lezaco". Ekran staje tam, gdzie patrzy glowa -- takze na suficie.
//
// Geometria ekranu P5M zostaje nietknieta: w tym trybie warstwy (obraz, 3D,
// poswiata, panel) i poza glowy dla dzwieku sa liczone w osobnej przestrzeni,
// LOCAL przesunietej i obroconej do pozy glowy z chwili centrowania. W trybie
// normalnym LezacoPrzestrzen oddaje te sama przestrzen co w P5M.
//
// Wszystko oprocz trybu (atomowy, ustawia go Kotlin) zyje w watku petli klatek.
#pragma once

#include <openxr/openxr.h>
#include <cmath>

namespace qs8 {

// Wartosci jak w qs8/NaLezaco.kt.
enum TrybLezenia : int { kLezNormalny = 0, kLezPlecy = 1, kLezBok = 2 };

// Raz na klatke, po xrBeginFrame i przed xrLocateViews. yaw_p5m = yaw
// recentrowania P5M (przestrzen go znosi, wiec recenter_orientation_ czeka
// nietkniety na powrot). true = po wyjsciu z trybu trzeba zwyklego centrowania,
// bo system przestawil LOCAL i dawny yaw nic juz nie znaczy.
bool LezacoKlatka(XrSession session, XrSpace view, XrSpace local, XrTime czas, float yaw_p5m);

// Przestrzen warstw: LOCAL P5M albo (tryb lezacy, po pierwszym centrowaniu) jego.
XrSpace LezacoPrzestrzen(XrSpace local);

// Zadanie centrowania (A / krzyzyk). true = tryb lezacy je przejal.
bool LezacoCentruj();

// Zdarzenie REFERENCE_SPACE_CHANGE_PENDING dla LOCAL (przytrzymanie Meta): od
// chwili od ekran ustawia sie na nowo wedlug glowy.
void LezacoZmianaPrzestrzeni(XrTime od);

// Przed xrDestroySession. Nowa sesja zaczyna w trybie normalnym.
void LezacoZniszcz();

// ------------------------------------------------ matematyka (tez do testow)

inline XrVector3f LezObroc(const XrQuaternionf &q, const XrVector3f &v)
{
	const XrVector3f t{2.0f * (q.y * v.z - q.z * v.y), 2.0f * (q.z * v.x - q.x * v.z),
			2.0f * (q.x * v.y - q.y * v.x)};
	return XrVector3f{v.x + q.w * t.x + (q.y * t.z - q.z * t.y),
			v.y + q.w * t.y + (q.z * t.x - q.x * t.z),
			v.z + q.w * t.z + (q.x * t.y - q.y * t.x)};
}

inline XrQuaternionf LezMnoz(const XrQuaternionf &a, const XrQuaternionf &b)
{
	return XrQuaternionf{
			a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
			a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
			a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
			a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z};
}

// Orientacja ekranu z orientacji glowy. Srodek ekranu zawsze w osi wzroku; rozni
// sie tylko przechylenie na bok (roll).
//
// Bok: pelna orientacja glowy.
// Plecy: poki wzrok jest blizej horyzontu niz sufitu (do 45 stopni), ekran stoi
// rowno wzgledem pokoju. Wyzej (i nizej niz -45) przechylenia wzgledem pokoju
// nie da sie sensownie policzyc -- patrzac w zenit nie ma go wcale, a tuz obok
// zmienia sie o dowolny kat -- wiec gora ekranu to gora glowy. Tak samo, gdy
// wyrownanie do pokoju postawiloby ekran do gory nogami wzgledem patrzacego.
inline XrQuaternionf LezOrientacjaEkranu(XrQuaternionf glowa, bool bok)
{
	const float n = std::sqrt(glowa.x * glowa.x + glowa.y * glowa.y + glowa.z * glowa.z + glowa.w * glowa.w);
	if(!(n > 1e-3f))
		return XrQuaternionf{0.0f, 0.0f, 0.0f, 1.0f};
	glowa = XrQuaternionf{glowa.x / n, glowa.y / n, glowa.z / n, glowa.w / n};
	if(bok)
		return glowa;
	const XrVector3f f = LezObroc(glowa, XrVector3f{0.0f, 0.0f, -1.0f});
	if(std::fabs(f.y) >= 0.70710678f)
		return glowa;
	// Prawo = f x Y i gora = prawo x f: ekran bez przechylenia.
	const float d = std::sqrt(f.x * f.x + f.z * f.z);
	const XrVector3f r{-f.z / d, 0.0f, f.x / d};
	const XrVector3f g{r.y * f.z - r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y - r.y * f.x};
	const XrVector3f u = LezObroc(glowa, XrVector3f{0.0f, 1.0f, 0.0f});
	const float c = u.x * g.x + u.y * g.y + u.z * g.z;
	if(c < -0.5f)
		return glowa;
	const float roll = std::atan2(-(u.x * r.x + u.y * r.y + u.z * r.z), c);
	return LezMnoz(glowa, XrQuaternionf{0.0f, 0.0f, std::sin(-roll * 0.5f), std::cos(-roll * 0.5f)});
}

// Poza przestrzeni warstw w LOCAL: poza ekranu z cofnietym yaw P5M.
inline XrPosef LezPozaPrzestrzeni(const XrPosef &ekran, float yaw_p5m)
{
	XrPosef p = ekran;
	p.orientation = LezMnoz(ekran.orientation,
			XrQuaternionf{0.0f, std::sin(-yaw_p5m * 0.5f), 0.0f, std::cos(-yaw_p5m * 0.5f)});
	return p;
}

}  // namespace qs8
