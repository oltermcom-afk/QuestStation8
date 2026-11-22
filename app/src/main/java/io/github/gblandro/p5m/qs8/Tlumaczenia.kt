// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import io.github.gblandro.p5m.PainelDeAjuste

/**
 * Wersja polska / angielska. Teksty P5M są wpisane w Kotlinie po angielsku i tak
 * zostają — tłumaczymy dopiero to, co trafia na ekran:
 *
 * - ekrany 2D (launcher, diagnostyka, logowanie PSN, rejestracja…): [zainstaluj]
 *   rejestruje w aplikacji tłumacza, który przed narysowaniem podmienia teksty
 *   w TextView według słownika (pomija pola edycji i teksty z linkami);
 * - panel w grze i paski na Canvas: [t] w kilku miejscach rysowania.
 *
 * Brak tłumaczenia = tekst angielski, więc nowy tekst z upstreamu niczego nie
 * psuje. Domyślnie polski; Horizon OS nie ma polskiego, więc o języku decyduje
 * przełącznik w aplikacji (Help › Language), nie system.
 */
object Tlumaczenia
{
	@Volatile var polski = true
		private set

	fun init(context: Context) { polski = Qs8Prefs(context).jezykPolski }

	fun ustaw(context: Context, pl: Boolean)
	{
		Qs8Prefs(context).jezykPolski = pl
		polski = pl
	}

	/** Tłumaczenie tekstu albo ten sam tekst, gdy brak go w słowniku (albo język angielski). */
	fun t(tekst: String): String
	{
		if(!polski || tekst.isBlank())
			return tekst
		val klucz = normalizuj(tekst)
		SLOWNIK[klucz]?.let { return it }
		// Ta sama wartość z wielkiej / małej litery („Medium” / „medium”).
		// Nagłówki sekcji P5M są pisane wielkimi literami („ADVANCED”).
		if(klucz.length > 1 && klucz == klucz.uppercase() && klucz != klucz.lowercase())
			SLOWNIK[klucz.lowercase().replaceFirstChar { it.uppercase() }]?.let { return it.uppercase() }
		val male = klucz.replaceFirstChar { it.lowercase() }
		SLOWNIK[male]?.let { pl -> return if(klucz[0].isUpperCase()) pl.replaceFirstChar { it.uppercase() } else pl }
		for((wzor, zamiana) in WZORY)
		{
			val m = wzor.matchEntire(tekst.trim()) ?: continue
			return zamiana(m)
		}
		return tekst
	}

	fun linie(lista: List<PainelDeAjuste.Linha>) =
		if(!polski) lista else lista.map { it.copy(rotulo = t(it.rotulo), valor = t(it.valor)) }

	fun odczyty(lista: List<PainelDeAjuste.Medidor>) =
		if(!polski) lista else lista.map { it.copy(nome = t(it.nome), valor = t(it.valor), detalhe = t(it.detalhe)) }

	private fun normalizuj(s: String) = s.trim().replace(Regex("\\s+"), " ")

	// ------------------------------------------------------------------ ekrany 2D

	/** Raz, w P5MApp.onCreate: tłumacz dla każdej aktywności. */
	fun zainstaluj(app: Application)
	{
		init(app)
		app.registerActivityLifecycleCallbacks(object: Application.ActivityLifecycleCallbacks
		{
			override fun onActivityCreated(a: Activity, b: Bundle?)
			{
				val decor = a.window?.decorView ?: return
				decor.viewTreeObserver.addOnPreDrawListener(object: ViewTreeObserver.OnPreDrawListener
				{
					override fun onPreDraw(): Boolean
					{
						if(!polski)
							return true
						// Coś zmienione = rysujemy jeszcze raz już po polsku (bez mignięcia angielskim).
						return !przetlumacz(decor)
					}
				})
			}
			override fun onActivityStarted(a: Activity) {}
			override fun onActivityResumed(a: Activity) {}
			override fun onActivityPaused(a: Activity) {}
			override fun onActivityStopped(a: Activity) {}
			override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
			override fun onActivityDestroyed(a: Activity) {}
		})
	}

	private fun przetlumacz(v: View): Boolean
	{
		var zmiana = false
		if(v is TextView && v !is EditText)
		{
			val tekst = v.text
			if(tekst != null && tekst !is Spanned && tekst.isNotEmpty())
			{
				val s = tekst.toString()
				val pl = t(s)
				if(pl != s)
				{
					v.text = pl
					zmiana = true
				}
			}
		}
		if(v is ViewGroup)
			for(i in 0 until v.childCount)
				zmiana = przetlumacz(v.getChildAt(i)) || zmiana
		return zmiana
	}

	// ------------------------------------------------------------------ słownik

