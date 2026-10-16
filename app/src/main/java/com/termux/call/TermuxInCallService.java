package com.termux.app.call;

import android.telecom.Call;
import android.telecom.InCallService;

public class TermuxInCallService extends InCallService {
    @Override
    public void onCreate() {
        super.onCreate();
        TermuxCallManager.inCallService = this;
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        TermuxCallManager.inCallService = this;
        TermuxCallManager.onCallAdded(this, call);
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        TermuxCallManager.onCallRemoved(this, call);
    }

    @Override
    public void onDestroy() {
        if (TermuxCallManager.inCallService == this) {
            TermuxCallManager.inCallService = null;
        }
        super.onDestroy();
    }
}
