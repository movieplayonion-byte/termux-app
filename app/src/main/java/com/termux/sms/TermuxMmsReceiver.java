package com.termux.app.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TermuxMmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Required by Android OS for Default SMS Handler qualification
    }
}
