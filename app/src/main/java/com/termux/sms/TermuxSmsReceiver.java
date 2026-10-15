package com.termux.app.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class TermuxSmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action) ||
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {

            if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action)) {
                String defaultPkg = Telephony.Sms.getDefaultSmsPackage(context);
                if (defaultPkg != null && defaultPkg.equals(context.getPackageName())) {
                    return; // Duplicate notification roke jab Termux default SMS app ho
                }
            }

            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (messages != null && messages.length > 0) {
                StringBuilder body = new StringBuilder();
                String sender = messages[0].getOriginatingAddress();
                for (SmsMessage msg : messages) {
                    if (msg.getMessageBody() != null) {
                        body.append(msg.getMessageBody());
                    }
                }
                TermuxSmsManager.handleIncomingSms(context, sender, body.toString());
            }
        }
    }
}
