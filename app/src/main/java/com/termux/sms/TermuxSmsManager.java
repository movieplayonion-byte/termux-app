package com.termux.app.sms;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TermuxSmsManager {
    private static final String PREFS_NAME = "termux_sms_prefs";
    private static final String KEY_DEFAULT_SIM = "default_sim";
    private static final String CHANNEL_ID = "termux_sms_channel";

    public static void setDefaultSim(Context context, int simSlot) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_DEFAULT_SIM, simSlot).apply();
        showToast(context, "Default SIM set to SIM " + simSlot);
    }

    public static int getDefaultSim(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DEFAULT_SIM, 1);
    }

    public static void sendSms(Context context, String number, String text, int simChoice) {
        if (number == null || number.trim().isEmpty() || text == null || text.trim().isEmpty()) {
            showToast(context, "Error: Number or text empty");
            return;
        }
        String cleanNumber = number.trim();
        int targetSim = (simChoice == 1 || simChoice == 2) ? simChoice : getDefaultSim(context);

        try {
            SmsManager smsManager = getSmsManagerForSim(context, targetSim - 1);
            ArrayList<String> parts = smsManager.divideMessage(text);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(cleanNumber, null, text, null, null);
            }
            showToast(context, "SMS Sent via SIM " + targetSim + " to " + cleanNumber);
        } catch (Exception e) {
            showToast(context, "SMS Failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private static SmsManager getSmsManagerForSim(Context context, int slotIndex) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager subManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (subManager != null && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    List<SubscriptionInfo> subList = subManager.getActiveSubscriptionInfoList();
                    if (subList != null) {
                        for (SubscriptionInfo info : subList) {
                            if (info.getSimSlotIndex() == slotIndex) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    return context.getSystemService(SmsManager.class).createForSubscriptionId(info.getSubscriptionId());
                                } else {
                                    return SmsManager.getSmsManagerForSubscriptionId(info.getSubscriptionId());
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return SmsManager.getDefault();
    }

    public static void handleIncomingSms(Context context, String sender, String body) {
        String otp = extractOtp(body);
        if (otp != null) {
            copyToClipboard(context, otp);
            showToast(context, "OTP Copied: " + otp);
        }
        String senderName = getContactName(context, sender);
        String displayTitle = senderName != null ? senderName + " (" + sender + ")" : sender;
        showReplyNotification(context, sender, displayTitle, body, otp);
    }

    private static String extractOtp(String body) {
        if (body == null) return null;
        String lower = body.toLowerCase();
        if (lower.contains("otp") || lower.contains("code") || lower.contains("verification") ||
            lower.contains("password") || lower.contains("pin") || lower.contains("passcode")) {
            Pattern pattern = Pattern.compile("\\b(\\d{4,8})\\b");
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static void copyToClipboard(final Context context, final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText("OTP", text);
                        clipboard.setPrimaryClip(clip);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static String getContactName(Context context, String phoneNumber) {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        return cursor.getString(0);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static void showReplyNotification(Context context, String rawNumber, String title, String body, String otp) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Termux SMS", NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }

        int notificationId = (rawNumber != null) ? rawNumber.hashCode() : (int) System.currentTimeMillis();

        Intent replyIntent = new Intent(context, TermuxCommandReceiver.class);
        replyIntent.setAction("com.termux.action.INLINE_REPLY");
        replyIntent.putExtra("sender", rawNumber);
        replyIntent.putExtra("notif_id", notificationId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(context, notificationId, replyIntent, flags);

        RemoteInput remoteInput = new RemoteInput.Builder("key_text_reply")
                .setLabel("Type reply...")
                .build();

        android.app.Notification.Action replyAction = new android.app.Notification.Action.Builder(
                android.R.drawable.ic_menu_send, "Reply", replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build();

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context);
            builder.setPriority(android.app.Notification.PRIORITY_HIGH);
        }

        String contentText = (otp != null) ? "[OTP: " + otp + " Copied] " + body : body;

        builder.setSmallIcon(android.R.drawable.sym_action_email)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(contentText))
                .setAutoCancel(true)
                .addAction(replyAction);

        nm.notify(notificationId, builder.build());
    }

    public static void showToast(final Context context, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static Intent getDefaultSmsRoleIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
            }
        }
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
        return intent;
    }
}
