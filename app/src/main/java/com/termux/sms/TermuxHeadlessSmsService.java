package com.termux.app.sms;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

public class TermuxHeadlessSmsService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "android.intent.action.RESPOND_VIA_MESSAGE".equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            Uri uri = intent.getData();
            if (extras != null && uri != null) {
                String text = extras.getString(Intent.EXTRA_TEXT);
                String recipients = uri.getSchemeSpecificPart();
                if (text != null && recipients != null) {
                    TermuxSmsManager.sendSms(this, recipients, text, 0);
                }
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}
