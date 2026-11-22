// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: MQSR (eksperymentalne). Ostrosc "Automatic" P5M daje kompozytorowi do
// wyboru zwykle i MQSR wyostrzanie oraz supersampling. Ten przelacznik zostawia
// mu tylko MQSR (QUALITY_SHARPENING) -- wariant z dev.21, ktory w tescie
// z wlaczona glebia neuronowa gubil 0-4 klatek. Do sprawdzenia bez niej.
// Bity warstwy ustawiane sa co klatke, wiec przelacznik dziala na zywo.
#include "qs8_warstwa.h"

#include <jni.h>
#include <atomic>

namespace qs8 {
namespace {
std::atomic<bool> g_mqsr{false};
}

XrCompositionLayerSettingsFlagsFB FiltrMqsr(XrCompositionLayerSettingsFlagsFB bity)
{
	if(!g_mqsr.load(std::memory_order_relaxed) || !(bity & XR_COMPOSITION_LAYER_SETTINGS_AUTO_LAYER_FILTER_BIT_META))
		return bity;
	return XR_COMPOSITION_LAYER_SETTINGS_AUTO_LAYER_FILTER_BIT_META
			| XR_COMPOSITION_LAYER_SETTINGS_QUALITY_SHARPENING_BIT_FB;
}

}  // namespace qs8

extern "C" JNIEXPORT void JNICALL
Java_io_github_gblandro_p5m_qs8_MqsrNative_nativeSetMqsr(JNIEnv *, jobject, jboolean on)
{
	qs8::g_mqsr.store(on == JNI_TRUE, std::memory_order_relaxed);
	LOGI("QS8: MQSR (experimental) %s", on == JNI_TRUE ? "on: Automatic = MQSR only" : "off: Automatic as in P5M");
}
