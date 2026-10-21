package com.termux.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.util.ArrayList;

public class TermuxAlexaService extends Service {

    private static final String CHANNEL_ID = "termux_alexa_channel";
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private AudioManager audioManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isListening = false;
    private boolean waitingForCommandOnly = false;

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        startForegroundNotification();
        initSpeechRecognizer();
        startListeningLoop();
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Termux Alexa Wake-Word",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("WonderPlayer AI Active")
                .setContentText("Listening for 'Hey Alexa'...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
            startForeground(999, notification);
        }
    }

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                unmuteBeepSound();
            }

            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                isListening = false;
            }

            @Override
            public void onError(int error) {
                unmuteBeepSound();
                isListening = false;
                restartListening(400);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                processSpeech(results, true);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                processSpeech(partialResults, false);
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void processSpeech(Bundle bundle, boolean isFinal) {
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            if (isFinal) restartListening(300);
            return;
        }

        String text = matches.get(0).toLowerCase().trim();

        // Case 1: Agar pehle "Hey Alexa" bol chuke hain aur ab main command sun rahe hain
        if (waitingForCommandOnly && isFinal) {
            waitingForCommandOnly = false;
            Toast.makeText(this, "🗣️ " + text, Toast.LENGTH_SHORT).show();
            executePythonAssistant(text);
            // Assistant bol raha hoga isliye 6 second baad mic on karein
            restartListening(6000);
            return;
        }

        // Case 2: Wake-Word ("Hey Alexa" / "Alexa") check karna
        if (text.contains("hey alexa") || text.contains("hi alexa") || text.contains("हे एलेक्सा") || text.contains("एलेक्सा") || text.startsWith("alexa")) {
            if (!isFinal && text.length() < 12) {
                // Pura sentence bolne ka wait karein
                return;
            }

            speechRecognizer.cancel();
            isListening = false;
            vibratePhone();

            String command = text
                .replace("hey alexa", "")
                .replace("hi alexa", "")
                .replace("हे एलेक्सा", "")
                .replace("एलेक्सा", "")
                .replaceFirst("^alexa", "")
                .trim();

            if (!command.isEmpty()) {
                // Ek saath bola: "Hey Alexa aaj mausam kaisa hai"
                Toast.makeText(this, "🤖 Alexa: " + command, Toast.LENGTH_SHORT).show();
                executePythonAssistant(command);
                restartListening(6000);
            } else {
                // Sirf "Hey Alexa" bola -> Ab command sunne ke liye active ho jao
                waitingForCommandOnly = true;
                Toast.makeText(this, "🎤 जी भाई, बोलिए...", Toast.LENGTH_SHORT).show();
                executePythonAssistant("__WAKE_ONLY__");
                restartListening(2500);
            }
            return;
        }

        if (isFinal) {
            restartListening(300);
        }
    }

    private void executePythonAssistant(String query) {
        Intent serviceIntent = new Intent(this, TermuxService.class);
        serviceIntent.setAction(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE);
        serviceIntent.setData(Uri.parse("file:///data/data/com.termux/files/usr/bin/python"));
        serviceIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{
            "/data/data/com.termux/files/home/assistant.py",
            query
        });
        serviceIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, "/data/data/com.termux/files/home");
        serviceIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true);
        startService(serviceIntent);
    }

    private void startListeningLoop() {
        if (!isListening && speechRecognizer != null) {
            isListening = true;
            muteBeepSound();
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    private void restartListening(long delayMs) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::startListeningLoop, delayMs);
    }

    private void muteBeepSound() {
        if (audioManager != null && !waitingForCommandOnly) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
        }
    }

    private void unmuteBeepSound() {
        if (audioManager != null) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0);
        }
    }

    private void vibratePhone() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(80);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unmuteBeepSound();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
              }
