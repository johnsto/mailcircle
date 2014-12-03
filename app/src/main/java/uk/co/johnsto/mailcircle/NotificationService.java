package uk.co.johnsto.mailcircle;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;

import com.google.android.gm.contentprovider.GmailContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class NotificationService extends Service implements Consts {
    public final static String
            ACTION_INIT = "uk.co.johnsto.mailcircle.ACTION_INIT",
            ACTION_CHECK = "uk.co.johnsto.mailcircle.ACTION_CHECK";
    private static final int NOTIFICATION_ID = 2222;
    private ContentObserver mObserver;

    public NotificationService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        observe();
        check();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Checks for any unread emails and hides/shows a notification as appropriate.
     */
    public void check() {
        final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Check if user actually wants a notification
        boolean enabled = prefs.getBoolean("enable_notification", true);
        if (!enabled) {
            // Notification isn't enabled right now
            nm.cancel(NOTIFICATION_ID);
            return;
        }

        final Settings settings = new Settings(this);
        String[] accountNames = settings.getAccountNames();

        if (accountNames == null || accountNames.length == 0) {
            // No accounts, therefore definitely no email
            nm.cancel(NOTIFICATION_ID);
            return;
        }

        final Resources res = getResources();
        int notifWidth = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
        int notifHeight = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
        int defaultColor = res.getColor(R.color.primary);

        Bitmap bitmap = Bitmap.createBitmap(notifWidth, notifHeight, Bitmap.Config.ARGB_8888);
        ArrayList<Pair<String, Integer>> counts = new ArrayList<Pair<String, Integer>>();

        // Check unread count for each account
        int totalUnread = 0;
        for (String accountName : accountNames) {
            // Get unread count for the selected account label
            String label = settings.getAccountLabel(accountName,
                    GmailContract.Labels.LabelCanonicalNames.CANONICAL_NAME_ALL_MAIL);
            int unread = getUnreadCount(accountName, label);
            if (unread > 0) {
                totalUnread += unread;
                counts.add(new Pair<String, Integer>(accountName, unread));
            }
        }

        if (totalUnread == 0) {
            // Remove notification if there's no unread email in an account
            nm.cancel(NOTIFICATION_ID);
            return;
        }

        // Sort accounts by unread #, greatest first
        Collections.sort(counts, new Comparator<Pair<String, Integer>>() {
            @Override
            public int compare(Pair<String, Integer> left, Pair<String, Integer> right) {
                return right.second - left.second;
            }
        });

        // Populate icon and notification content
        NotificationIconFactory factory = new NotificationIconFactory(res, bitmap);
        StringBuilder contentInfo = new StringBuilder();
        String contentSep = "";
        for (Pair<String, Integer> count : counts) {
            final String accountName = count.first;
            final int unread = count.second;
            final int color = settings.getAccountColor(accountName, defaultColor);
            final String alias = settings.getAccountAlias(accountName);
            factory.addSlice(count.second, color);
            contentInfo.append(contentSep);
            contentInfo.append(String.format("%s (%,d)", alias, unread));
            contentSep = ", ";
        }

        // Get details about account with the most unread emails
        final String majorAccount = counts.get(0).first;
        final int majorColor = settings.getAccountColor(majorAccount, defaultColor);
        final String majorLabel = settings.getAccountLabel(majorAccount,
                GmailContract.Labels.LabelCanonicalNames.CANONICAL_NAME_ALL_MAIL);

        // Make intent to GMail app that opens the account with the highest number of unread emails
        // FIXME: this probably won't work on some devices
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.gm");
        intent.putExtra("account", majorAccount);
        intent.putExtra("label", majorLabel);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Set notification color to that of the account with the highest number of unread emails
        factory.setColor(majorColor);
        factory.setNumber(totalUnread);

        // Set notification style
        String styleName = prefs.getString("notification_style", NotificationIconFactory.DEFAULT_STYLE.name);
        factory.setStyle(styleName);

        //String pluralString = getResources().getQuantityString(R.plurals.unread_emails, totalUnread, totalUnread);

        // Create notification
        Notification notif = new Notification.Builder(this)
                .setNumber(totalUnread)
                .setSmallIcon(R.drawable.ic_notif_mail, totalUnread)
                .setLargeIcon(factory.build())
                .setColor(majorColor)
                .setContentIntent(pendingIntent)
                .setContentTitle(res.getString(R.string.notif_title))
                .setContentText(contentInfo.toString())
                .build();

        // Display the notification
        nm.notify(NOTIFICATION_ID, notif);
    }


    /**
     * Start watching content for changes.
     */
    private void observe() {
        final Settings settings = new Settings(this);
        final ContentResolver resolver = getContentResolver();
        final String[] accounts = settings.getAccountNames();

        if (accounts == null) {
            Log.w(TAG, "No accounts defined.");
            return;
        }

        if (mObserver != null) {
            getContentResolver().unregisterContentObserver(mObserver);
        }

        mObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                onChange(selfChange, null);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                check();
            }
        };

        // Register observer for each account and label the user has selected.
        for (String account : accounts) {
            Uri uri = GmailContract.Labels.getLabelsUri(account);
            resolver.registerContentObserver(uri, true, mObserver);
        }
    }

    /**
     * Listens for broadcasts that suggests the service needs to be (re)started.
     */
    public static class InitReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Intent serviceIntent = new Intent(context, NotificationService.class);
            serviceIntent.setAction(ACTION_INIT);
            context.startService(serviceIntent);
        }
    }

    /**
     * Listens for broadcasts that suggests the email count may have changed.
     */
    public static class MailReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Intent serviceIntent = new Intent(context, NotificationService.class);
            serviceIntent.setAction(ACTION_CHECK);
            context.startService(serviceIntent);
        }
    }

    /**
     * Returns the number of unread emails on the given account and label.
     *
     * @param accountName Name of the account to read from
     * @param label       Canonical label name
     * @return Number of unread emails for the account label
     */
    public int getUnreadCount(String accountName, String label) {
        Cursor cursor = getContentResolver().query(
                GmailContract.Labels.getLabelsUri(accountName),
                UnreadQuery.PROJECTION, null, null, null
        );
        try {
            if (cursor == null || cursor.isAfterLast()) {
                return 0;
            }

            int unread = 0;
            while (cursor.moveToNext()) {
                String canonicalName = cursor.getString(UnreadQuery.CANONICAL_NAME);
                int unreadInLabel = cursor.getInt(UnreadQuery.NUM_UNREAD_CONVERSATIONS);
                if (label.equals(canonicalName)) {
                    unread = Math.max(unread, unreadInLabel);
                }
            }
            return unread;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private interface UnreadQuery {
        String[] PROJECTION = {
                GmailContract.Labels.NUM_UNREAD_CONVERSATIONS,
                GmailContract.Labels.CANONICAL_NAME,
        };
        int NUM_UNREAD_CONVERSATIONS = 0;
        int CANONICAL_NAME = 1;
    }

}
