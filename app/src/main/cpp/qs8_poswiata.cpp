// SPDX-License-Identifier: AGPL-3.0-only
//
// QS8: poswiata wokol ekranu. Trzy male przebiegi GL:
//   1. strefy: obraz gry (1080p) -> siatka 16x9 srednich kolorow (64 probki na
//      komorke). Skrajne kolumny i wiersze to "diody" przy krawedziach.
//   2. wygladzanie: siatka miesza sie z poprzednia (stala czasu ~0,25 s), zeby
//      poswiata nie migala przy cieciach i wybuchach.
//   3. poswiata: obraz 256x144 do wlasnego swapchaina; srodek to miejsce ekranu,
//      wokol kolor najblizszej strefy krawedzi, miekko zanikajacy na zewnatrz.
// Warstwa poswiaty jest wieksza od ekranu, ma jego ksztalt i poze, i wchodzi
// do kompozytora tuz POD nim (miedzy pokojem a ekranem). Obraz gry idzie jak
// dotad -- opoznienie gry sie nie zmienia.
#include "qs8_poswiata.h"
#include "log.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>

namespace qs8 {
namespace {

std::atomic<int> g_poziom{0};  // 0 wyl., 1 slaba, 2 srednia, 3 mocna
constexpr float kSila[4] = {0.0f, 0.40f, 0.65f, 0.95f};

constexpr int kStrefyX = 16;
constexpr int kStrefyY = 9;
constexpr int kSzer = 256;
constexpr int kWys = 144;
// O ile poswiata wychodzi poza ekran z kazdej strony, w wysokosciach ekranu.
constexpr float kMargines = 0.45f;
constexpr double kTauS = 0.25;
// Bez swiezych danych dluzej niz to (sciezka direct, pauza): bez warstwy.
constexpr double kSwiezeS = 0.5;

using Zegar = std::chrono::steady_clock;

struct Stan
{
	bool gl_gotowe = false;
	bool gl_blad = false;
	GLuint prog_strefy = 0, prog_wygladz = 0, prog_poswiata = 0;
	GLint u_strefy_obraz = -1, u_strefy_komorka = -1;
	GLint u_wyg_nowe = -1, u_wyg_stare = -1, u_wyg_a = -1;
	GLint u_posw_strefy = -1, u_posw_ekran = -1, u_posw_skala = -1, u_posw_sila = -1;
	GLuint vao = 0;
	GLuint fbo = 0;
	GLuint tex_strefy = 0;
	GLuint tex_wyg[2] = {0, 0};
	int wyg = 0;              // ktora z tex_wyg ma aktualny wynik
	bool ma_dane = false;
	Zegar::time_point ostatnia{};

	XrSession sesja = XR_NULL_HANDLE;
	XrSwapchain swapchain = XR_NULL_HANDLE;
	std::vector<XrSwapchainImageOpenGLESKHR> obrazy;
	bool swapchain_blad = false;

	XrCompositionLayerCylinderKHR cylinder{XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR};
	XrCompositionLayerQuad quad{XR_TYPE_COMPOSITION_LAYER_QUAD};
	int zalogowany_poziom = -1;
};
Stan s;

const char *kVs = R"(#version 300 es
void main()
{
	vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
	gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
)";

// Srednia komorki siatki: 8x8 probek rozlozonych po jej obszarze.
const char *kFsStrefy = R"(#version 300 es
precision highp float;
uniform sampler2D obraz;
uniform vec2 komorka;
out vec4 kolor;
void main()
{
	vec2 baza = floor(gl_FragCoord.xy) * komorka;
	vec3 suma = vec3(0.0);
	for(int y = 0; y < 8; y++)
		for(int x = 0; x < 8; x++)
			suma += texture(obraz, baza + (vec2(float(x), float(y)) + 0.5) / 8.0 * komorka).rgb;
	kolor = vec4(suma / 64.0, 1.0);
}
)";

const char *kFsWygladz = R"(#version 300 es
precision highp float;
uniform sampler2D nowe;
uniform sampler2D stare;
uniform float a;
out vec4 kolor;
void main()
{
	ivec2 p = ivec2(gl_FragCoord.xy);
	vec4 n = texelFetch(nowe, p, 0);
	// a = 1: pierwsza klatka; stara tekstura moze byc niezainicjowana (NaN * 0 = NaN).
	kolor = a >= 0.999 ? n : mix(texelFetch(stare, p, 0), n, a);
}
)";

