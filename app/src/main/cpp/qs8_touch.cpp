// SPDX-License-Identifier: AGPL-3.0-only
#include "qs8_touch.h"
#include "log.h"

#include <jni.h>
#include <atomic>
#include <cmath>
#include <cstring>

namespace qs8 {
namespace {

// Bool: kliki. Float: gripy i triggery (z histereza). Vector2f: thumbsticki.
struct Akcje
{
	XrActionSet set = XR_NULL_HANDLE;
	XrAction menu = XR_NULL_HANDLE;
	XrAction a = XR_NULL_HANDLE, b = XR_NULL_HANDLE, x = XR_NULL_HANDLE, y = XR_NULL_HANDLE;
	XrAction gripL = XR_NULL_HANDLE, gripR = XR_NULL_HANDLE;
	XrAction trigL = XR_NULL_HANDLE, trigR = XR_NULL_HANDLE;
	XrAction stickL = XR_NULL_HANDLE, stickR = XR_NULL_HANDLE;
	XrAction clickL = XR_NULL_HANDLE, clickR = XR_NULL_HANDLE;
};

Akcje g_akcje;
XrInstance g_instance = XR_NULL_HANDLE;
bool g_gotowe = false;
std::atomic<int> g_stan{0};

// Histereza: wcisniecie powyzej progu, puszczenie ponizej nizszego, zeby
// wartosc drgajaca kolo progu nie dawala serii klikniec.
constexpr float kWcisniety = 0.70f;
constexpr float kPuszczony = 0.40f;

bool Utworz(XrActionType typ, const char *nazwa, const char *opis, XrAction *out)
{
	XrActionCreateInfo info{XR_TYPE_ACTION_CREATE_INFO};
	info.actionType = typ;
	std::strncpy(info.actionName, nazwa, XR_MAX_ACTION_NAME_SIZE - 1);
	std::strncpy(info.localizedActionName, opis, XR_MAX_LOCALIZED_ACTION_NAME_SIZE - 1);
	XrResult res = xrCreateAction(g_akcje.set, &info, out);
	if(XR_FAILED(res))
	{
		LOGW("QS8 touch: xrCreateAction(%s) failed: %d", nazwa, (int)res);
		return false;
	}
	return true;
}

XrPath Sciezka(const char *tekst)
{
	XrPath p = XR_NULL_PATH;
	xrStringToPath(g_instance, tekst, &p);
	return p;
}

bool Przycisk(XrSession session, XrAction akcja)
{
	XrActionStateGetInfo info{XR_TYPE_ACTION_STATE_GET_INFO};
	info.action = akcja;
	XrActionStateBoolean st{XR_TYPE_ACTION_STATE_BOOLEAN};
	if(XR_FAILED(xrGetActionStateBoolean(session, &info, &st)) || !st.isActive)
		return false;
	return st.currentState == XR_TRUE;
}

bool Os(XrSession session, XrAction akcja, bool bylWcisniety)
{
	XrActionStateGetInfo info{XR_TYPE_ACTION_STATE_GET_INFO};
	info.action = akcja;
	XrActionStateFloat st{XR_TYPE_ACTION_STATE_FLOAT};
	if(XR_FAILED(xrGetActionStateFloat(session, &info, &st)) || !st.isActive)
		return false;
	return st.currentState > (bylWcisniety ? kPuszczony : kWcisniety);
}

// Kierunek thumbsticka jako bity D-pada: dominujaca os, z histereza.
int Galka(XrSession session, XrAction akcja, int poprzednie)
{
	XrActionStateGetInfo info{XR_TYPE_ACTION_STATE_GET_INFO};
	info.action = akcja;
	XrActionStateVector2f st{XR_TYPE_ACTION_STATE_VECTOR2F};
	if(XR_FAILED(xrGetActionStateVector2f(session, &info, &st)) || !st.isActive)
		return 0;
	const float x = st.currentState.x;
	const float y = st.currentState.y;  // OpenXR: y w gore dodatnie
	const float prog = poprzednie ? kPuszczony : kWcisniety;
	if(std::fabs(x) < prog && std::fabs(y) < prog)
		return 0;
	if(std::fabs(x) > std::fabs(y))
		return x > 0 ? kStickRight : kStickLeft;
	return y > 0 ? kStickUp : kStickDown;
}

// Stan poprzedniej klatki, tylko dla histerezy (watek petli klatek).
int g_galkaL = 0, g_galkaR = 0;

}  // namespace

bool TouchCreate(XrInstance instance, XrSession session)
{
	TouchDestroy();
	g_instance = instance;

	XrActionSetCreateInfo set_info{XR_TYPE_ACTION_SET_CREATE_INFO};
	std::strncpy(set_info.actionSetName, "qs8_panel", XR_MAX_ACTION_SET_NAME_SIZE - 1);
	std::strncpy(set_info.localizedActionSetName, "QuestStation8 panel",
			XR_MAX_LOCALIZED_ACTION_SET_NAME_SIZE - 1);
	set_info.priority = 0;
	XrResult res = xrCreateActionSet(instance, &set_info, &g_akcje.set);
	if(XR_FAILED(res))
	{
		LOGW("QS8 touch: xrCreateActionSet failed: %d; Quest controllers stay inactive", (int)res);
		g_akcje = Akcje{};
		return false;
	}

	bool ok = Utworz(XR_ACTION_TYPE_BOOLEAN_INPUT, "menu", "Panel", &g_akcje.menu)
			&& Utworz(XR_ACTION_TYPE_BOOLEAN_INPUT, "a", "A", &g_akcje.a)
			&& Utworz(XR_ACTION_TYPE_BOOLEAN_INPUT, "b", "B", &g_akcje.b)
			&& Utworz(XR_ACTION_TYPE_BOOLEAN_INPUT, "x", "X", &g_akcje.x)
			&& Utworz(XR_ACTION_TYPE_BOOLEAN_INPUT, "y", "Y", &g_akcje.y)
			&& Utworz(XR_ACTION_TYPE_FLOAT_INPUT, "grip_left", "Left grip", &g_akcje.gripL)
			&& Utworz(XR_ACTION_TYPE_FLOAT_INPUT, "grip_right", "Right grip", &g_akcje.gripR)
			&& Utworz(XR_ACTION_TYPE_FLOAT_INPUT, "trigger_left", "Left trigger", &g_akcje.trigL)
			&& Utworz(XR_ACTION_TYPE_FLOAT_INPUT, "trigger_right", "Right trigger", &g_akcje.trigR)
			&& Utworz(XR_ACTION_TYPE_VECTOR2F_INPUT, "stick_left", "Left thumbstick", &g_akcje.stickL)
			&& Utworz(XR_ACTION_TYPE_VECTOR2F_INPUT, "stick_right", "Right thumbstick", &g_akcje.stickR)
			&& Utworz(XR_ACTION_TYPE_BOOLEAN_INPUT, "stick_click_left", "Left thumbstick click", &g_akcje.clickL)
			&& Utworz(XR_ACTION_TYPE_BOOLEAN_INPUT, "stick_click_right", "Right thumbstick click", &g_akcje.clickR);
	if(!ok)
	{
		TouchDestroy();
		return false;
	}

	// Przycisk Meta (prawy) jest zarezerwowany przez system i nie ma sciezki.
	const XrActionSuggestedBinding wiazania[] = {
		{g_akcje.menu, Sciezka("/user/hand/left/input/menu/click")},
		{g_akcje.a, Sciezka("/user/hand/right/input/a/click")},
		{g_akcje.b, Sciezka("/user/hand/right/input/b/click")},
		{g_akcje.x, Sciezka("/user/hand/left/input/x/click")},
		{g_akcje.y, Sciezka("/user/hand/left/input/y/click")},
		{g_akcje.gripL, Sciezka("/user/hand/left/input/squeeze/value")},
		{g_akcje.gripR, Sciezka("/user/hand/right/input/squeeze/value")},
		{g_akcje.trigL, Sciezka("/user/hand/left/input/trigger/value")},
		{g_akcje.trigR, Sciezka("/user/hand/right/input/trigger/value")},
		{g_akcje.stickL, Sciezka("/user/hand/left/input/thumbstick")},
		{g_akcje.stickR, Sciezka("/user/hand/right/input/thumbstick")},
		{g_akcje.clickL, Sciezka("/user/hand/left/input/thumbstick/click")},
		{g_akcje.clickR, Sciezka("/user/hand/right/input/thumbstick/click")},
	};
	XrInteractionProfileSuggestedBinding sugestia{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
	sugestia.interactionProfile = Sciezka("/interaction_profiles/oculus/touch_controller");
	sugestia.suggestedBindings = wiazania;
	sugestia.countSuggestedBindings = sizeof(wiazania) / sizeof(wiazania[0]);
	res = xrSuggestInteractionProfileBindings(instance, &sugestia);
	if(XR_FAILED(res))
	{
		LOGW("QS8 touch: xrSuggestInteractionProfileBindings failed: %d", (int)res);
		TouchDestroy();
		return false;
	}

	XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
	attach.countActionSets = 1;
	attach.actionSets = &g_akcje.set;
	res = xrAttachSessionActionSets(session, &attach);
	if(XR_FAILED(res))
	{
		LOGW("QS8 touch: xrAttachSessionActionSets failed: %d", (int)res);
		TouchDestroy();
		return false;
	}

	g_gotowe = true;
	LOGI("QS8 touch: Quest controller actions ready (they only reach the settings panel)");
	return true;
}

void TouchSync(XrSession session)
{
	if(!g_gotowe)
		return;
	XrActiveActionSet aktywny{g_akcje.set, XR_NULL_PATH};
	XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO};
	sync.countActiveActionSets = 1;
	sync.activeActionSets = &aktywny;
	// XR_SESSION_NOT_FOCUSED to sukces: akcje sa wtedy nieaktywne i stan
	// wychodzi zerowy, czyli nic nie jest "wcisniete" pod menu systemowym.
	if(XR_FAILED(xrSyncActions(session, &sync)))
	{
		g_stan.store(0);
		return;
	}

	const int poprzedni = g_stan.load();
	int stan = 0;
	if(Przycisk(session, g_akcje.menu)) stan |= kMenu;
	if(Przycisk(session, g_akcje.a)) stan |= kA;
	if(Przycisk(session, g_akcje.b)) stan |= kB;
	if(Przycisk(session, g_akcje.x)) stan |= kX;
	if(Przycisk(session, g_akcje.y)) stan |= kY;
	if(Przycisk(session, g_akcje.clickL)) stan |= kStickClickLeft;
	if(Przycisk(session, g_akcje.clickR)) stan |= kStickClickRight;
	if(Os(session, g_akcje.gripL, poprzedni & kGripLeft)) stan |= kGripLeft;
	if(Os(session, g_akcje.gripR, poprzedni & kGripRight)) stan |= kGripRight;
	if(Os(session, g_akcje.trigL, poprzedni & kTriggerLeft)) stan |= kTriggerLeft;
	if(Os(session, g_akcje.trigR, poprzedni & kTriggerRight)) stan |= kTriggerRight;
	g_galkaL = Galka(session, g_akcje.stickL, g_galkaL);
	g_galkaR = Galka(session, g_akcje.stickR, g_galkaR);
	stan |= g_galkaL | g_galkaR;
	g_stan.store(stan);
}

void TouchDestroy()
{
	g_gotowe = false;
	g_stan.store(0);
	g_galkaL = g_galkaR = 0;
	// Akcje gina razem z action setem.
	if(g_akcje.set != XR_NULL_HANDLE)
		xrDestroyActionSet(g_akcje.set);
	g_akcje = Akcje{};
}

int TouchState()
{
	return g_stan.load();
}

}  // namespace qs8

extern "C" JNIEXPORT jint JNICALL
Java_io_github_gblandro_p5m_qs8_TouchNative_nativeReadTouchState(JNIEnv *, jobject)
{
	return qs8::TouchState();
}
