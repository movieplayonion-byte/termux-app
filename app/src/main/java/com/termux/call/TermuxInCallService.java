package com.termux.app.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

import java.util.ArrayList;
import java.util.Locale;

public class TermuxInCallService extends InCallService {

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isVoiceAnswered = false;
    private boolean shouldKeepListening = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Call.Callback voiceCallCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            if (state == Call.STATE_RINGING) {
                isVoiceAnswered = false;
                startVoiceListening();
            } else if (state == Call.STATE_ACTIVE) {
                stopVoiceListening();
                if (isVoiceAnswered) {
                    routeAudioToSpeakerIfNoBluetooth();
                }
            } else if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                stopVoiceListening();
                isVoiceAnswered = false;
            }
        }
    };

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

        call.registerCallback(voiceCallCallback);

        if (call.getState() == Call.STATE_RINGING) {
            isVoiceAnswered = false;
            startVoiceListening();
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        stopVoiceListening();
        if (call != null) {
            call.unregisterCallback(voiceCallCallback);
        }
        super.onCallRemoved(call);
        TermuxCallManager.onCallRemoved(this, call);
    }

    @Override
    public void onDestroy() {
        stopVoiceListening();
        if (TermuxCallManager.inCallService == this) {
            TermuxCallManager.inCallService = null;
        }
        super.onDestroy();
    }

    // ================= BACKGROUND VOICE LISTENER ("pickup" / "cut") =================
    private void startVoiceListening() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                    }
                    if (!SpeechRecognizer.isRecognitionAvailable(TermuxInCallService.this)) {
                        return;
                    }

                    shouldKeepListening = true;

                    if (speechRecognizer == null) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(TermuxInCallService.this);
                        speechRecognizer.setRecognitionListener(new RecognitionListener() {
                            @Override
                            public void onReadyForSpeech(Bundle params) {}

                            @Override
                            public void onBeginningOfSpeech() {}

                            @Override
                            public void onRmsChanged(float rmsdB) {}

                            @Override
                            public void onBufferReceived(byte[] buffer) {}

                            @Override
                            public void onEndOfSpeech() {}

                            @Override
                            public void onError(int error) {
                                // Agar call abhi bhi ring ho rahi hai toh dubara sunna chalu karein
                                if (shouldKeepListening && TermuxCallManager.currentCall != null
                                        && TermuxCallManager.currentCall.getState() == Call.STATE_RINGING) {
                                    mainHandler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (shouldKeepListening && speechRecognizer != null) {
                                                try {
                                                    speechRecognizer.startListening(recognizerIntent);
                                                } catch (Exception ignored) {}
                                            }
                                        }
                                    }, 250);
                                }
                            }

                            @Override
                            public void onResults(Bundle results) {
                                handleSpeechResults(results);
                                if (shouldKeepListening && TermuxCallManager.currentCall != null
                                        && TermuxCallManager.currentCall.getState() == Call.STATE_RINGING) {
                                    try {
                                        speechRecognizer.startListening(recognizerIntent);
                                    } catch (Exception ignored) {}
                                }
                            }

                            @Override
                            public void onPartialResults(Bundle partialResults) {
                                handleSpeechResults(partialResults);
                            }

                            @Override
                            public void onEvent(int eventType, Bundle params) {}
                        });
                    }

                    if (recognizerIntent == null) {
                        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                    }

                    speechRecognizer.startListening(recognizerIntent);
                } catch (Exception ignored) {}
            }
        });
    }

    private void handleSpeechResults(Bundle bundle) {
        if (bundle == null || !shouldKeepListening) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null) return;

        for (String phrase : matches) {
            if (phrase == null) continue;
            String text = phrase.toLowerCase(Locale.ROOT).trim();

            // Sirf "pickup" (ya "pick up") aur "cut" par kaam karega
            if (text.contains("pickup") || text.contains("pick up")) {
                triggerVoicePickup();
                break;
            } else if (text.contains("cut") || text.equals("kat")) {
                triggerVoiceCut();
                break;
            }
        }
    }

    private void stopVoiceListening() {
        shouldKeepListening = false;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (speechRecognizer != null) {
                        speechRecognizer.stopListening();
                        speechRecognizer.cancel();
                        speechRecognizer.destroy();
                        speechRecognizer = null;
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    // ================= SMART SPEAKER ROUTING (BLUETOOTH VS SPEAKER) =================
    private void routeAudioToSpeakerIfNoBluetooth() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if ( isBluetoothAudioConnected()) {
                        // Bluetooth connected hai toh speaker par NAHI jayega
                        return;
                    }
                    // Bluetooth nahi hai aur Voice se pickup kiya hai toh Speaker ON
                    setAudioRoute(CallAudioState.ROUTE_SPEAKER);
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.setSpeakerphoneOn(true);
                    }
                } catch (Exception ignored) {}
            }
        }, 300);
    }

    private boolean isBluetoothAudioConnected() {
        try {
            CallAudioState audioState = getCallAudioState();
            if (audioState != null) {
                if (audioState.getRoute() == CallAudioState.ROUTE_BLUETOOTH) {
                    return true;
                }
                if ((audioState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if (audioState.getSupportedBluetoothDevices() != null
                                && !audioState.getSupportedBluetoothDevices().isEmpty()) {
                            return true;
                        }
                    } else {
                        return true;
                    }
                }
            }

            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                if (am.isBluetoothScoOn() || am.isBluetoothA2dpOn()) {
                    return true;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    for (AudioDeviceInfo d : devices) {
                        int type = d.getType();
                        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                                || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void triggerVoicePickup() {
        shouldKeepListening = false;
        isVoiceAnswered = true;
        stopVoiceListening();
        TermuxCallManager.answerCall();
    }

    public void triggerVoiceCut() {
        shouldKeepListening = false;
        isVoiceAnswered = false;
        stopVoiceListening();
        TermuxCallManager.endCall();
    }
}
