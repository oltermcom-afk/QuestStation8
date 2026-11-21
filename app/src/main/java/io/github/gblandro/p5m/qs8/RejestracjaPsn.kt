// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

import android.app.Activity
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.util.Base64
import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.metallic.chiaki.databinding.ActivityRegistBinding
import io.github.gblandro.p5m.PsnAuth
import io.github.gblandro.p5m.PsnRemoteActivity

/**
 * Plan 1E-1: rejestracja konsoli bez ręcznego szukania PSN Account ID.
 *
 * Ekran rejestracji chiaki-ng (RegistActivity, patch 0058) woła [podepnij].
 * Jeśli w goglach jest zalogowane konto PSN, pole Account ID wypełnia się
 * samo (`PsnAuth.accountId`, Base64 jak oczekuje chiaki). Jeśli nie — w miejscu
 * linku „jak zdobyć Account ID” jest „Sign in with PSN”, które otwiera
 * istniejące logowanie z PsnRemoteActivity (w goglach albo przez telefon) i po
 * udanym logowaniu wraca tu z wypełnionym polem.
 *
 * Do tego: „Broadcast” domyślnie odznaczony (rejestracja z nim kończy się
 * „Invalid argument”, plan „Stan faktyczny” p. 3) i wskazówka, na którym
 * koncie wygenerować PIN.
 */
object RejestracjaPsn
{
	/** Extra dla PsnRemoteActivity: po udanym logowaniu zamknij ekran (wróć do rejestracji). */
	private const val EXTRA_TYLKO_LOGOWANIE = "pl.olterm.queststation8.TYLKO_LOGOWANIE"

	private const val POMOC_PIN = "Generate the PIN on the PS5 while logged in to the PSN account you " +
			"signed in with here (for Home mode: the second account)."

	fun podepnij(activity: AppCompatActivity, binding: ActivityRegistBinding)
	{
		binding.broadcastCheckBox.isChecked = false
		binding.pinTextInputLayout.helperText = POMOC_PIN
		// Link z instrukcją chiaki-ng staje się przyciskiem logowania.
		binding.psnAccountIdHelpURLTextView.autoLinkMask = 0
		binding.psnAccountIdHelpURLTextView.movementMethod = null
		binding.psnAccountIdHelpURLTextView.isClickable = true
		binding.psnAccountIdHelpURLTextView.isFocusable = true

		var wypelnione: String? = null
		activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
			if(event == Lifecycle.Event.ON_RESUME)
				wypelnione = odswiez(activity, binding, wypelnione)
		})
	}

	/** Stan logowania → teksty i pole. Zwraca wartość wpisaną automatycznie (żeby nie nadpisywać ręcznej). */
	private fun odswiez(activity: Activity, binding: ActivityRegistBinding, wypelnione: String?): String?
	{
		val pole = binding.psnIdEditText
		val obecne = pole.text?.toString()?.trim().orEmpty()
		val konto = if(PsnAuth.isLoggedIn(activity)) PsnAuth.accountId(activity) else null
		val link = binding.psnAccountIdHelpURLTextView
		val pomoc = binding.psnAccountIdHelpTextView

		if(konto == null)
		{
			pomoc.text = "Sign in to PSN on this headset and the account ID fills in by itself. " +
					"Use the account that will play here (for Home mode: the second account)."
			link.text = "Sign in with PSN  ›"
			link.setOnClickListener { activity.startActivity(logowanie(activity)) }
			if(wypelnione != null && obecne == wypelnione)
				pole.setText("")
			return null
		}

		val b64 = Base64.encodeToString(konto, Base64.NO_WRAP)
		pomoc.text = "Account ID filled in from the PSN account signed in on this headset " +
				"(account number ${numer(konto)})."
		link.text = "Use a different PSN account  ›"
		link.setOnClickListener {
			// Jak „Sign out” w PsnRemoteActivity: tokeny i ciasteczka Sony, żeby strona nie zalogowała z pamięci.
			PsnAuth.forget(activity)
			CookieManager.getInstance().removeAllCookies(null)
			activity.startActivity(logowanie(activity))
		}
		// PS4 < 7.0 chce Online ID (nazwy), nie Account ID — wtedy pole zostaje użytkownikowi.
		if(binding.ps4VersionLT7RadioButton.isChecked)
			return wypelnione
		if(obecne.isEmpty() || obecne == wypelnione)
		{
			pole.setText(b64)
			pole.error = null
			return b64
		}
		return wypelnione
	}

	private fun logowanie(activity: Activity) =
		Intent(activity, PsnRemoteActivity::class.java).putExtra(EXTRA_TYLKO_LOGOWANIE, true)

	/** Numer konta PSN (user_id) z 8 bajtów little endian — ten sam, który zwraca Sony. */
	private fun numer(konto: ByteArray): String
	{
		var v = 0L
		for(i in 7 downTo 0)
			v = (v shl 8) or (konto[i].toLong() and 0xff)
		return java.lang.Long.toUnsignedString(v)
	}

	/**
	 * Wołane przez PsnRemoteActivity po udanym logowaniu. Jeśli ekran otworzyła
	 * rejestracja, zamyka go (powrót z wypełnionym polem) i zwraca true.
	 */
	fun poLogowaniu(activity: Activity): Boolean
	{
		if(!activity.intent.getBooleanExtra(EXTRA_TYLKO_LOGOWANIE, false))
			return false
		activity.finish()
		return true
	}
}