// ekran: prostokat ekranu w uv poswiaty (x0, y0, x1, y1); skala: uv -> marginesy.
// Alfa pre-mnozona (tego oczekuje kompozytor przy BLEND_TEXTURE_SOURCE_ALPHA).
const char *kFsPoswiata = R"(#version 300 es
precision highp float;
uniform sampler2D strefy;
uniform vec4 ekran;
uniform vec2 skala;
uniform float sila;
out vec4 kolor;
void main()
{
	vec2 uv = gl_FragCoord.xy / vec2(256.0, 144.0);
	vec2 s = (uv - ekran.xy) / (ekran.zw - ekran.xy);
	vec2 poza = max(max(ekran.xy - uv, uv - ekran.zw), vec2(0.0)) * skala;
	float d = length(poza);
	vec2 k = vec2(1.0 / 16.0, 1.0 / 9.0);
	vec2 st = clamp(s, 0.0, 1.0) * (1.0 - k) + 0.5 * k;
	vec3 c = texture(strefy, st).rgb;
	float a = sila * exp(-2.4 * d) * (1.0 - smoothstep(0.7, 1.0, d));
	kolor = vec4(c * a, a);
}
)";

GLuint Shader(GLenum typ, const char *zrodlo)
{
	GLuint sh = glCreateShader(typ);
	glShaderSource(sh, 1, &zrodlo, nullptr);
	glCompileShader(sh);
	GLint ok = 0;
	glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
	if(!ok)
	{
		char log[512] = {};
		glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
		LOGW("QS8: glow shader did not compile: %s", log);
		glDeleteShader(sh);
		return 0;
	}
	return sh;
}

GLuint Program(const char *fs)
{
	GLuint v = Shader(GL_VERTEX_SHADER, kVs);
	GLuint f = Shader(GL_FRAGMENT_SHADER, fs);
	if(!v || !f)
	{
		if(v) glDeleteShader(v);
		if(f) glDeleteShader(f);
		return 0;
	}
	GLuint p = glCreateProgram();
	glAttachShader(p, v);
	glAttachShader(p, f);
	glLinkProgram(p);
	glDeleteShader(v);
	glDeleteShader(f);
	GLint ok = 0;
	glGetProgramiv(p, GL_LINK_STATUS, &ok);
	if(!ok)
	{
		char log[512] = {};
		glGetProgramInfoLog(p, sizeof(log), nullptr, log);
		LOGW("QS8: glow program did not link: %s", log);
		glDeleteProgram(p);
		return 0;
	}
	return p;
}

GLuint Tekstura(GLenum format, int w, int h)
{
	GLuint t = 0;
	glGenTextures(1, &t);
	glBindTexture(GL_TEXTURE_2D, t);
	glTexStorage2D(GL_TEXTURE_2D, 1, format, w, h);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	return t;
}

bool DoRysowania(GLuint t)
{
	glBindFramebuffer(GL_FRAMEBUFFER, s.fbo);
	glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t, 0);
	return glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
}

void UsunGl()
{
	if(s.prog_strefy) glDeleteProgram(s.prog_strefy);
	if(s.prog_wygladz) glDeleteProgram(s.prog_wygladz);
	if(s.prog_poswiata) glDeleteProgram(s.prog_poswiata);
	if(s.vao) glDeleteVertexArrays(1, &s.vao);
	if(s.fbo) glDeleteFramebuffers(1, &s.fbo);
	if(s.tex_strefy) glDeleteTextures(1, &s.tex_strefy);
	if(s.tex_wyg[0]) glDeleteTextures(2, s.tex_wyg);
	s.prog_strefy = s.prog_wygladz = s.prog_poswiata = 0;
	s.vao = s.fbo = s.tex_strefy = 0;
	s.tex_wyg[0] = s.tex_wyg[1] = 0;
	s.gl_gotowe = false;
	s.ma_dane = false;
}

