package com.termux.app.sms;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class TermuxCommandReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        if ("com.termux.action.CLI_SMS".equals(action)) {
            String cmd = intent.getStringExtra("cmd");
            if ("set_sim".equals(cmd)) {
                int sim = intent.getIntExtra("sim", 1);
                TermuxSmsManager.setDefaultSim(context, sim);
            } else if ("send".equals(cmd)) {
                String number = intent.getStringExtra("number");
                String text = intent.getStringExtra("text");
                int sim = intent.getIntExtra("sim", 0);
                TermuxSmsManager.sendSms(context, number, text, sim);
            } else if ("popup".equals(cmd)) {
                String text = intent.getStringExtra("text");
                Intent popupIntent = new Intent(context, TermuxSmsPopupActivity.class);
                popupIntent.putExtra(Intent.EXTRA_TEXT, text);
                popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(popupIntent);
            }
        } else if ("com.termux.action.INLINE_REPLY".equals(action)) {
            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput != null) {
                CharSequence replyCharSeq = remoteInput.getCharSequence("key_text_reply");
                String sender = intent.getStringExtra("sender");
                int notifId = intent.getIntExtra("notif_id", 0);
                if (replyCharSeq != null && sender != null) {
                    TermuxSmsManager.sendSms(context, sender, replyCharSeq.toString(), 0);
                    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) {
                        nm.cancel(notifId);
                    }
                }
            }
        }
    }
}
