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
    call.registerCallback(new Call.Callback() {
    @Override
    public void onStateChanged(Call call, int state) {
        super.onStateChanged(call, state);
        
        // Jab call ringing state mein ho (Incoming Call)
        if (state == Call.STATE_RINGING) {
            isVoiceAnswered = false; // Reset state
        } 
        // Jab call active ho jaye (Answer ho chuki hai)
        else if (state == Call.STATE_ACTIVE) {
            // Smart Audio Routing: Agar voice se uthaya hai tabhi speaker check hoga
            if (isVoiceAnswered) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    boolean isBtConnected = audioManager.isBluetoothScoOn() || audioManager.isBluetoothA2dpOn();
                    if (!isBtConnected) {
                        audioManager.setSpeakerphoneOn(true);
                    }
                }
                
            }
        }
    }
});
// Voice pickup ke liye safe trigger
public void triggerVoicePickup() {
    isVoiceAnswered = true;
    TermuxCallManager.answerCall();
}

// Voice cut ke liye safe trigger
public void triggerVoiceCut() {
    TermuxCallManager.endCall();
}


}