bool PrzygotujGl()
{
	if(s.gl_gotowe)
		return true;
	if(s.gl_blad)
		return false;
	s.prog_strefy = Program(kFsStrefy);
	s.prog_wygladz = Program(kFsWygladz);
	s.prog_poswiata = Program(kFsPoswiata);
	if(!s.prog_strefy || !s.prog_wygladz || !s.prog_poswiata)
	{
		s.gl_blad = true;
		UsunGl();
		LOGW("QS8: glow disabled for this session (shaders)");
		return false;
	}
	s.u_strefy_obraz = glGetUniformLocation(s.prog_strefy, "obraz");
	s.u_strefy_komorka = glGetUniformLocation(s.prog_strefy, "komorka");
	s.u_wyg_nowe = glGetUniformLocation(s.prog_wygladz, "nowe");
	s.u_wyg_stare = glGetUniformLocation(s.prog_wygladz, "stare");
	s.u_wyg_a = glGetUniformLocation(s.prog_wygladz, "a");
	s.u_posw_strefy = glGetUniformLocation(s.prog_poswiata, "strefy");
	s.u_posw_ekran = glGetUniformLocation(s.prog_poswiata, "ekran");
	s.u_posw_skala = glGetUniformLocation(s.prog_poswiata, "skala");
	s.u_posw_sila = glGetUniformLocation(s.prog_poswiata, "sila");
	glGenVertexArrays(1, &s.vao);
	glGenFramebuffers(1, &s.fbo);

	// Polowiczna precyzja (ciemne kolory bez schodkow); bez niej 8 bitow.
	GLenum format = GL_RGBA16F;
	for(int proba = 0; proba < 2; proba++)
	{
		s.tex_strefy = Tekstura(format, kStrefyX, kStrefyY);
		glGenTextures(2, s.tex_wyg);
		for(GLuint t : s.tex_wyg)
		{
			glBindTexture(GL_TEXTURE_2D, t);
			glTexStorage2D(GL_TEXTURE_2D, 1, format, kStrefyX, kStrefyY);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		}
		if(DoRysowania(s.tex_strefy) && DoRysowania(s.tex_wyg[0]))
			break;
		glDeleteTextures(1, &s.tex_strefy);
		glDeleteTextures(2, s.tex_wyg);
		s.tex_strefy = 0;
		s.tex_wyg[0] = s.tex_wyg[1] = 0;
		if(format == GL_RGBA8)
		{
			s.gl_blad = true;
			UsunGl();
			LOGW("QS8: glow disabled for this session (framebuffer)");
			return false;
		}
		format = GL_RGBA8;
	}
	s.gl_gotowe = true;
	LOGI("QS8: glow GL ready (%s zones)", format == GL_RGBA16F ? "16-bit float" : "8-bit");
	return true;
}

// Zapamietuje i przywraca stan GL, ktorego dotykamy: tone mapper i petla
// klatek nie moga zauwazyc, ze tu bylismy.
struct StanGl
{
	GLint fbo = 0, prog = 0, vao = 0, aktywna = 0, tex0 = 0, tex1 = 0;
	GLint viewport[4] = {};
	GLboolean blend = GL_FALSE, scissor = GL_FALSE, depth = GL_FALSE, cull = GL_FALSE;

	StanGl()
	{
		glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbo);
		glGetIntegerv(GL_CURRENT_PROGRAM, &prog);
		glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vao);
		glGetIntegerv(GL_VIEWPORT, viewport);
		glGetIntegerv(GL_ACTIVE_TEXTURE, &aktywna);
		glActiveTexture(GL_TEXTURE0);
		glGetIntegerv(GL_TEXTURE_BINDING_2D, &tex0);
		glActiveTexture(GL_TEXTURE1);
		glGetIntegerv(GL_TEXTURE_BINDING_2D, &tex1);
		blend = glIsEnabled(GL_BLEND);
		scissor = glIsEnabled(GL_SCISSOR_TEST);
		depth = glIsEnabled(GL_DEPTH_TEST);
		cull = glIsEnabled(GL_CULL_FACE);
		glDisable(GL_BLEND);
		glDisable(GL_SCISSOR_TEST);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
	}

	~StanGl()
	{
		glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)fbo);
		glUseProgram((GLuint)prog);
		glBindVertexArray((GLuint)vao);
		glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, (GLuint)tex1);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, (GLuint)tex0);
		glActiveTexture((GLenum)aktywna);
		if(blend) glEnable(GL_BLEND);
		if(scissor) glEnable(GL_SCISSOR_TEST);
		if(depth) glEnable(GL_DEPTH_TEST);
		if(cull) glEnable(GL_CULL_FACE);
	}
};

