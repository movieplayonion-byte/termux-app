package com.termux.app.call;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TermuxCallManager {
    private static final String PREFS_NAME = "termux_call_prefs";
    private static final String KEY_DEFAULT_SIM = "default_call_sim";

    public static Call currentCall = null;
    public static TermuxInCallService inCallService = null;
    private static Ringtone currentRingtone = null;
    private static boolean hasVibratedConnected = false;
    private static MediaRecorder stealthRecorder = null;
    private static boolean isRecordingActive = false;

    public interface CallStateListener {
        void onCallStateChanged(Call call, int state);
    }

    public static CallStateListener listener = null;

    public static void setDefaultSim(Context context, int simSlot) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_DEFAULT_SIM, simSlot).apply();
        showToast(context, "Default Call SIM set to SIM " + simSlot);
    }

    public static int getDefaultSim(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DEFAULT_SIM, 1);
    }

    public static void startOutgoingCall(Context context, String number, int simChoice) {
        if (number == null || number.trim().isEmpty()) {
            showToast(context, "Error: Number empty");
            return;
        }
        String clean = number.trim();
        int targetSim = (simChoice == 1 || simChoice == 2) ? simChoice : getDefaultSim(context);

        try {
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            Uri uri = Uri.fromParts("tel", clean, null);
            Bundle extras = new Bundle();

            if (tm != null && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                List<PhoneAccountHandle> accounts = tm.getCallCapablePhoneAccounts();
                if (accounts != null && accounts.size() >= targetSim) {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts.get(targetSim - 1));
                }
            }

            if (tm != null && context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                tm.placeCall(uri, extras);
                showToast(context, "Dialing " + clean + " [SIM " + targetSim + "]");
            } else {
                Intent callIntent = new Intent(Intent.ACTION_CALL, uri);
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(callIntent);
            }
        } catch (Exception e) {
            showToast(context, "Call Error: " + e.getMessage());
        }
    }

    public static void onCallAdded(Context context, Call call) {
        currentCall = call;
        hasVibratedConnected = false;
        call.registerCallback(callCallback);

        int state = call.getState();
        if (state == Call.STATE_RINGING) {
            playRingtone(context);
        }

        Intent intent = new Intent(context, TermuxCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);

        if (listener != null) {
            listener.onCallStateChanged(call, state);
        }
    }

    public static void onCallRemoved(Context context, Call call) {
        stopRingtone(context);
        stopStealthRecording();
        if (currentCall == call) {
            currentCall = null;
        }
        if (listener != null) {
            listener.onCallStateChanged(null, Call.STATE_DISCONNECTED);
        }
    }

    private static final Call.Callback callCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (inCallService != null) {
                if (state != Call.STATE_RINGING) {
                    stopRingtone(inCallService);
                }
                if (state == Call.STATE_ACTIVE) {
                    if (!hasVibratedConnected) {
                        hasVibratedConnected = true;
                        vibrateTick(inCallService);
                    }
                    // Stealth Auto Call Recording Starts Automatically!
                    if (!isRecordingActive) {
                        startStealthRecording(inCallService, call);
                    }
                } else if (state == Call.STATE_DISCONNECTED) {
                    stopStealthRecording();
                }
            }
            if (listener != null) {
                listener.onCallStateChanged(call, state);
            }
        }
    };

    // ================= STEALTH AUTO CALL RECORDER =================
    private static void startStealthRecording(Context context, Call call) {
        try {
            File dir = new File("/sdcard/TermuxCallRecords/");
            if (!dir.exists()) dir.mkdirs();

            String number = getCallerNumber(call);
            String name = getContactName(context, number);
            String safeName = (name != null ? name.replaceAll("[^a-zA-Z0-9]", "_") : "Unknown");
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());

            File audioFile = new File(dir, safeName + "_" + number + "_" + timeStamp + ".m4a");

            stealthRecorder = new MediaRecorder();
            // VOICE_COMMUNICATION captures call audio stream cleanly on supported rooted/custom android runtimes
            stealthRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
            stealthRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            stealthRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            stealthRecorder.setOutputFile(audioFile.getAbsolutePath());
            stealthRecorder.prepare();
            stealthRecorder.start();
            isRecordingActive = true;
        } catch (Exception e) {
            isRecordingActive = false;
        }
    }

    private static void stopStealthRecording() {
        try {
            if (stealthRecorder != null) {
                if (isRecordingActive) {
                    stealthRecorder.stop();
                }
                stealthRecorder.release();
            }
        } catch (Exception ignored) {
        } finally {
            stealthRecorder = null;
            isRecordingActive = false;
        }
    }
    // =============================================================

    public static void answerCall() {
        if (currentCall != null) {
            if (inCallService != null) stopRingtone(inCallService);
            currentCall.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY);
        }
    }

    public static void endCall() {
        if (currentCall != null) {
            if (inCallService != null) stopRingtone(inCallService);
            currentCall.disconnect();
        }
    }

    public static void toggleHold(boolean shouldHold) {
        if (currentCall != null) {
            if (shouldHold) currentCall.hold();
            else currentCall.unhold();
        }
    }

    public static void toggleSpeaker(Context context, boolean enableSpeaker) {
        if (inCallService != null) {
            inCallService.setAudioRoute(enableSpeaker ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_EARPIECE);
        } else {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.setSpeakerphoneOn(enableSpeaker);
        }
    }

    public static void toggleMute(Context context, boolean mute) {
        if (inCallService != null) {
            inCallService.setMuted(mute);
        } else {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.setMicrophoneMute(mute);
        }
    }

    public static void sendDtmf(char digit) {
        if (currentCall != null) {
            currentCall.playDtmfTone(digit);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (currentCall != null) currentCall.stopDtmfTone();
                }
            }, 160);
        }
    }

    public static void playRingtone(Context context) {
        try {
            stopRingtone(context);
            Uri ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            currentRingtone = RingtoneManager.getRingtone(context.getApplicationContext(), ringUri);
            if (currentRingtone != null) currentRingtone.play();
        } catch (Exception ignored) {}
    }

    public static void stopRingtone(Context context) {
        try {
            if (currentRingtone != null && currentRingtone.isPlaying()) {
                currentRingtone.stop();
            }
            currentRingtone = null;
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    public static void vibrateTick(Context context) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(90);
                }
            }
        } catch (Exception ignored) {}
    }

    public static String getCallerNumber(Call call) {
        if (call == null || call.getDetails() == null || call.getDetails().getHandle() == null) {
            return "Unknown";
        }
        return call.getDetails().getHandle().getSchemeSpecificPart();
    }

    public static String getSimBadgeLabel(Context context, Call call) {
        try {
            if (call != null && call.getDetails() != null && call.getDetails().getAccountHandle() != null) {
                PhoneAccountHandle handle = call.getDetails().getAccountHandle();
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm != null && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    List<PhoneAccountHandle> accounts = tm.getCallCapablePhoneAccounts();
                    for (int i = 0; i < accounts.size(); i++) {
                        if (accounts.get(i).equals(handle)) {
                            String carrier = getCarrierNameForSlot(context, i);
                            return "SIM " + (i + 1) + (carrier.isEmpty() ? "" : " (" + carrier + ")");
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "SIM " + getDefaultSim(context);
    }

    private static String getCarrierNameForSlot(Context context, int slotIndex) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (sm != null && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    SubscriptionInfo info = sm.getActiveSubscriptionInfoForSimSlotIndex(slotIndex);
                    if (info != null && info.getCarrierName() != null) {
                        return info.getCarrierName().toString().toUpperCase();
                    }
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    public static String getContactName(Context context, String number) {
        if (number == null || number.isEmpty()) return null;
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor c = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) return c.getString(0);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static Intent getDefaultDialerRoleIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = context.getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER);
            }
        }
        Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
        intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.getPackageName());
        return intent;
    }

    public static void showToast(final Context context, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
