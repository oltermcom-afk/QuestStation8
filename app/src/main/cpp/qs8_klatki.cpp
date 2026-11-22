// SPDX-License-Identifier: AGPL-3.0-only
#include "qs8_klatki.h"
#include "log.h"

#include <jni.h>
#include <atomic>
#include <algorithm>
#include <cmath>

namespace qs8 {
namespace {
std::atomic<bool> g_do60{false};
constexpr double kOkres60Ns = 1e9 / 60.0;
}

float SkalaPrzewidywania(float skala, int64_t okres_zrodla_ns)
{
	if(!g_do60.load(std::memory_order_relaxed) || okres_zrodla_ns <= 0 || skala <= 0.0f)
		return skala;
	// Krok w jednostkach okresu zrodla: 0,5 przy 30 fps, ~0,67 przy 40 fps, 1 przy 60 fps.
	const double krok = std::min(1.0, kOkres60Ns / (double)okres_zrodla_ns);
	return (float)(std::floor((double)skala / krok + 1e-4) * krok);
}

}  // namespace qs8

// Czytane przez watek renderu przy kazdym rysowaniu, wiec zmiana dziala od razu.
extern "C" JNIEXPORT void JNICALL
Java_io_github_gblandro_p5m_qs8_KlatkiNative_nativeSetLimit(JNIEnv *, jobject, jboolean do60)
{
	qs8::g_do60.store(do60 == JNI_TRUE, std::memory_order_relaxed);
	LOGI("QS8: generated frames limit %s", do60 == JNI_TRUE ? "up to 60 fps" : "up to 120 fps (P5M)");
}
