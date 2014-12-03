package uk.co.johnsto.mailcircle;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.WebView;

import com.google.android.gm.contentprovider.GmailContract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import uk.co.johnsto.mailcircle.widgets.ColorListPreference;


public class SettingsActivity extends PreferenceActivity implements Consts, SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String ACTION_PREFERENCE_CHANGED = "uk.co.johnsto.mailcircle.PREFERENCE_CHANGED";

    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar bar = getActionBar();
        bar.setDisplayShowHomeEnabled(true);
        bar.setDisplayUseLogoEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in action bar clicked; go home
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    @Override
    protected void onStop() {
        // Update notification immediately with new settings
        final Intent serviceIntent = new Intent(this, NotificationService.class);
        serviceIntent.setAction(NotificationService.ACTION_INIT);
        startService(serviceIntent);
        super.onStop();
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        Header accounts = new Header();
        accounts.fragment = AccountsPreferenceFragment.class.getName();
        accounts.title = getString(R.string.pref_accounts);
        target.add(accounts);

        Header notif = new Header();
        notif.fragment = NotificationPreferenceFragment.class.getName();
        notif.title = getString(R.string.pref_notification);
        target.add(notif);

        Header about = new Header();
        about.fragment = AboutPreferenceFragment.class.getName();
        about.title = getString(R.string.pref_about);
        target.add(about);

        if(BuildConfig.DEBUG) {
            Header debug = new Header();
            debug.fragment = DebugPreferenceFragment.class.getName();
            debug.title = "Debug";
            target.add(debug);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Send a pref change broadcast each time a value changes so other components can respond
        final Intent i = new Intent(ACTION_PREFERENCE_CHANGED);
        i.putExtra("key", key);
        Object value = sharedPreferences.getAll().get(key);
        if (value instanceof String) {
            i.putExtra("stringValue", value.toString());
        } else if (value instanceof Boolean) {
            i.putExtra("boolValue", (Boolean) value);
        } else if (value instanceof Integer) {
            i.putExtra("intValue", (Integer) value);
        }
        sendBroadcast(i);
    }

    public static class SubPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            ActionBar bar = getActivity().getActionBar();
            bar.setDisplayShowHomeEnabled(false);
            bar.setDisplayUseLogoEnabled(false);
            bar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class AccountsPreferenceFragment extends SubPreferenceFragment implements LoaderManager.LoaderCallbacks<Account[]> {
        private Settings mSettings;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mSettings = new Settings(getActivity());
            addPreferencesFromResource(R.xml.pref_accounts);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            getLoaderManager().initLoader(0, null, this);
        }

        /**
         * Populates the screen with the given accounts
         *
         * @param accounts An array of accounts to show
         */
        private void setAccounts(Account[] accounts) {
            PreferenceScreen screen = getPreferenceScreen();
            screen.removeAll();

            for (Account account : accounts) {
                addAccountPreferences(screen, account.name);
            }

            if (accounts.length == 0) {
                final Context context = getActivity();
                new AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_no_accounts_title)
                        .setMessage(R.string.dialog_no_accounts_message)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Intent intent = new Intent(context, SettingsActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }

        /**
         * Adds a preference group for the specifeid account.
         *
         * @param screen      Preference screen to add to.
         * @param accountName The account name, e.g. example@gmail.com
         */
        private void addAccountPreferences(PreferenceScreen screen, final String accountName) {
            final Context context = getActivity();

            PreferenceCategory cat = new PreferenceCategory(context);
            cat.setTitle(accountName);
            screen.addPreference(cat);

            // Add "Enabled [x]"
            final CheckBoxPreference prefEnabled = new CheckBoxPreference(context);
            prefEnabled.setKey(accountName);
            prefEnabled.setTitle(R.string.pref_enable);
            prefEnabled.setChecked(mSettings.isEnabled(accountName));
            prefEnabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean checked = (Boolean) o;
                    mSettings.setEnabled(accountName, checked);
                    return true;
                }
            });
            screen.addPreference(prefEnabled);

            // Add "Name..."
            final EditTextPreference prefAlias = new EditTextPreference(context);
            prefAlias.setTitle(R.string.pref_name);
            prefAlias.setKey(Settings.KEY_ACCOUNT_ALIAS + ":" + accountName);
            prefAlias.setDefaultValue(accountName);
            prefAlias.setSummary(mSettings.getAccountAlias(accountName));
            bindPreferenceSummaryToValue(prefAlias);
            screen.addPreference(prefAlias);
            prefAlias.setDependency(accountName);

            // Add "Label..."
            final ListPreference prefLabel = makeLabelPref(context, accountName);
            prefLabel.setKey(accountName + "_label");
            String label = mSettings.getAccountLabel(accountName, GmailContract.Labels.LabelCanonicalNames.CANONICAL_NAME_ALL_MAIL);
            prefLabel.setValue(label);
            prefLabel.setSummary(getLabelName(context, accountName, label));
            bindPreferenceSummaryToValue(prefLabel);
            screen.addPreference(prefLabel);
            prefLabel.setDependency(accountName);

            // Add "Color..."
            final ColorListPreference prefColor = new ColorListPreference(context);
            prefColor.setTitle(R.string.pref_color);

            final int defaultColor = getResources().getColor(R.color.primary);
            final int color = mSettings.getAccountColor(accountName, defaultColor);
            String hexColor = String.format("#%08x", color).toUpperCase();
            prefColor.setEntries(R.array.pref_colors);
            prefColor.setEntryValues(R.array.pref_colors_values);
            prefColor.setValue(hexColor);
            prefColor.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    int color = Color.parseColor((String) o);
                    mSettings.setAccountColor(accountName, color);
                    return true;
                }
            });
            screen.addPreference(prefColor);
            prefColor.setDependency(accountName);

        }

        /**
         * Create a ListPreference containing all the labels for the given account.
         *
         * @param context     Context
         * @param accountName Account name
         * @return ListPreference containing account labels
         */
        private ListPreference makeLabelPref(Context context, String accountName) {
            final ListPreference prefLabel = new ListPreference(context);
            prefLabel.setTitle("Label");

            ArrayList<CharSequence> names = new ArrayList<CharSequence>();
            ArrayList<CharSequence> values = new ArrayList<CharSequence>();

            // Query labels
            Cursor cursor = context.getContentResolver().query(
                    GmailContract.Labels.getLabelsUri(accountName),
                    LabelQuery.PROJECTION, null, null, null
            );
            while (cursor.moveToNext()) {
                String name = cursor.getString(LabelQuery.NAME);
                String canonical = cursor.getString(LabelQuery.CANONICAL_NAME);
                names.add(name);
                values.add(canonical);
            }
            cursor.close();

            prefLabel.setEntries(names.toArray(new CharSequence[names.size()]));
            prefLabel.setEntryValues(values.toArray(new CharSequence[values.size()]));

            return prefLabel;
        }

        /**
         * Gets the human-readable label name for the given account and canonical label.
         *
         * @param context     Context
         * @param accountName The account name, e.g. example@gmail.com
         * @param label       The canonical label name.
         * @return The human-readable label name.
         */
        private String getLabelName(Context context, String accountName, String label) {
            // FIXME: change this to a WHERE query if supported.
            Cursor cursor = context.getContentResolver().query(
                    GmailContract.Labels.getLabelsUri(accountName),
                    LabelQuery.PROJECTION, null, null, null
            );
            // Iterate through labels until the label is found.
            while (cursor.moveToNext()) {
                String name = cursor.getString(LabelQuery.NAME);
                String canonical = cursor.getString(LabelQuery.CANONICAL_NAME);
                if (canonical.equals(label)) {
                    cursor.close();
                    return name;
                }
            }
            cursor.close();
            return label;
        }

        @Override
        public Loader<Account[]> onCreateLoader(int id, Bundle bundle) {
            return new AccountLoader(getActivity());
        }

        @Override
        public void onLoadFinished(Loader<Account[]> loader, Account[] accounts) {
            setAccounts(accounts);
        }

        @Override
        public void onLoaderReset(Loader<Account[]> loader) {
        }

        private interface LabelQuery {
            String[] PROJECTION = {
                    GmailContract.Labels.CANONICAL_NAME,
                    GmailContract.Labels.NAME,
            };
            int CANONICAL_NAME = 0;
            int NAME = 1;
        }
    }

    public static class AboutPreferenceFragment extends SubPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.pref_about);

            findPreference("version").setSummary(BuildConfig.VERSION_NAME);
            findPreference("website").setOnPreferenceClickListener(sBindPreferenceSummaryToBrowserListener);
            findPreference("source").setOnPreferenceClickListener(sBindPreferenceSummaryToBrowserListener);

            findPreference("license").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference pref) {
                    final Context context = pref.getContext();

                    WebView webView = new WebView(context);
                    webView.loadUrl("file:///android_asset/gpl-2.0-standalone.html");

                    new AlertDialog.Builder(context)
                            .setView(webView)
                            .setNeutralButton(android.R.string.ok, null)
                            .show();

                    return true;
                }
            });
        }
    }

    public static class NotificationPreferenceFragment extends SubPreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.pref_notification);

            bindPreferenceSummaryToValue(findPreference("notification_style"));
        }
    }

    public static class DebugPreferenceFragment extends SubPreferenceFragment {
        private NotificationManager mNM;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_debug);

            mNM = (NotificationManager)getActivity().getSystemService(NOTIFICATION_SERVICE);

            findPreference("test_notification").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    final Resources res = getResources();

                    int notifWidth = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
                    int notifHeight = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);

                    Bitmap bitmap = Bitmap.createBitmap(notifWidth, notifHeight, Bitmap.Config.ARGB_8888);

                    NotificationIconFactory factory = new NotificationIconFactory(res, bitmap)
                            .setColor(res.getColor(R.color.material_red))
                            .setNumber(9)
                            .addSlice(4, res.getColor(R.color.material_red))
                            .addSlice(3, res.getColor(R.color.material_green))
                            .addSlice(2, res.getColor(R.color.material_blue))
                            .setStyle(prefs.getString("notification_style", NotificationIconFactory.DEFAULT_STYLE.name));

                    // Create notification
                    Notification notif = new Notification.Builder(getActivity())
                            .setNumber(9)
                            .setSmallIcon(R.drawable.ic_notif_mail, 9)
                            .setLargeIcon(factory.build())
                            .setColor(res.getColor(R.color.material_red))
                            .setContentTitle(res.getString(R.string.notif_title))
                            .setContentText("dave (4), work (3), webmaster (2)")
                            .build();

                    // Display the notification
                    mNM.notify(2, notif);

                    return true;
                }
            });
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mNM.cancel(2);
        }
    }

    private static void bindPreferenceSummaryToValue(Preference pref) {
        pref.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(pref,
                PreferenceManager
                        .getDefaultSharedPreferences(pref.getContext())
                        .getString(pref.getKey(), "")
        );
    }

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference pref, Object value) {
            String stringValue = value.toString();

            if (pref instanceof ListPreference) {
                ListPreference listPref = (ListPreference) pref;
                int index = listPref.findIndexOfValue(stringValue);
                pref.setSummary(index >= 0 ? listPref.getEntries()[index] : null);
            } else {
                pref.setSummary(stringValue);
            }

            return true;
        }
    };

    private static Preference.OnPreferenceClickListener sBindPreferenceSummaryToBrowserListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference pref) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(pref.getSummary().toString()));
            pref.getContext().startActivity(intent);
            return true;
        }
    };

    public static class AccountLoader extends Loader<Account[]> {

        private final AccountManager mAccountManager;

        public AccountLoader(Context context) {
            super(context);
            mAccountManager = AccountManager.get(context);
        }

        @Override
        public void onStartLoading() {
            final String ACCOUNT_TYPE_GOOGLE = "com.google";
            final String[] FEATURES_MAIL = {
                    "service_mail"
            };

            // FIXME: show a loader while this operation runs
            mAccountManager.getAccountsByTypeAndFeatures(ACCOUNT_TYPE_GOOGLE, FEATURES_MAIL,
                    new AccountManagerCallback() {
                        @Override
                        public void run(AccountManagerFuture future) {
                            Account[] accounts;
                            try {
                                accounts = (Account[]) future.getResult();
                                if (accounts == null) {
                                    Log.e(TAG, "No accounts!");
                                    return;
                                }
                                deliverResult(accounts);
                            } catch (OperationCanceledException oce) {
                                Log.e(TAG, "Couldn't get accounts", oce);
                                deliverResult(null);
                            } catch (IOException ioe) {
                                Log.e(TAG, "Couldn't get accounts", ioe);
                                deliverResult(null);
                            } catch (AuthenticatorException ae) {
                                Log.e(TAG, "Couldn't get accounts", ae);
                                deliverResult(null);
                            }
                        }
                    }, null /* handler */);
        }
    }
}
