package com.termux.app.sms;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.Locale;

public class TermuxSmsAutoCleaner {
    private static final String TAG = "SmsAutoCleaner";
    private static final long HOURS_24_MS = 24L * 60L * 60L * 1000L;

    private static final String[] TARGET_KEYWORDS = {
        "otp", "recharge", "cashback", "win", "won", "lottery", 
        "loan", "credit", "debit", "rs.", "inr", "offer", "discount", "kyc", "alert"
    };

    public static void cleanSpamAndOtps(Context context) {
        if (context == null) return;
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri inboxUri = Uri.parse("content://sms/inbox");
            
            long cutoffTime = System.currentTimeMillis() - HOURS_24_MS;
            String selection = "date < ?";
            String[] selectionArgs = { String.valueOf(cutoffTime) };

            Cursor cursor = resolver.query(inboxUri, new String[]{"_id", "body"}, selection, selectionArgs, null);
            if (cursor != null) {
                try {
                    int idIndex = cursor.getColumnIndex("_id");
                    int bodyIndex = cursor.getColumnIndex("body");

                    while (cursor.moveToNext()) {
                        long smsId = cursor.getLong(idIndex);
                        String body = cursor.getString(bodyIndex);

                        if (body != null) {
                            String lowerBody = body.toLowerCase(Locale.ROOT);
                            boolean isTarget = false;

                            for (String keyword : TARGET_KEYWORDS) {
                                if (lowerBody.contains(keyword)) {
                                    isTarget = true;
                                    break;
                                }
                            }

                            if (isTarget) {
                                resolver.delete(Uri.parse("content://sms/" + smsId), null, null);
                                Log.d(TAG, "Auto-deleted 24h old target SMS ID: " + smsId);
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Auto-clean error: " + e.getMessage());
        }
    }
}