	private val WZORY: List<Pair<Regex, (MatchResult) -> String>> = listOf(
		Regex("(\\d+) Mbps \\(auto, from (\\d+)\\)") to { m -> "${m.groupValues[1]} Mb/s (auto, z ${m.groupValues[2]})" },
		Regex("(\\d+) Mbps") to { m -> "${m.groupValues[1]} Mb/s" },
		Regex("(\\d+) nits") to { m -> "${m.groupValues[1]} nitów" },
		Regex("HDR peak (\\d+) nits( — only with 10-bit color)?") to { m ->
			"Szczyt HDR ${m.groupValues[1]} nitów" + if(m.groupValues[2].isNotEmpty()) " — działa tylko z 10-bit" else "" },
		Regex("Size 1:1 pixel: (\\d+)°") to { m -> "Rozmiar 1:1: ${m.groupValues[1]}°" },
		Regex("Size 1:1 is outside the range, set to (\\d+)°") to { m -> "1:1 poza zakresem, ustawiono ${m.groupValues[1]}°" },
		Regex("B · ENHANCED — sharpness (.+)") to { m -> "B · ULEPSZONY — wyostrzanie: ${t(m.groupValues[1])}" },
		Regex("Profile: (.+)") to { m -> "Profil: ${t(m.groupValues[1])}" },
		Regex("(\\d+)% left\\.") to { m -> "Zostało ${m.groupValues[1]}%." },
		Regex("([\\d.,]+)% loss") to { m -> "${m.groupValues[1]}% strat" },
		Regex("(\\d+) packets/s") to { m -> "${m.groupValues[1]} pakietów/s" },
		Regex("(\\d+) dropped") to { m -> "utracone: ${m.groupValues[1]}" },
		Regex("Account ID filled in from the PSN account signed in on this headset \\(account number (\\d+)\\)\\.") to { m ->
			"Account ID wpisane z konta PSN zalogowanego w goglach (numer konta ${m.groupValues[1]})." },
		Regex("QuestStation8 (\\S+(?: \\(\\d+\\))?) is available\\. You have (\\S+(?: \\(\\d+\\))?)\\.\\n(.*)\\nUpdate downloads it and opens the installer\\.",
				RegexOption.DOT_MATCHES_ALL) to { m ->
			"Dostępna jest wersja QuestStation8 ${m.groupValues[1]}. Masz ${m.groupValues[2]}.\n${m.groupValues[3]}\n" +
					"„Aktualizuj” pobierze ją i otworzy instalator." },
	)

