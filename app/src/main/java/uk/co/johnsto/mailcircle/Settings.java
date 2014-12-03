package uk.co.johnsto.mailcircle;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class to set/get user preferences.
 */
public class Settings {
    public static final String KEY_ACCOUNTS = "accounts";
    public static final String KEY_ACCOUNT_ALIAS = "alias";
    public static final String KEY_ACCOUNT_COLOR = "color";
    public static final String KEY_ACCOUNT_LABEL = "label";

    private final SharedPreferences mPreferences;

    public Settings(Context context) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Gets the names of the accounts that the user has enabled.
     *
     * @return An array of enabled account names. Empty if no accounts have been chosen.
     */
    public String[] getAccountNames() {
        if (!mPreferences.contains(KEY_ACCOUNTS)) {
            return new String[0];
        }
        Set<String> accountNames = mPreferences.getStringSet(KEY_ACCOUNTS, new HashSet<String>());
        return accountNames.toArray(new String[accountNames.size()]);
    }

    /**
     * Determines if the named account is enabled.
     *
     * @param accountName The account name, e.g. example@gmail.com
     * @return True if the account is enabled.
     */
    public boolean isEnabled(String accountName) {
        Set<String> accounts = mPreferences.getStringSet(KEY_ACCOUNTS, new HashSet<String>());
        return accounts.contains(accountName);
    }

    /**
     * Sets whether to display an unread count for the specified inbox.
     *
     * @param accountName The account name, e.g. example@gmail.com
     * @param enabled     Whether to watch this account or not.
     */
    public void setEnabled(String accountName, boolean enabled) {
        Set<String> accounts = new HashSet<String>(mPreferences.getStringSet(KEY_ACCOUNTS, new HashSet<String>()));

        if (enabled) {
            accounts.add(accountName);
        } else {
            accounts.remove(accountName);
        }

        mPreferences.edit()
                .putStringSet(KEY_ACCOUNTS, accounts)
                .apply();
    }

    /**
     * Gets the color to use when displaying notifications for this account.
     *
     * @param accountName The account name, e.g. example@gmail.com
     * @return The color to use.
     */
    public int getAccountColor(String accountName, int defValue) {
        try {
            return mPreferences.getInt(KEY_ACCOUNT_COLOR + ":" + accountName, defValue);
        } catch (ClassCastException ex) {
            setAccountColor(accountName, defValue);
            return defValue;
        }
    }

    /**
     * Sets the color to use when displaying notifications for this account.
     *
     * @param accountName The account name, e.g. example@gmail.com
     * @param color       The color to use.
     */
    public void setAccountColor(String accountName, int color) {
        mPreferences.edit()
                .putInt(KEY_ACCOUNT_COLOR + ":" + accountName, color)
                .apply();
    }

    /**
     * Gets the canonical label name to check for the named account.
     *
     * @param accountName The account name, e.g. example@gmail.com
     * @param defValue    The default canonical label value to return if not set.
     * @return Cacnonical label name
     */
    public String getAccountLabel(String accountName, String defValue) {
        return mPreferences.getString(KEY_ACCOUNT_LABEL + ":" + accountName, defValue);
    }

    /**
     * Sets the canonical label name to check for the named account.
     *
     * @param accountName The account name, e.g. example@gmail.com
     * @param canonical   The canonical label name.
     */
    public void setAccountLabel(String accountName, String canonical) {
        mPreferences.edit()
                .putString(KEY_ACCOUNT_LABEL + ":" + accountName, canonical)
                .apply();
    }

    /**
     * Get the alias to use for the account.
     *
     * @param accountName The account name, e.g. example@gmail.com
     * @return Account alias
     */
    public String getAccountAlias(String accountName) {
        return mPreferences.getString(KEY_ACCOUNT_ALIAS + ":" + accountName, accountName);
    }

    /**
     * Set the alias to use for the account.
     *
     * @param accountName The account name, e.g. example@gmail.com
     * @param alias       Account alias
     */
    public void setAccountAlias(String accountName, String alias) {
        mPreferences.edit()
                .putString(KEY_ACCOUNT_ALIAS + ":" + accountName, alias)
                .apply();
    }
}