bool PrzygotujSwapchain(XrSession sesja)
{
	if(s.sesja != sesja)
	{
		// Nowa sesja OpenXR bez Zniszcz po starej: stary uchwyt zginal z nia.
		s.sesja = sesja;
		s.swapchain = XR_NULL_HANDLE;
		s.obrazy.clear();
		s.swapchain_blad = false;
	}
	if(s.swapchain != XR_NULL_HANDLE)
		return true;
	if(s.swapchain_blad || sesja == XR_NULL_HANDLE)
		return false;

	uint32_t ile = 0;
	xrEnumerateSwapchainFormats(sesja, 0, &ile, nullptr);
	std::vector<int64_t> formaty(ile);
	if(ile > 0)
		xrEnumerateSwapchainFormats(sesja, ile, &ile, formaty.data());
	int64_t wybrany = 0;
	for(int64_t chce : {(int64_t)GL_SRGB8_ALPHA8, (int64_t)GL_RGBA8})
	{
		if(std::find(formaty.begin(), formaty.end(), chce) != formaty.end())
		{
			wybrany = chce;
			break;
		}
	}
	if(wybrany == 0)
	{
		s.swapchain_blad = true;
		LOGW("QS8: glow off, no RGBA swapchain format among %u", ile);
		return false;
	}

	XrSwapchainCreateInfo info{XR_TYPE_SWAPCHAIN_CREATE_INFO};
	info.usageFlags = XR_SWAPCHAIN_USAGE_SAMPLED_BIT | XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT;
	info.format = wybrany;
	info.sampleCount = 1;
	info.width = kSzer;
	info.height = kWys;
	info.faceCount = 1;
	info.arraySize = 1;
	info.mipCount = 1;
	XrResult r = xrCreateSwapchain(sesja, &info, &s.swapchain);
	if(XR_FAILED(r))
	{
		s.swapchain = XR_NULL_HANDLE;
		s.swapchain_blad = true;
		LOGW("QS8: glow off, swapchain refused (%d)", (int)r);
		return false;
	}
	uint32_t n = 0;
	if(XR_FAILED(xrEnumerateSwapchainImages(s.swapchain, 0, &n, nullptr)) || n == 0)
	{
		xrDestroySwapchain(s.swapchain);
		s.swapchain = XR_NULL_HANDLE;
		s.swapchain_blad = true;
		return false;
	}
	s.obrazy.assign(n, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
	if(XR_FAILED(xrEnumerateSwapchainImages(s.swapchain, n, &n,
			reinterpret_cast<XrSwapchainImageBaseHeader *>(s.obrazy.data()))))
	{
		xrDestroySwapchain(s.swapchain);
		s.swapchain = XR_NULL_HANDLE;
		s.obrazy.clear();
		s.swapchain_blad = true;
		return false;
	}
	LOGI("QS8: glow swapchain %dx%d, format 0x%llx, %u images", kSzer, kWys,
			(unsigned long long)wybrany, n);
	return true;
}

}  // namespace