	private val SLOWNIK: Map<String, String> = listOf(
		// --- launcher: strony i menu
		"Play" to "Graj",
		"Picture" to "Obraz",
		"Screen" to "Ekran",
		"Stream" to "Strumień",
		"Controller" to "Pad",
		"Help" to "Pomoc",
		"Advanced" to "Zaawansowane",
		"Your console, ready when you are." to "Twoja konsola, gotowa, kiedy ty.",
		"How the game looks in the headset." to "Jak gra wygląda w goglach.",
		"What the console sends over the network." to "Co konsola wysyła przez sieć.",
		"Haptics, triggers, lights, sound and your in-game shortcut." to "Wibracje, triggery, światła, dźwięk i skrót w grze.",
		"Report a problem, diagnostics and project support." to "Zgłoszenie problemu, diagnostyka i wsparcie projektu.",
		"Let's play" to "Gramy",
		"D-pad  Move      Cross  Select      Square  Step back      Circle  Back" to
				"Krzyżak  ruch      Krzyżyk  wybór      Kwadrat  krok wstecz      Kółko  wstecz",
		"While playing, this opens Screen settings: move, resize and sharpen the screen, and click the touchpad." to
				"W grze otwiera ustawienia ekranu: przesuwanie, rozmiar, ostrość i klik touchpada.",
		"The app crashed the last time it ran. The reason is in Help › Diagnostics & report." to
				"Ostatnim razem aplikacja się zawiesiła. Przyczyna jest w Pomoc › Diagnostyka i zgłoszenie.",
		"Remote connection (PSN)" to "Połączenie zdalne (PSN)",
		"For playing away from home. The console list finds consoles on your local network, which does not cross the internet; this reaches the console through your PSN account." to
				"Do gry poza domem. Lista konsol widzi tylko sieć lokalną; tu konsola jest osiągana przez konto PSN.",
		// --- launcher: Ekran
		"Mode" to "Tryb",
		"Immersive (3D)" to "Immersyjny (3D)",
		"Immersive" to "Immersyjny",
		"Window" to "Okno",
		"Sharpness" to "Ostrość",
		"Emulated 3D" to "Emulowane 3D",
		"3D strength" to "Siła 3D",
		"Pop-out" to "Wychodzenie z ekranu",
		"Neural depth" to "Głębia z sieci neuronowej",
		"Screen depth" to "Głębia ekranu",
		"3D requires immersive mode. Turn off Emulated 3D to choose Window." to
				"3D wymaga trybu immersyjnego. Wyłącz emulowane 3D, żeby wybrać okno.",
		"Curved screen, managed color, refresh rate matched to the source, and Screen settings in game. Takes the whole display." to
				"Zakrzywiony ekran, zarządzanie kolorem, odświeżanie zgodne ze źródłem i ustawienia ekranu w grze. Zajmuje cały widok.",
		"A system panel, side by side with your other apps. No curved screen, no managed color, no Screen settings in game." to
				"Panel systemowy obok innych aplikacji. Bez zakrzywienia, zarządzania kolorem i ustawień ekranu w grze.",
		"The image as the console delivers it, with no sharpening." to "Obraz taki, jak wysyła konsola, bez wyostrzania.",
		"The compositor chooses the filter for the screen size and GPU load." to
				"Kompozytor sam dobiera filtr do rozmiaru ekranu i obciążenia GPU.",
		"Automatic filtering is available in immersive mode. Choose light, medium or strong for window sharpening." to
				"Filtr automatyczny działa w trybie immersyjnym. W oknie wybierz lekkie, średnie albo mocne.",
		"This also changes in game, with Square in Screen settings. Square here steps back." to
				"Zmienisz to też w grze: X / Kwadrat w ustawieniach ekranu. Tu Kwadrat cofa.",
		"In window mode the sharpening is a shader: it costs one GPU pass, because there is no layer of ours in the compositor." to
				"W oknie wyostrza shader: to jeden dodatkowy przebieg GPU, bo nie mamy własnej warstwy w kompozytorze.",
		"The console sends a flat image. The app guesses depth from it and builds the second eye. It is a guess, so some scenes come out wrong — the aim is a sense of volume, not accurate depth." to
				"Konsola wysyła płaski obraz. Aplikacja zgaduje z niego głębię i buduje drugie oko. To przybliżenie — chodzi o wrażenie przestrzeni, nie o dokładną głębię.",
		"Turning on 3D switches to immersive mode. Window is unavailable until 3D is off. Reconnect to switch." to
				"Włączenie 3D przełącza w tryb immersyjny; okno wróci po wyłączeniu 3D. Zmiana po ponownym połączeniu.",
		"How deep the scene goes. At 100% the far end reaches most of the way to what your eyes can fuse; the limit comes from your interpupillary distance and the screen size. Also on Options in Screen settings, in game." to
				"Jak głęboka jest scena. Przy 100% dalekie plany sięgają prawie granicy wygodnego widzenia (zależy od rozstawu oczu i rozmiaru ekranu). W grze: prawa gałka / Options w ustawieniach ekranu.",
		"The nearest part of the scene comes out of the screen toward you. A thin black edge on each eye makes the frame float in front, so nothing breaks at the borders. Screen depth is capped at 55% while this is on." to
				"Najbliższe obiekty wychodzą z ekranu w twoją stronę. Cienka czarna ramka unosi się z przodu, więc krawędzie się nie psują. Głębia ekranu jest wtedy ograniczona do 55%.",
		"The scene stays behind the screen, like looking through a window. The most comfortable. Turn on to let near things come out toward you." to
				"Scena zostaje za ekranem, jak za oknem — najwygodniej. Włącz, żeby bliskie obiekty wychodziły do ciebie.",
		"Depth comes from a neural network (ZipDepth) running on the headset GPU, about 20 times a second. Shapes and distances are far more accurate than the image cues. The first seconds of a session use the cues while it loads." to
				"Głębię liczy sieć neuronowa (ZipDepth) na GPU gogli, ok. 20 razy na sekundę — dużo dokładniej niż wskazówki z obrazu. Na początku sesji, zanim się wczyta, używane są wskazówki.",
		"Depth is guessed from the picture itself: what is low on the screen, detailed or saturated comes forward, sky and haze go back. Lighter on the GPU." to
				"Głębia zgadywana z samego obrazu: to, co nisko, szczegółowe albo nasycone, idzie do przodu; niebo i mgła do tyłu. Lżej dla GPU.",
		"What sits exactly on the screen. At 100% the whole scene stays behind it, like looking through a window, which is the most comfortable. Lower lets the nearest things come out toward you." to
				"Co leży dokładnie na ekranie. Przy 100% cała scena jest za nim, jak za oknem — najwygodniej. Mniej pozwala bliskim obiektom wychodzić do ciebie.",
		// --- launcher: Strumień
		"Profile" to "Profil",
		"Bitrate" to "Bitrate",
		"Auto bitrate" to "Auto bitrate",
		"Color" to "Kolor",
		"Video path" to "Ścieżka wideo",
		"Panel rate" to "Odświeżanie panelu",
		"HDR peak" to "Szczyt HDR",
		"How bright the game's highlights are assumed to be with 10-bit color. Lower brightens highlights in games that never reach 1000 nits; higher keeps bright games from clipping. Changes live in the game too: hold Y / △ in the settings panel." to
				"Jak jasne mogą być najjaśniejsze miejsca gry przy kolorze 10-bit. Mniej rozjaśnia światła w grach, które nie sięgają 1000 nitów; więcej chroni jasne gry przed przepaleniem. W grze: przytrzymaj Y / △ w panelu ustawień.",
		"Each console frame shown three times. About 3 ms less latency, more battery and heat." to
				"Każda klatka konsoli wyświetlona trzy razy. Ok. 3 ms mniej opóźnienia, więcej baterii i ciepła.",
		"Each console frame shown twice. The default." to "Każda klatka konsoli wyświetlona dwa razy. Domyślnie.",
		"shader (automatic)" to "shader (automatycznie)",
		"direct (automatic)" to "bezpośrednia (automatycznie)",
		"shader + frames" to "shader + klatki",
		"Generated frames" to "Generowane klatki",
		"Up to 60 fps" to "do 60 fps",
		"Up to 120 fps" to "do 120 fps",
		"One predicted frame per console frame in 30 fps games: motion like 60 fps, fewer and smaller artifacts. In 60 fps games no frames are predicted. No added latency." to
				"W grach 30 fps jedna przewidziana klatka na każdą z konsoli: ruch jak przy 60 fps, mniej i mniejszych artefaktów. W grach 60 fps bez przewidywania. Bez dodatkowego opóźnienia.",
		"A predicted frame on every panel refresh: 30 fps games get three per console frame. The smoothest, with the most artifacts in fast motion. No added latency." to
				"Przewidziana klatka przy każdym odświeżeniu panelu: w grach 30 fps trzy na każdą z konsoli. Najpłynniej, ale najwięcej artefaktów w szybkim ruchu. Bez dodatkowego opóźnienia.",
		"direct" to "bezpośrednia",
		// --- panel w grze, strona 2 „Zaawansowane” (qs8/PanelZaawansowany)
		"ADVANCED" to "ZAAWANSOWANE",
		"FRAMES & PANEL" to "KLATKI I PANEL",
		"NEXT CONNECTION" to "OD NASTĘPNEGO POŁĄCZENIA",
		"↕ choose  ·  ↔ or ✕ / A change  ·  hold Options or ≡: page 1  ·  ○ / B close" to
				"↕ wybór  ·  ↔ albo ✕ / A zmiana  ·  przytrzymaj Options albo ≡: strona 1  ·  ○ / B zamknij",
		"Changes live" to "Zmienia się na żywo",
		"Saved. Takes effect from the next connection." to "Zapisane. Zadziała od następnego połączenia.",
		"Saved. No effect in this session." to "Zapisane. W tej sesji bez efektu.",
		"Needs the shader path: from the next connection." to "Wymaga ścieżki shader: od następnego połączenia.",
		"Only with 10-bit color." to "Tylko przy kolorze 10-bit.",
		"Only in a 3D session: turn on Emulated 3D." to "Tylko w sesji 3D: włącz emulację 3D.",
		"Measured: about 35 ms latency with it, 17 ms without." to "Zmierzone: ok. 35 ms opóźnienia z nią, 17 ms bez.",
		"3D and 10-bit always use the shader." to "3D i 10-bit zawsze używają shadera.",
		"Window selects its video path from Sharpness and Color. Frame prediction is only available in immersive mode." to
				"Okno dobiera ścieżkę wideo do ostrości i koloru. Przewidywanie klatek działa tylko w trybie immersyjnym.",
		"3D requires the shader. Select to toggle experimental frame prediction." to
				"3D wymaga shadera. Wybierz, żeby włączyć lub wyłączyć eksperymentalne przewidywanie klatek.",
		"10-bit color requires tone mapping, so the shader stays on. Select to toggle experimental frame prediction." to
				"Kolor 10-bit wymaga mapowania tonów, więc shader zostaje. Wybierz, żeby przełączyć przewidywanie klatek.",
		"From the network to the compositor with no copy. The lowest latency there is." to
				"Z sieci prosto do kompozytora, bez kopiowania. Najmniejsze możliwe opóźnienie.",
		"Correct 10-bit color, at the cost of one GPU pass." to "Poprawny kolor 10-bit kosztem jednego przebiegu GPU.",
		"120 frames per second out of the source's 60, extrapolated by the Adreno. No added latency; artifacts wherever the guess is wrong." to
				"120 klatek na sekundę z 60 źródła, przewidywanych przez Adreno. Bez dodatkowego opóźnienia; artefakty tam, gdzie przewidywanie chybi.",
		"10-bit gradients with shader tone mapping. Uses an extra GPU pass. 3D stays at native 8-bit. Reconnect to switch." to
				"Gładkie przejścia 10-bit z mapowaniem tonów w shaderze. Dodatkowy przebieg GPU. 3D zostaje 8-bit. Zmiana po ponownym połączeniu.",
		"The Remote Play default, and what gives correct color today." to "Standard Remote Play i dziś pewnie poprawny kolor.",
		"Every session uses the bitrate above. Pick this, or Only lowers, for the lowest latency." to
				"Każda sesja używa bitrate'u powyżej. Wybierz to albo „Tylko obniża” dla najmniejszego opóźnienia.",
		"The bitrate above is the ceiling. A session that keeps losing packets makes the next one lower; clean sessions climb back." to
				"Bitrate powyżej to sufit. Sesja z utratą pakietów obniża następną; czyste sesje wracają w górę.",
		"Climbs a step after each clean session, for a sharper picture. Higher bitrate also takes the headset a few ms longer to decode." to
				"Po każdej czystej sesji o stopień w górę, dla ostrzejszego obrazu. Wyższy bitrate to też kilka ms dłuższe dekodowanie.",
		"Experimental: above what Remote Play normally uses. The diary shows what the console actually sent." to
				"Eksperyment: więcej niż zwykle w Remote Play. Dziennik pokazuje, co konsola naprawdę wysłała.",
		"Lighter on the network. Pick this if the picture stutters or freezes on weak Wi-Fi." to
				"Lżej dla sieci. Wybierz, gdy obraz się tnie albo zamarza na słabym Wi-Fi.",
		"The default. Fine on Wi-Fi 6 with the console on a cable." to "Domyślnie. Dobre przy Wi-Fi 6 i konsoli na kablu.",
		"Sharper in fast scenes, but needs a strong link. If you see stutter or blocks, step down." to
				"Ostrzej w szybkich scenach, ale wymaga mocnego łącza. Przy przycięciach albo blokach zejdź niżej.",
		"Only lowers" to "Tylko obniża",
		"Up and down" to "W górę i w dół",
		// --- profile (QS8)
		"Balanced" to "Zrównoważony",
		"Ultra smooth" to "Ultra płynność",
		"Cinema" to "Kino",
		"Custom" to "Własny",
		"Direct video path at 120 Hz. The default. Takes effect on the next connection." to
				"Ścieżka bezpośrednia, 120 Hz. Domyślnie. Działa od następnego połączenia.",
		"Shader path with predicted frames between the console's: smoother motion, some artifacts in fast scenes. Takes effect on the next connection." to
				"Ścieżka shader z przewidywanymi klatkami: płynniejszy ruch, czasem artefakty w szybkich scenach. Działa od następnego połączenia.",
		"10-bit color (smooth gradients, PS5 only) and a curved screen in the dark. One extra GPU pass, a little more latency. Takes effect on the next connection." to
				"Kolor 10-bit (gładkie przejścia, tylko PS5) i zakrzywiony ekran w ciemności. Dodatkowy przebieg GPU, trochę większe opóźnienie. Działa od następnego połączenia.",
		"Settings changed by hand. Choose a profile to set picture, path and panel rate at once (bitrate and 3D stay as they are)." to
				"Ustawienia zmienione ręcznie. Profil ustawia naraz obraz, ścieżkę i odświeżanie (bitrate i 3D zostają bez zmian).",
		// --- launcher: Pad
		"Rumble" to "Wibracje",
		"Adaptive triggers" to "Adaptacyjne triggery",
		"Unavailable" to "Niedostępne",
		"Controller lights" to "Światła pada",
		"Follow console" to "Jak konsola",
		"System" to "System",
		"Motion controls" to "Sterowanie ruchem",
		"Spatial audio" to "Dźwięk przestrzenny",
		"Head-locked" to "Przypięty do głowy",
		"Microphone" to "Mikrofon",
		"No permission" to "Brak uprawnień",
		"Screen settings shortcut" to "Skrót ustawień ekranu",
		"Stick calibration" to "Kalibracja gałek",
		"Measures where each stick rests and how much it jitters, and sets the deadzone from that. Worth doing once per controller." to
				"Mierzy położenie spoczynkowe i drgania gałek i na tej podstawie ustawia martwą strefę. Warto raz dla każdego pada.",
		"Both stick buttons. Fastest one, but some games use that combination themselves, and there the panel opens in the middle of a fight." to
				"Obie gałki wciśnięte. Najszybciej, ale niektóre gry używają tej kombinacji i panel otworzy się w środku walki.",
		"Both stick buttons plus R1. Three fingers, and almost no game asks for that shape, so it stays out of the way." to
				"Obie gałki plus R1. Trzy palce, prawie żadna gra tego nie używa, więc nie przeszkadza.",
		"Hold both stick buttons for about a second. Nothing to memorise, but the game still sees the clicks while you hold them." to
				"Przytrzymaj obie gałki ok. sekundy. Łatwe do zapamiętania, ale gra widzi wciśnięcia, dopóki trzymasz.",
		"Classic rumble announces a DualShock 4, which has no adaptive triggers, so the console sends none. Pick haptic coil, haptic motor or off to get them." to
				"Klasyczne wibracje przedstawiają pad jako DualShock 4, bez adaptacyjnych triggerów. Wybierz cewkę, silnik albo wyłączone, żeby je mieć.",
		"The triggers resist, click and vibrate the way the game asks, like on the PS5. R3 in Screen settings, in game, turns them off and on at any moment. Immersive mode only for now." to
				"Triggery stawiają opór, klikają i wibrują tak, jak chce gra — jak na PS5. W grze R3 w ustawieniach ekranu włącza je i wyłącza. Na razie tylko w trybie immersyjnym.",
		"The triggers stay free. Turn this on here, or with R3 in Screen settings, in game." to
				"Triggery pracują swobodnie. Włączysz tu albo w grze: R3 w ustawieniach ekranu.",
		"Tilting the controller aims and steers in games that use motion, as on the console. Takes effect on the next stream." to
				"Przechylanie pada celuje i steruje w grach, które to obsługują, jak na konsoli. Działa od następnej sesji.",
		"The console gets no motion from the controller. Turn on for games that aim or steer by tilting." to
				"Konsola nie dostaje ruchu pada. Włącz w grach, w których celuje się lub steruje przechyleniem.",
		"Your voice goes to the console for party and game chat, with the headset's echo cancelling so the game sound does not come back." to
				"Twój głos idzie do konsoli (czat drużyny i gry), z usuwaniem echa gogli, żeby dźwięk gry nie wracał.",
		"The console hears nothing from the headset." to "Konsola nic nie słyszy z gogli.",
		"The lightbar takes the colour the game picks and the LEDs show your player number, like on the PS5. Immersive mode only for now." to
				"Pasek świetlny przyjmuje kolor z gry, a diody pokazują numer gracza, jak na PS5. Na razie tylko w trybie immersyjnym.",
		"The lightbar and player LEDs stay as the headset sets them." to "Pasek świetlny i diody zostają takie, jak ustawią gogle.",
		"The console reduces the game's haptics to two motors and sends that. The most faithful on paper, but on 12/09 a whole session came back with zero rumble packets while the game was shaking on screen." to
				"Konsola sprowadza haptykę gry do dwóch silników. Na papierze najwierniej, ale bywały sesje bez żadnych pakietów wibracji.",
		"The console sends its full haptic track and the app plays it on the DualSense coils. The best when it works, and the one that can start seconds late until you turn the controller off and on again." to
				"Konsola wysyła pełną ścieżkę haptyki, a aplikacja gra ją na cewkach DualSense. Najlepiej, gdy działa; czasem spóźnia się o sekundy, dopóki nie wyłączysz i nie włączysz pada.",
		"Nothing vibrates, not even the test buzz when the stream opens. The client announces itself as DualShock 4, so the console does not even open the haptic track." to
				"Nic nie wibruje, nawet testowy impuls na starcie. Klient przedstawia się jako DualShock 4, więc konsola nie otwiera ścieżki haptyki.",
		"The console sends its full haptic track and the app turns it into motor strength. No coil, so no delay, and it works on controllers that are not a DualSense. It vibrates for footsteps and rain too." to
				"Konsola wysyła pełną ścieżkę haptyki, a aplikacja zamienia ją na siłę silnika. Bez cewki, więc bez opóźnień; działa też z padami innymi niż DualSense. Wibruje również przy krokach i deszczu.",
		"In window mode the sound follows your head. Spatial audio needs immersive mode." to
				"W oknie dźwięk podąża za głową. Dźwięk przestrzenny wymaga trybu immersyjnego.",
		"Stereo as the console sends it, locked to your head." to "Stereo prosto z konsoli, przypięte do głowy.",
		"Two speakers in a small room: both ears hear both speakers, with head shadow, wall reflections and a short tail. The sound stays outside your head." to
				"Dwa głośniki w małym pokoju: oba uszy słyszą oba głośniki, z cieniem głowy, odbiciami i krótkim pogłosem. Dźwięk zostaje poza głową.",
		"The two channels become speakers on the screen. Turn your head and the sound stays where the screen is." to
				"Dwa kanały stają się głośnikami przy ekranie. Obróć głowę, a dźwięk zostanie tam, gdzie ekran.",
		"Your DualSense is in Bluetooth power saving mode, which adds input lag. Turn it off (hold the PS button for 10 seconds) and on again before you play." to
				"DualSense jest w trybie oszczędzania Bluetooth, co dodaje opóźnienie. Przed grą wyłącz go (przytrzymaj PS 10 s) i włącz ponownie.",
		"classic" to "klasyczne",
		"haptic coil" to "cewka haptyczna",
		"haptic motor" to "silnik haptyczny",
		// --- launcher: Pomoc
		"Language" to "Język (Language)",
		"Diagnostics & report" to "Diagnostyka i zgłoszenie",
		"Report a problem, share the diary with a computer on your network, and the tools for testing the controller and the video path." to
				"Zgłoś problem, udostępnij dziennik komputerowi w sieci, narzędzia do testu pada i ścieżki wideo.",
		"Check for updates" to "Sprawdzaj aktualizacje",
		"Based on P5M by gblandro" to "Oparte na P5M (gblandro)",
		"QuestStation8 is built on P5M, licensed under AGPL-3.0. Opens the original project and its source code." to
				"QuestStation8 powstał na bazie P5M, licencja AGPL-3.0. Otwiera oryginalny projekt i jego kod źródłowy.",
		"Support P5M development on Patreon" to "Wesprzyj rozwój P5M na Patreonie",
		"P5M is free and the source is open. If it is useful to you, chipping in pays for the hours that keep it moving." to
				"P5M jest darmowy i otwarty. Jeśli ci się przydaje, wsparcie opłaca godziny pracy autora.",
		"Update" to "Aktualizuj",
		"Downloading..." to "Pobieranie…",
		"Skip this version" to "Pomiń tę wersję",
		// --- wartości
		"On" to "Wł.",
		"Off" to "Wył.",
		"off" to "wył.",
		"light" to "lekkie",
		"medium" to "średnie",
		"strong" to "mocne",
		"automatic" to "automatyczne",
		"normal" to "normalny",
		"soft" to "łagodny",
		"dark" to "ciemny",
		"bright" to "jasny",
		"subtle" to "subtelny",
		"room" to "pokój",
		"almost flat" to "prawie płaski",
		"maximum" to "maksymalna",
		"N/a (flat screen)" to "Nie dotyczy (płaski ekran)",
		// --- panel w grze
		"Screen settings" to "Ustawienia ekranu",
		"The game gets no input while this is open" to "Gra nie dostaje teraz sterowania",
		"SCREEN" to "EKRAN",
		"PICTURE & CONTROLLER" to "OBRAZ I PAD",
		"LIVE" to "NA ŻYWO",
		"No readings from this runtime" to "Brak odczytów z tego runtime'u",
		"Size" to "Rozmiar",
		"Distance" to "Odległość",
		"Height" to "Wysokość",
		"Shape" to "Kształt",
		"Curvature" to "Krzywizna",
		"Recenter" to "Wycentruj",
		"In front of you" to "Przed tobą",
		"Flat" to "Płaski",
		"Curved" to "Zakrzywiony",
		"Brightness" to "Jasność",
		"Room" to "Pokój",
		"Hidden" to "Ukryty",
		"Not available" to "Niedostępny",
		"just right" to "w sam raz",
		"can grow" to "może być większy",
		"too large" to "za duży",
		"PS5 only" to "tylko PS5",
		"Need Bluetooth permission" to "Brak zgody BT",
		"Not with classic rumble" to "Nie przy klasycz.",
		"3D off in the launcher" to "3D wył. w menu",
		"Touchpad click" to "Klik touchpada",
		"Map · closes this" to "Mapa · zamyka",
		"Network" to "Sieć",
		"No traffic" to "Brak ruchu",
		"this second" to "w tej sekundzie",
		"Latency" to "Opóźnienie",
		"head to photon" to "ruch głowy → obraz",
		"Frames" to "Klatki",
		"No drops" to "Bez strat",
		"compositor" to "kompozytor",
		"Thermal" to "Temperatura",
		"headroom · steady" to "zapas · stabilnie",
		"headroom · warming up" to "zapas · nagrzewa się",
		"headroom · cooling down" to "zapas · stygnie",
		"Slow" to "Wolny",
		"hold PS 10 s, turn on again" to "przytrzymaj PS 10 s, włącz ponownie",
		"Charging" to "Ładuje",
		"battery" to "bateria",
		"battery · charging" to "bateria · ładuje",
		"battery low" to "słaba bateria",
		"or" to "lub",
		"close  ·" to "zamknij  ·",
		"hold to end the session" to "przytrzymaj: koniec sesji",
		// --- paski w grze
		"opens the settings panel" to "otwiera panel ustawień",
		"on the left Quest controller opens the settings panel" to "na lewym kontrolerze Questa otwiera panel ustawień",
		"The menu button on the left Quest controller works too. Same button closes the panel." to
				"Działa też przycisk menu na lewym kontrolerze Questa. Ten sam przycisk zamyka panel.",
		"Move, resize and sharpen the screen, and click the touchpad." to "Przesuwanie, rozmiar i ostrość ekranu, klik touchpada.",
		"Home mode: your PS5 controller is connected to the console, as it should be." to
				"Tryb domowy: pad PS5 jest połączony z konsolą — tak ma być.",
		"CONTROLLER DISCONNECTED" to "PAD ROZŁĄCZONY",
		"The game keeps running; the console can't tell." to "Gra działa dalej; konsola tego nie wie.",
		"Press the PS button to reconnect. Settings panel: menu button on the left Quest controller." to
				"Naciśnij PS, żeby połączyć. Panel ustawień: przycisk menu na lewym kontrolerze Questa.",
		"CONTROLLER BACK" to "PAD WRÓCIŁ",
		"Connected again." to "Znów połączony.",
		"Checking its speed in the next few seconds." to "Sprawdzam jego szybkość przez kilka sekund.",
		"CONTROLLER RUNNING SLOW" to "PAD DZIAŁA WOLNO",
		"Vibration may lag. Fix it now, with the game running:" to "Wibracje mogą się spóźniać. Napraw to teraz, w trakcie gry:",
		"hold the controller's PS button for 10 s, then press it again." to "przytrzymaj PS na padzie 10 s, potem naciśnij go ponownie.",
		"CONTROLLER AT FULL SPEED" to "PAD ZNÓW SZYBKI",
		"Reconnected fresh; vibration is back in step." to "Połączony na nowo; wibracje znów na czas.",
		"CONTROLLER BATTERY LOW" to "SŁABA BATERIA PADA",
		"A USB-C cable keeps it going while you play." to "Kabel USB-C pozwoli grać dalej.",
		"A · ORIGINAL — no sharpening" to "A · ORYGINAŁ — bez wyostrzania",
		"1:1 size is not available on this headset" to "Rozmiar 1:1 niedostępny w tych goglach",
		// --- legenda Questa (QS8)
		"Quest / PS5 controller  ·  LT RT  Quest triggers  ·  Grip  grip buttons  ·  stick: either one, L / R: press the stick  ·  hold A / ✕: size 1:1 pixel  ·  hold X / □: original / enhanced  ·  hold Y / △: HDR peak  ·  triggers, touchpad: PS5 only" to
				"Kontroler Questa / pad PS5  ·  LT RT  triggery Questa  ·  Grip  przyciski chwytu  ·  gałka: dowolna, L / R: wciśnij gałkę  ·  przytrzymaj A / ✕: rozmiar 1:1  ·  przytrzymaj X / □: oryginał / ulepszony  ·  przytrzymaj Y / △: szczyt HDR  ·  triggery, touchpad: tylko PS5",
		// --- rejestracja (1E-1, QS8)
		"Generate the PIN on the PS5 while logged in to the PSN account you signed in with here (for Home mode: the second account)." to
				"PIN wygeneruj na PS5 na koncie PSN, którym logujesz się tutaj (w Trybie domowym: na drugim koncie).",
		"Sign in to PSN on this headset and the account ID fills in by itself. Use the account that will play here (for Home mode: the second account)." to
				"Zaloguj się do PSN w goglach, a Account ID wpisze się samo. Użyj konta, które będzie tu grać (w Trybie domowym: drugiego).",
		"Sign in with PSN  ›" to "Zaloguj przez PSN  ›",
		"Use a different PSN account  ›" to "Użyj innego konta PSN  ›",
		// --- ekrany wspólne
		"Remote connection" to "Połączenie zdalne",
		"Sign out" to "Wyloguj",
		"Back" to "Wstecz",
		"No account signed in." to "Brak zalogowanego konta.",
		"Sign in from your phone" to "Zaloguj się telefonem",
		"Sign in right here" to "Zaloguj się tutaj",
		"Looking for the consoles on this account…" to "Szukam konsol na tym koncie…",
		"Finishing the sign-in…" to "Kończę logowanie…",
	).associate { (en, pl) -> normalizuj(en) to pl }
}
