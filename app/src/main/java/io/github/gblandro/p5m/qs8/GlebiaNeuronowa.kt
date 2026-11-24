// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.content.Context
import android.util.Log
import io.github.gblandro.p5m.ProfundidadeNeural
import java.util.concurrent.Executors

/**
 * Włączanie i wyłączanie sieci głębi w trakcie sesji 3D (strona 2 panelu).
 *
 * P5M uruchamia [ProfundidadeNeural] raz na sesję i zatrzymuje w onDestroy.
 * Przełącznik w grze dokłada dwa wyścigi, które tu zamykamy:
 * - start po zakończeniu sesji: [zamknij] (przed zatrzymaniem sieci w onDestroy)
 *   gwarantuje, że żaden start już nie nastąpi;
 * - dwie sieci naraz: stop czeka na wątek najwyżej 1 s, a wczytanie modelu trwa
 *   dłużej, więc start czeka, aż stary wątek naprawdę zniknie.
 *
 * Wykonuje się po kolei w jednym wątku i liczy się ostatni wybór, nie każde
 * naciśnięcie.
 */
class GlebiaNeuronowa(context: Context, dzialaTeraz: Boolean)
{
	private val app = context.applicationContext
	private val watek = Executors.newSingleThreadExecutor { Thread(it, "qs8-neural-toggle").apply { isDaemon = true } }
	private val blokada = Any()

	@Volatile private var chce = dzialaTeraz
	@Volatile private var zamkniety = false
	/** Co naprawdę jest uruchomione; tylko z [watek]. */
	private var uruchomiona = dzialaTeraz

	fun ustaw(wlaczona: Boolean)
	{
		chce = wlaczona
		synchronized(blokada)
		{
			if(!zamkniety)
				watek.execute { wykonaj() }
		}
	}

	/** Koniec sesji: po powrocie stąd sieć już się nie uruchomi. Wołać przed ProfundidadeNeural.stop(). */
	fun zamknij()
	{
		synchronized(blokada)
		{
			zamkniety = true
			watek.shutdown()
		}
	}

	private fun wykonaj()
	{
		try
		{
			if(zamkniety || chce == uruchomiona)
				return
			if(!chce)
			{
				ProfundidadeNeural.stop()
				uruchomiona = false
				return
			}
			while(staryWatekZyje())
			{
				if(zamkniety || !chce)
					return
				Thread.sleep(100)
			}
			synchronized(blokada)
			{
				if(zamkniety)
					return
				ProfundidadeNeural.start(app)
				uruchomiona = true
			}
		}
		catch(e: Throwable) { Log.w("P5MVR", "QS8: neural depth switch failed", e) }
	}

	/** enumerate, a nie getAllStackTraces: tamto zatrzymuje na chwilę wątki, a gra trwa. */
	private fun staryWatekZyje(): Boolean
	{
		val watki = arrayOfNulls<Thread>(Thread.activeCount() * 2 + 16)
		val ile = Thread.enumerate(watki)
		return (0 until ile).any { watki[it]?.let { w -> w.name == WATEK_P5M && w.isAlive } == true }
	}

	private companion object
	{
		/** Nazwa wątku z ProfundidadeNeural.start. */
		const val WATEK_P5M = "p5m-neural-depth"
	}
}
