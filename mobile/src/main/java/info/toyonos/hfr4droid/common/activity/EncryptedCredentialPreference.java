package info.toyonos.hfr4droid.common.activity;

import android.app.Activity;
import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

import info.toyonos.hfr4droid.common.HFR4droidApplication;

/**
 * EditTextPreference dont la valeur est stockée dans EncryptedSharedPreferences
 * au lieu des SharedPreferences classiques en clair.
 */
public class EncryptedCredentialPreference extends EditTextPreference
{
	public EncryptedCredentialPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	/**
	 * Ne pas persister dans les SharedPreferences standards : on gère
	 * nous-mêmes la persistance chiffrée.
	 */
	@Override
	public boolean shouldPersist()
	{
		return false;
	}

	/**
	 * Lors de l'initialisation, lire depuis le stockage chiffré (avec migration
	 * automatique si une valeur en clair existe encore dans les prefs standards).
	 */
	@Override
	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue)
	{
		String value = getEncryptedValue();
		if (value == null)
		{
			value = (defaultValue instanceof String) ? (String) defaultValue : "";
		}
		// setText() met à jour mText (affiché dans le dialog) sans persister
		// puisque shouldPersist() = false.
		setText(value);
	}

	/**
	 * Quand l'utilisateur valide le dialog, sauvegarder dans le stockage chiffré.
	 */
	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		// Ne pas appeler super pour éviter toute tentative de persistance classique.
		if (positiveResult)
		{
			String value = getEditText().getText().toString();
			if (callChangeListener(value))
			{
				setText(value);
				HFR4droidApplication app = getApp();
				if (app != null)
				{
					app.setEncryptedCredential(getKey(), value);
				}
			}
		}
	}

	private String getEncryptedValue()
	{
		HFR4droidApplication app = getApp();
		return app != null ? app.getEncryptedCredential(getKey()) : null;
	}

	private HFR4droidApplication getApp()
	{
		Context ctx = getContext();
		if (ctx instanceof Activity)
		{
			return (HFR4droidApplication) ((Activity) ctx).getApplication();
		}
		return null;
	}
}