void PoswiataAktualizuj(GLuint obraz, int szerokosc, int wysokosc)
{
	if(g_poziom.load(std::memory_order_relaxed) == 0)
	{
		s.ma_dane = false;
		return;
	}
	if(obraz == 0 || szerokosc <= 0 || wysokosc <= 0 || s.gl_blad)
		return;

	StanGl stan;  // przed PrzygotujGl: tworzenie tekstur i FBO tez zmienia stan
	if(!PrzygotujGl())
		return;
	glBindVertexArray(s.vao);

	// 1. strefy
	if(!DoRysowania(s.tex_strefy))
		return;
	glViewport(0, 0, kStrefyX, kStrefyY);
	glUseProgram(s.prog_strefy);
	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, obraz);
	// Tekstura swapchaina ma jeden poziom; filtr z mipmapami (domyslny w GL)
	// zrobilby ja "niekompletna" i odczyt dalby czern. Tone mapper tylko w nia
	// rysuje, kompozytor ma wlasny sampler -- dla nich to bez znaczenia.
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	glUniform1i(s.u_strefy_obraz, 0);
	glUniform2f(s.u_strefy_komorka, 1.0f / kStrefyX, 1.0f / kStrefyY);
	glDrawArrays(GL_TRIANGLES, 0, 3);

	// 2. wygladzanie (pierwsza klatka po wlaczeniu: od razu wynik, bez narastania)
	const Zegar::time_point teraz = Zegar::now();
	float a = 1.0f;
	if(s.ma_dane)
	{
		const double dt = std::chrono::duration<double>(teraz - s.ostatnia).count();
		a = (float)(1.0 - std::exp(-std::max(0.0, std::min(dt, 1.0)) / kTauS));
	}
	const int cel = 1 - s.wyg;
	if(!DoRysowania(s.tex_wyg[cel]))
		return;
	glUseProgram(s.prog_wygladz);
	glActiveTexture(GL_TEXTURE0);
	glBindTexture(GL_TEXTURE_2D, s.tex_strefy);
	glActiveTexture(GL_TEXTURE1);
	glBindTexture(GL_TEXTURE_2D, s.tex_wyg[s.wyg]);
	glUniform1i(s.u_wyg_nowe, 0);
	glUniform1i(s.u_wyg_stare, 1);
	glUniform1f(s.u_wyg_a, a);
	glDrawArrays(GL_TRIANGLES, 0, 3);
	s.wyg = cel;
	s.ma_dane = true;
	s.ostatnia = teraz;
}

