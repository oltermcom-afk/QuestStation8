// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8 (plan 1B): kontrolery Touch Questa -> panel ustawien.
//
// Action set z profilem /interaction_profiles/oculus/touch_controller. Watek
// petli klatek wola TouchSync raz na klatke; stan przyciskow trafia do jednej
// atomowej maski bitowej, ktora Kotlin (qs8/TouchNative) odpytuje przez JNI.
// Zadnego wywolania OpenXR poza watkami sesji.
#pragma once

#include <openxr/openxr.h>

namespace qs8 {

// Bity maski zwracanej do Kotlina. Musza sie zgadzac z qs8/TouchMenu.kt.
enum TouchBit : int
{
	kMenu = 1 << 0,        // lewy przycisk menu (≡)
	kA = 1 << 1,
	kB = 1 << 2,
	kX = 1 << 3,
	kY = 1 << 4,
	kGripLeft = 1 << 5,
	kGripRight = 1 << 6,
	kTriggerLeft = 1 << 7,
	kTriggerRight = 1 << 8,
	kStickUp = 1 << 9,     // dowolny z dwoch thumbstickow
	kStickDown = 1 << 10,
	kStickLeft = 1 << 11,
	kStickRight = 1 << 12,
	kStickClickLeft = 1 << 13,   // klik lewego thumbsticka (jasnosc)
	kStickClickRight = 1 << 14,  // klik prawego thumbsticka (sila 3D)
};

// Po xrCreateSession, przed xrBeginSession. Blad nie jest fatalny: bez
// kontrolerow Touch panel dalej dziala z pada.
bool TouchCreate(XrInstance instance, XrSession session);

// Raz na klatke, w watku petli klatek. Bez fokusu stan jest zerowany.
void TouchSync(XrSession session);

// Przed xrDestroySession.
void TouchDestroy();

// Ostatnia maska (bezpieczne z dowolnego watku).
int TouchState();

}  // namespace qs8
