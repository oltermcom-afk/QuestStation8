// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: poswiata wokol ekranu (jak Ambilight), kolejka 2026-11-22 pkt 5.
//
// Tylko sciezka shader: kolory krawedzi bierzemy z obrazu, ktory tone mapper
// wlasnie narysowal (swapchain wideo ma SAMPLED_BIT). Wszystko na watku petli
// klatek, z kontekstem GL tego watku.
#pragma once

#ifndef XR_USE_PLATFORM_ANDROID
#define XR_USE_PLATFORM_ANDROID
#endif
#ifndef XR_USE_GRAPHICS_API_OPENGL_ES
#define XR_USE_GRAPHICS_API_OPENGL_ES
#endif
#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>
#include <cstddef>
#include <vector>

namespace qs8 {

// Po narysowaniu klatki przez tone mapper (przed oddaniem obrazu swapchaina):
// usrednia strefy przy krawedziach i wygladza je w czasie. Nic nie robi przy
// wylaczonej poswiacie.
void PoswiataAktualizuj(GLuint obraz, int szerokosc, int wysokosc);

// Po dodaniu warstw ekranu: wstawia warstwe poswiaty na pozycje [przed] (pod
// ekran), z geometria wzieta z warstwy ekranu, ktora tam lezy. Tylko w ciemnym
// pokoju i gdy sa swieze dane z PoswiataAktualizuj.
void PoswiataWstaw(std::vector<const XrCompositionLayerBaseHeader *> &warstwy, size_t przed,
		XrSession sesja, bool ciemny_pokoj);

// Koniec sesji (z kontekstem GL): swapchain i obiekty GL.
void PoswiataZniszcz();

}  // namespace qs8