void PoswiataWstaw(std::vector<const XrCompositionLayerBaseHeader *> &warstwy, size_t przed,
		XrSession sesja, bool ciemny_pokoj)
{
	const int poziom = g_poziom.load(std::memory_order_relaxed);
	if(poziom != s.zalogowany_poziom)
	{
		s.zalogowany_poziom = poziom;
		LOGI("QS8: glow level %d (%s)", poziom, ciemny_pokoj ? "dark room" : "room visible: glow waits for a dark room");
	}
	if(poziom <= 0 || poziom > 3 || !ciemny_pokoj || !s.ma_dane || !s.gl_gotowe || przed >= warstwy.size())
		return;
	if(std::chrono::duration<double>(Zegar::now() - s.ostatnia).count() > kSwiezeS)
		return;

	// Geometria ekranu z warstwy, ktora juz lezy na pozycji [przed].
	const XrCompositionLayerBaseHeader *ekran = warstwy[przed];
	float szer_m = 0.0f, wys_m = 0.0f;
	if(ekran->type == XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR)
	{
		const auto *c = reinterpret_cast<const XrCompositionLayerCylinderKHR *>(ekran);
		if(c->radius <= 0.0f || c->aspectRatio <= 0.0f)
			return;
		szer_m = c->radius * c->centralAngle;
		wys_m = szer_m / c->aspectRatio;
	}
	else if(ekran->type == XR_TYPE_COMPOSITION_LAYER_QUAD)
	{
		const auto *q = reinterpret_cast<const XrCompositionLayerQuad *>(ekran);
		szer_m = q->size.width;
		wys_m = q->size.height;
	}
	else
		return;
	if(szer_m <= 0.0f || wys_m <= 0.0f)
		return;

	float margines = kMargines * wys_m;
	float marg_x = margines;
	float szer_p = szer_m + 2.0f * marg_x;
	const float wys_p = wys_m + 2.0f * margines;
	if(ekran->type == XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR)
	{
		// Luk cylindra nie moze przekroczyc pelnego obrotu.
		const auto *c = reinterpret_cast<const XrCompositionLayerCylinderKHR *>(ekran);
		const float max_luk = 6.1f * c->radius;
		if(szer_p > max_luk)
		{
			szer_p = std::max(szer_m, max_luk);
			marg_x = (szer_p - szer_m) * 0.5f;
		}
	}

	if(!PrzygotujSwapchain(sesja))
		return;

	uint32_t indeks = 0;
	XrSwapchainImageAcquireInfo acq{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
	if(XR_FAILED(xrAcquireSwapchainImage(s.swapchain, &acq, &indeks)))
		return;
	XrSwapchainImageWaitInfo wait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
	wait.timeout = XR_INFINITE_DURATION;
	bool narysowane = false;
	if(XR_SUCCEEDED(xrWaitSwapchainImage(s.swapchain, &wait)) && indeks < s.obrazy.size())
	{
		StanGl stan;
		glBindVertexArray(s.vao);
		if(DoRysowania(s.obrazy[indeks].image))
		{
			glViewport(0, 0, kSzer, kWys);
			glUseProgram(s.prog_poswiata);
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GL_TEXTURE_2D, s.tex_wyg[s.wyg]);
			glUniform1i(s.u_posw_strefy, 0);
			const float x0 = marg_x / szer_p, y0 = margines / wys_p;
			glUniform4f(s.u_posw_ekran, x0, y0, 1.0f - x0, 1.0f - y0);
			glUniform2f(s.u_posw_skala, szer_p / std::max(margines, 1e-3f), wys_p / std::max(margines, 1e-3f));
			glUniform1f(s.u_posw_sila, kSila[poziom]);
			glDrawArrays(GL_TRIANGLES, 0, 3);
			narysowane = true;
		}
	}
	XrSwapchainImageReleaseInfo rel{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
	xrReleaseSwapchainImage(s.swapchain, &rel);
	if(!narysowane)
		return;

	XrSwapchainSubImage obraz{};
	obraz.swapchain = s.swapchain;
	obraz.imageRect.offset = XrOffset2Di{0, 0};
	obraz.imageRect.extent = XrExtent2Di{kSzer, kWys};
	obraz.imageArrayIndex = 0;

	const XrCompositionLayerBaseHeader *nowa = nullptr;
	if(ekran->type == XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR)
	{
		const auto *c = reinterpret_cast<const XrCompositionLayerCylinderKHR *>(ekran);
		s.cylinder = XrCompositionLayerCylinderKHR{XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR};
		s.cylinder.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
		s.cylinder.space = c->space;
		s.cylinder.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
		s.cylinder.subImage = obraz;
		s.cylinder.pose = c->pose;
		s.cylinder.radius = c->radius;
		s.cylinder.centralAngle = szer_p / c->radius;
		s.cylinder.aspectRatio = szer_p / wys_p;
		nowa = reinterpret_cast<const XrCompositionLayerBaseHeader *>(&s.cylinder);
	}
	else
	{
		const auto *q = reinterpret_cast<const XrCompositionLayerQuad *>(ekran);
		s.quad = XrCompositionLayerQuad{XR_TYPE_COMPOSITION_LAYER_QUAD};
		s.quad.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
		s.quad.space = q->space;
		s.quad.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
		s.quad.subImage = obraz;
		s.quad.pose = q->pose;
		s.quad.size = XrExtent2Df{szer_p, wys_p};
		nowa = reinterpret_cast<const XrCompositionLayerBaseHeader *>(&s.quad);
	}
	warstwy.insert(warstwy.begin() + (std::ptrdiff_t)przed, nowa);
}

void PoswiataZniszcz()
{
	if(s.swapchain != XR_NULL_HANDLE)
		xrDestroySwapchain(s.swapchain);
	s.swapchain = XR_NULL_HANDLE;
	s.obrazy.clear();
	s.sesja = XR_NULL_HANDLE;
	s.swapchain_blad = false;
	if(eglGetCurrentContext() != EGL_NO_CONTEXT)
		UsunGl();
	else
	{
		// Bez kontekstu glDelete* nic nie robi; obiekty znikna z kontekstem.
		s.gl_gotowe = false;
		s.ma_dane = false;
		s.prog_strefy = s.prog_wygladz = s.prog_poswiata = 0;
		s.vao = s.fbo = s.tex_strefy = 0;
		s.tex_wyg[0] = s.tex_wyg[1] = 0;
	}
	s.gl_blad = false;
	s.zalogowany_poziom = -1;
}

}  // namespace qs8

extern "C" JNIEXPORT void JNICALL
Java_io_github_gblandro_p5m_qs8_PoswiataNative_nativeSetPoziom(JNIEnv *, jobject, jint poziom)
{
	qs8::g_poziom.store(std::max(0, std::min(3, (int)poziom)), std::memory_order_relaxed);
	LOGI("QS8: glow setting %d (0 off, 1 soft, 2 medium, 3 strong)", (int)poziom);
}
