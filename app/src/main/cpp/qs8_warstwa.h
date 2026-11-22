// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: filtry kompozytora dla warstwy wideo (XrCompositionLayerSettingsFB).
// Tylko naglowek (inline), zeby nie dokladac zrodla do CMakeLists.
#pragma once

#include <openxr/openxr.h>
#include <string>
#include "log.h"

namespace qs8 {

// Zapisuje do dziennika, jakie bity filtrow dostala warstwa wideo -- raz na
// zmiane, nie co klatke. Linia zawiera "XrCompositionLayerSettingsFB", wiec
// trafia do podsumowania w raporcie diagnostycznym. Tylko watek petli klatek.
inline void LogujBityWarstwy(XrCompositionLayerSettingsFlagsFB bity, const char *powod)
{
	static XrCompositionLayerSettingsFlagsFB ostatnie = ~XrCompositionLayerSettingsFlagsFB(0);
	if(bity == ostatnie)
		return;
	ostatnie = bity;
	std::string nazwy;
	auto dodaj = [&](XrCompositionLayerSettingsFlagsFB bit, const char *nazwa) {
		if(bity & bit)
			nazwy += nazwy.empty() ? nazwa : std::string(" + ") + nazwa;
	};
	dodaj(XR_COMPOSITION_LAYER_SETTINGS_AUTO_LAYER_FILTER_BIT_META, "AUTO_LAYER_FILTER");
	dodaj(XR_COMPOSITION_LAYER_SETTINGS_NORMAL_SUPER_SAMPLING_BIT_FB, "NORMAL_SUPER_SAMPLING");
	dodaj(XR_COMPOSITION_LAYER_SETTINGS_QUALITY_SUPER_SAMPLING_BIT_FB, "QUALITY_SUPER_SAMPLING");
	dodaj(XR_COMPOSITION_LAYER_SETTINGS_NORMAL_SHARPENING_BIT_FB, "NORMAL_SHARPENING");
	dodaj(XR_COMPOSITION_LAYER_SETTINGS_QUALITY_SHARPENING_BIT_FB, "QUALITY_SHARPENING (MQSR)");
	LOGI("QS8: video layer XrCompositionLayerSettingsFB = 0x%llx (%s), %s",
			(unsigned long long)bity, nazwy.empty() ? "no filter" : nazwy.c_str(), powod);
}

// MQSR (eksperymentalne, qs8_mqsr.cpp): przy wlaczonym przelaczniku i filtrze
// automatycznym zestaw kandydatow P5M zastepuje sam QUALITY_SHARPENING (MQSR),
// jak w dev.21. Bez bitu AUTO (inne stopnie ostrosci, brak rozszerzenia,
// sciezka shader bez filtra kompozytora) bity zostaja bez zmian.
XrCompositionLayerSettingsFlagsFB FiltrMqsr(XrCompositionLayerSettingsFlagsFB bity);

}  // namespace qs8
