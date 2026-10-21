package com.termux.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.util.ArrayList;

public class TermuxAssistActivity extends Activity {

    private static final int SPEECH_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startVoiceInput();
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye WonderPlayer...");

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Mic Service Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                Toast.makeText(this, "🗣️ " + spokenText, Toast.LENGTH_SHORT).show();
                executeAssistantScript(spokenText);
            }
        }
        finish();
    }

    private void executeAssistantScript(String query) {
        Intent serviceIntent = new Intent(this, TermuxService.class);
        serviceIntent.setAction(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE);
        
        Uri programUri = Uri.parse("file:///data/data/com.termux/files/usr/bin/python");
        serviceIntent.setData(programUri);
        
        String[] arguments = new String[]{
            "/data/data/com.termux/files/home/assistant.py",
            query
        };
        serviceIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, arguments);
        serviceIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, "/data/data/com.termux/files/home");
        serviceIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true);

        startService(serviceIntent);
    }
}
