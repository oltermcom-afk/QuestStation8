// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: tryb "na lezaco" (opis w qs8_lezaco.h).
#include "qs8_lezaco.h"
#include "log.h"

#include <jni.h>
#include <atomic>

namespace qs8 {
namespace {

std::atomic<int> g_tryb{kLezNormalny};

// Tylko watek petli klatek (Destroy wola po jego zakonczeniu).
struct Stan
{
	int aktywny = kLezNormalny;
	bool centruj = false;
	bool local_zmieniony = false;  // system przestawil LOCAL w trakcie trybu
	XrTime od = 0;                 // najwczesniejsza chwila centrowania
	XrPosef ekran{{0.0f, 0.0f, 0.0f, 1.0f}, {0.0f, 0.0f, 0.0f}};
	float yaw = 0.0f;              // yaw P5M, ktory znosi obecna przestrzen
	XrSpace przestrzen = XR_NULL_HANDLE;
};
Stan s;

void Usun()
{
	// Specyfikacja pozwala zniszczyc przestrzen uzyta w poprzedniej klatce.
	if(s.przestrzen != XR_NULL_HANDLE)
		xrDestroySpace(s.przestrzen);
	s.przestrzen = XR_NULL_HANDLE;
}

bool Zbuduj(XrSession session, float yaw_p5m)
{
	// Zapamietany takze po bledzie, zeby nie ponawiac co klatke.
	s.yaw = yaw_p5m;
	XrReferenceSpaceCreateInfo info{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
	info.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
	info.poseInReferenceSpace = LezPozaPrzestrzeni(s.ekran, yaw_p5m);
	XrSpace nowa = XR_NULL_HANDLE;
	const XrResult res = xrCreateReferenceSpace(session, &info, &nowa);
	if(XR_FAILED(res))
	{
		LOGW("QS8: lying mode, xrCreateReferenceSpace failed (%d); the screen stays where it was", (int)res);
		return false;
	}
	Usun();
	s.przestrzen = nowa;
	return true;
}

}  // namespace

bool LezacoKlatka(XrSession session, XrSpace view, XrSpace local, XrTime czas, float yaw_p5m)
{
	const int tryb = g_tryb.load(std::memory_order_relaxed);
	if(tryb == kLezNormalny)
	{
		if(s.aktywny == kLezNormalny)
			return false;
		const bool centrowac = s.local_zmieniony;
		Usun();
		s = Stan{};
		LOGI("QS8: lying mode off, the screen is %s", centrowac ? "recentered (LOCAL moved meanwhile)" : "back where it was");
		return centrowac;
	}

	if(tryb != s.aktywny)
	{
		s.aktywny = tryb;
		s.centruj = true;
	}

	if(s.centruj && czas >= s.od)
	{
		// Bez wiarygodnej orientacji probujemy w nastepnej klatce.
		XrSpaceLocation loc{XR_TYPE_SPACE_LOCATION};
		if(XR_SUCCEEDED(xrLocateSpace(view, local, czas, &loc))
				&& (loc.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT))
		{
			const bool pozycja = (loc.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0;
			s.centruj = false;
			s.od = 0;
			s.ekran.orientation = LezOrientacjaEkranu(loc.pose.orientation, tryb == kLezBok);
			s.ekran.position = pozycja ? loc.pose.position : XrVector3f{0.0f, 0.0f, 0.0f};
			if(Zbuduj(session, yaw_p5m))
			{
				const XrVector3f f = LezObroc(loc.pose.orientation, XrVector3f{0.0f, 0.0f, -1.0f});
				const XrVector3f p = LezObroc(loc.pose.orientation, XrVector3f{1.0f, 0.0f, 0.0f});
				LOGI("QS8: lying mode (%s) centered: gaze %.0f deg up, head tilt %.0f deg, position %s",
						tryb == kLezBok ? "side" : "back",
						(double)(std::asin(std::fmax(-1.0f, std::fmin(1.0f, f.y))) * 57.29578f),
						(double)(std::asin(std::fmax(-1.0f, std::fmin(1.0f, p.y))) * 57.29578f),
						pozycja ? "tracked" : "unknown");
			}
		}
	}
	else if(s.przestrzen != XR_NULL_HANDLE && yaw_p5m != s.yaw)
		// Yaw P5M zmienil sie miedzy centrowaniami (zdarzenie systemowe przychodzi
		// przed sama zmiana LOCAL): ekran nie moze sie przez to obrocic.
		Zbuduj(session, yaw_p5m);
	return false;
}

XrSpace LezacoPrzestrzen(XrSpace local)
{
	return s.przestrzen != XR_NULL_HANDLE ? s.przestrzen : local;
}

bool LezacoCentruj()
{
	if(g_tryb.load(std::memory_order_relaxed) == kLezNormalny)
		return false;
	s.centruj = true;
	return true;
}

void LezacoZmianaPrzestrzeni(XrTime od)
{
	if(s.aktywny == kLezNormalny)
		return;
	s.centruj = true;
	s.od = od;
	s.local_zmieniony = true;
	LOGI("QS8: lying mode, system recenter: the screen will be placed by the head again");
}

void LezacoZniszcz()
{
	Usun();
	s = Stan{};
	g_tryb.store(kLezNormalny, std::memory_order_relaxed);
}

}  // namespace qs8

extern "C" JNIEXPORT void JNICALL
Java_io_github_gblandro_p5m_qs8_NaLezacoNative_nativeSetTryb(JNIEnv *, jobject, jint tryb)
{
	const int t = tryb == qs8::kLezPlecy || tryb == qs8::kLezBok ? (int)tryb : (int)qs8::kLezNormalny;
	qs8::g_tryb.store(t, std::memory_order_relaxed);
	LOGI("QS8: lying mode %s", t == qs8::kLezPlecy ? "on (back)" : t == qs8::kLezBok ? "on (side)" : "off");
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_gblandro_p5m_qs8_NaLezacoNative_nativeTryb(JNIEnv *, jobject)
{
	return qs8::g_tryb.load(std::memory_order_relaxed);
}
