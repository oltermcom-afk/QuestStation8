// SPDX-License-Identifier: AGPL-3.0-only
#include "qs8_hdr.h"
#include "log.h"

#include <jni.h>
#include <atomic>

namespace qs8 {
namespace {
std::atomic<float> g_pik{0.0f};
}

float PikHdr(float domyslny)
{
	const float p = g_pik.load(std::memory_order_relaxed);
	return p > 0.0f ? p : domyslny;
}

}  // namespace qs8

// Czytane przez watek renderu przy kazdym rysowaniu, wiec dziala w trakcie gry.
extern "C" JNIEXPORT void JNICALL
Java_io_github_gblandro_p5m_qs8_HdrNative_nativeSetPeak(JNIEnv *, jobject, jfloat nity)
{
	qs8::g_pik.store(nity, std::memory_order_relaxed);
	LOGI("QS8: HDR peak for the tone curve %.0f nits", (double)nity);
}
