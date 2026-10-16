package com.termux.app.call;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.termux.app.sms.TermuxSmsManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TermuxCallActivity extends Activity implements TermuxCallManager.CallStateListener {

    private TermuxRadarView radarView;
    private TextView topCmdText;
    private TextView simBadgeText;
    private TextView callerNameText;
    private TextView callerNumText;
    private TextView statusText;
    private TextView telemetryText;

    private LinearLayout incomingSection;
    private LinearLayout activeSection;
    private EditText scratchpadInput;

    private Button btnSpeaker, btnMute, btnHold;
    private boolean isSpeaker = false;
    private boolean isMuted = false;
    private boolean isHold = false;

    private long callStartMillis = 0L;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock proximityWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (handleCliIntent(getIntent())) {
            return;
        }

        buildRedHackerCallScreen();
        TermuxCallManager.listener = this;
        acquireProximityLock();
        updateUiFromCall(TermuxCallManager.currentCall);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (handleCliIntent(intent)) return;
        updateUiFromCall(TermuxCallManager.currentCall);
    }

    private boolean handleCliIntent(Intent intent) {
        if (intent == null) return false;

        if (intent.getBooleanExtra("request_default_dialer", false)) {
            checkAndRequestPermissions();
            try {
                startActivityForResult(TermuxCallManager.getDefaultDialerRoleIntent(this), 303);
            } catch (Exception ignored) {}
            buildReactorDialpad("");
            return true;
        }

        if (intent.hasExtra("call_cli_cmd")) {
            String cmd = intent.getStringExtra("call_cli_cmd");
            if ("set_sim".equals(cmd)) {
                int sim = intent.getIntExtra("sim", 1);
                TermuxCallManager.setDefaultSim(this, sim);
                finish();
                return true;
            } else if ("dial".equals(cmd)) {
                String number = intent.getStringExtra("number");
                int sim = intent.getIntExtra("sim", 0);
                TermuxCallManager.startOutgoingCall(this, number, sim);
                finish();
                return true;
            } else if ("pick".equals(cmd)) {
                TermuxCallManager.answerCall();
                finish();
                return true;
            } else if ("end".equals(cmd)) {
                TermuxCallManager.endCall();
                finish();
                return true;
            } else if ("popup".equals(cmd)) {
                String q = intent.getStringExtra("query");
                buildReactorDialpad(q != null ? q : "");
                return true;
            } else if ("log".equals(cmd)) {
                buildCallHistoryScreen();
                return true;
            }
        }

        String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            String num = (data != null && "tel".equals(data.getScheme())) ? data.getSchemeSpecificPart() : "";
            buildReactorDialpad(num != null ? num : "");
            return true;
        }

        return false;
    }

    // ================= SCREEN 1: ACTIVE RED-OS CALL SCREEN =================
    private void buildRedHackerCallScreen() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#070506"));
        outer.setPadding(24, 28, 24, 24);

        LinearLayout phoneCard = new LinearLayout(this);
        phoneCard.setOrientation(LinearLayout.VERTICAL);
        phoneCard.setPadding(32, 32, 32, 32);
        phoneCard.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#0D080A"));
        cardBg.setCornerRadius(42f);
        cardBg.setStroke(5, Color.parseColor("#FF1744"));
        phoneCard.setBackground(cardBg);

        topCmdText = new TextView(this);
        topCmdText.setText("root@red-os:~# ./telephony --init");
        topCmdText.setTextColor(Color.parseColor("#FF5252"));
        topCmdText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        topCmdText.setTextSize(12f);
        topCmdText.setPadding(24, 18, 24, 18);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(Color.parseColor("#17090D"));
        topBg.setCornerRadius(14f);
        topBg.setStroke(2, Color.parseColor("#3B0D16"));
        topCmdText.setBackground(topBg);
        phoneCard.addView(topCmdText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        simBadgeText = new TextView(this);
        simBadgeText.setText("[ INITIALIZING LINK ]");
        simBadgeText.setTextColor(Color.parseColor("#FF5252"));
        simBadgeText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        simBadgeText.setTextSize(13f);
        simBadgeText.setGravity(Gravity.CENTER);
        simBadgeText.setPadding(36, 14, 36, 14);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#24070D"));
        badgeBg.setCornerRadius(30f);
        badgeBg.setStroke(3, Color.parseColor("#FF1744"));
        simBadgeText.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMargins(0, 24, 0, 12);
        phoneCard.addView(simBadgeText, badgeParams);

        radarView = new TermuxRadarView(this);
        LinearLayout.LayoutParams radarParams = new LinearLayout.LayoutParams(420, 420);
        radarParams.setMargins(0, 8, 0, 8);
        phoneCard.addView(radarView, radarParams);

        callerNameText = new TextView(this);
        callerNameText.setText("Unknown Caller");
        callerNameText.setTextColor(Color.WHITE);
        callerNameText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        callerNameText.setTextSize(21f);
        callerNameText.setGravity(Gravity.CENTER);
        phoneCard.addView(callerNameText);

        callerNumText = new TextView(this);
        callerNumText.setText("+91 ----------");
        callerNumText.setTextColor(Color.parseColor("#FF5252"));
        callerNumText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        callerNumText.setTextSize(15f);
        callerNumText.setGravity(Gravity.CENTER);
        callerNumText.setPadding(0, 4, 0, 4);
        phoneCard.addView(callerNumText);

        statusText = new TextView(this);
        statusText.setText("STATUS: SYN_SENT...");
        statusText.setTextColor(Color.parseColor("#94A3B8"));
        statusText.setTypeface(Typeface.MONOSPACE);
        statusText.setTextSize(11.5f);
        statusText.setGravity(Gravity.CENTER);
        phoneCard.addView(statusText);

        telemetryText = new TextView(this);
        telemetryText.setText("> Ringtone : TAP_HERE_TO_MUTE\n> Security : AES-256_VOICE_TUNNEL (Auto-Recording Active)");
        telemetryText.setTextColor(Color.parseColor("#FF8A80"));
        telemetryText.setTypeface(Typeface.MONOSPACE);
        telemetryText.setTextSize(11f);
        telemetryText.setPadding(24, 16, 24, 16);
        GradientDrawable teleBg = new GradientDrawable();
        teleBg.setColor(Color.parseColor("#120609"));
        teleBg.setCornerRadius(14f);
        teleBg.setStroke(2, Color.parseColor("#3B0D16"));
        telemetryText.setBackground(teleBg);
        telemetryText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.stopRingtone(TermuxCallActivity.this);
                telemetryText.setText("> Ringtone : SILENCED [MUTED]\n> Radar    : ACTIVE_LOCK");
            }
        });
        LinearLayout.LayoutParams teleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        teleParams.setMargins(0, 16, 0, 12);
        phoneCard.addView(telemetryText, teleParams);

        incomingSection = new LinearLayout(this);
        incomingSection.setOrientation(LinearLayout.VERTICAL);

        LinearLayout ansRejRow = new LinearLayout(this);
        ansRejRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnReject = createHackerButton("[ REJECT ]", "#FF5252", "#3B0712", "#FF1744");
        btnReject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.endCall();
            }
        });
        LinearLayout.LayoutParams rejP = new LinearLayout.LayoutParams(0, 130, 1f);
        rejP.setMargins(0, 0, 14, 0);
        ansRejRow.addView(btnReject, rejP);

        Button btnAnswer = createHackerButton("[ ANSWER ]", "#00E676", "#062918", "#00E676");
        btnAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.answerCall();
            }
        });
        LinearLayout.LayoutParams ansP = new LinearLayout.LayoutParams(0, 130, 1f);
        ansP.setMargins(14, 0, 0, 0);
        ansRejRow.addView(btnAnswer, ansP);

        incomingSection.addView(ansRejRow);
        phoneCard.addView(incomingSection, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        activeSection = new LinearLayout(this);
        activeSection.setOrientation(LinearLayout.VERTICAL);

        LinearLayout gridRow1 = new LinearLayout(this);
        gridRow1.setOrientation(LinearLayout.HORIZONTAL);
        btnSpeaker = createGridTile("SPEAKER\n[OFF]");
        btnMute = createGridTile("MIC MUTE\n[OFF]");
        btnHold = createGridTile("HOLD\n[OFF]");

        btnSpeaker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSpeaker = !isSpeaker;
                TermuxCallManager.toggleSpeaker(TermuxCallActivity.this, isSpeaker);
                styleTile(btnSpeaker, "SPEAKER\n[" + (isSpeaker ? "ON" : "OFF") + "]", isSpeaker, "#FF1744");
            }
        });
        btnMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isMuted = !isMuted;
                TermuxCallManager.toggleMute(TermuxCallActivity.this, isMuted);
                styleTile(btnMute, "MIC MUTE\n[" + (isMuted ? "ON" : "OFF") + "]", isMuted, "#FF1744");
            }
        });
        btnHold.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isHold = !isHold;
                TermuxCallManager.toggleHold(isHold);
                styleTile(btnHold, "HOLD\n[" + (isHold ? "ON" : "OFF") + "]", isHold, "#FFD600");
                if (radarView != null) {
                    radarView.setMode(isHold ? TermuxRadarView.MODE_HOLD : TermuxRadarView.MODE_ACTIVE);
                }
            }
        });

        addTileToRow(gridRow1, btnSpeaker);
        addTileToRow(gridRow1, btnMute);
        addTileToRow(gridRow1, btnHold);
        activeSection.addView(gridRow1);

        LinearLayout gridRow2 = new LinearLayout(this);
        gridRow2.setOrientation(LinearLayout.HORIZONTAL);
        Button btnKeypad = createGridTile("KEYPAD\n[DTMF]");
        Button btnTermuxMini = createGridTile("TERMUX\n[MINI]");
        Button btnLogs = createGridTile("CALL LOGS\n[VIEW]");
        styleTile(btnTermuxMini, "TERMUX\n[MINI]", true, "#FF5252");

        btnKeypad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDtmfDialog();
            }
        });
        btnTermuxMini.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveTaskToBack(true);
            }
        });
        btnLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buildCallHistoryScreen();
            }
        });

        addTileToRow(gridRow2, btnKeypad);
        addTileToRow(gridRow2, btnTermuxMini);
        addTileToRow(gridRow2, btnLogs);
        activeSection.addView(gridRow2);

        scratchpadInput = new EditText(this);
        scratchpadInput.setHint("Scratchpad: Type OTP/Number -> Auto-Clipboard...");
        scratchpadInput.setHintTextColor(Color.parseColor("#94A3B8"));
        scratchpadInput.setTextColor(Color.WHITE);
        scratchpadInput.setTypeface(Typeface.MONOSPACE);
        scratchpadInput.setTextSize(12f);
        scratchpadInput.setSingleLine(true);
        scratchpadInput.setPadding(24, 18, 24, 18);
        GradientDrawable noteBg = new GradientDrawable();
        noteBg.setColor(Color.parseColor("#17090D"));
        noteBg.setCornerRadius(14f);
        noteBg.setStroke(2, Color.parseColor("#4A1521"));
        scratchpadInput.setBackground(noteBg);
        LinearLayout.LayoutParams noteP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteP.setMargins(0, 12, 0, 14);
        activeSection.addView(scratchpadInput, noteP);

        Button btnHangup = createHackerButton("[ END CALL / HANGUP ]", "#FF5252", "#3B0712", "#FF1744");
        btnHangup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveScratchpadToClipboard();
                TermuxCallManager.endCall();
            }
        });
        activeSection.addView(btnHangup, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 130));

        phoneCard.addView(activeSection, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        outer.addView(phoneCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(outer);
    }

    // ================= SCREEN 2: MINIMAL REACTOR DIALPAD =================
    private void buildReactorDialpad(String initialQuery) {
        checkAndRequestPermissions();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#070506"));
        root.setPadding(32, 32, 32, 32);

        LinearLayout popupCard = new LinearLayout(this);
        popupCard.setOrientation(LinearLayout.VERTICAL);
        popupCard.setPadding(28, 28, 28, 28);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0D080A"));
        bg.setCornerRadius(40f);
        bg.setStroke(4, Color.parseColor("#FF1744"));
        popupCard.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("RED-OS // CYBER REACTOR DIALPAD");
        title.setTextColor(Color.parseColor("#FF1744"));
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextSize(15f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 16);
        popupCard.addView(title);

        final EditText numberDisplay = new EditText(this);
        numberDisplay.setText(initialQuery);
        numberDisplay.setTextColor(Color.WHITE);
        numberDisplay.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        numberDisplay.setTextSize(22f);
        numberDisplay.setGravity(Gravity.CENTER);
        numberDisplay.setBackground(null);

        LinearLayout numBox = new LinearLayout(this);
        numBox.setOrientation(LinearLayout.HORIZONTAL);
        numBox.setGravity(Gravity.CENTER_VERTICAL);
        numBox.setPadding(20, 12, 20, 12);
        GradientDrawable nBg = new GradientDrawable();
        nBg.setColor(Color.parseColor("#17070B"));
        nBg.setCornerRadius(20f);
        nBg.setStroke(2, Color.parseColor("#FF1744"));
        numBox.setBackground(nBg);
        numBox.addView(numberDisplay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnCut = new Button(this);
        btnCut.setText("⌫");
        btnCut.setTextColor(Color.parseColor("#FF5252"));
        btnCut.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnCut.setTextSize(18f);
        btnCut.setBackground(null);
        btnCut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cur = numberDisplay.getText().toString();
                if (!cur.isEmpty()) {
                    numberDisplay.setText(cur.substring(0, cur.length() - 1));
                }
            }
        });
        numBox.addView(btnCut);
        popupCard.addView(numBox);

        final TextView matchText = new TextView(this);
        matchText.setText("MATCH: Ready to dial");
        matchText.setTextColor(Color.parseColor("#00E5FF"));
        matchText.setTypeface(Typeface.MONOSPACE);
        matchText.setTextSize(12f);
        matchText.setGravity(Gravity.CENTER);
        matchText.setPadding(0, 10, 0, 16);
        popupCard.addView(matchText);

        // Circular Keypad Grid (1-9, *, 0, #)
        String[][] keys = {
            {"1", "2", "3"},
            {"4", "5", "6"},
            {"7", "8", "9"},
            {"*", "0", "#"}
        };

        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (final String digit : rowKeys) {
                Button btnKey = new Button(this);
                btnKey.setText(digit);
                btnKey.setTextColor(Color.WHITE);
                btnKey.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                btnKey.setTextSize(20f);
                GradientDrawable kBg = new GradientDrawable();
                kBg.setShape(GradientDrawable.OVAL);
                kBg.setColor(Color.parseColor("#3B0712"));
                kBg.setStroke(3, Color.parseColor("#00E5FF"));
                btnKey.setBackground(kBg);
                btnKey.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        numberDisplay.append(digit);
                    }
                });
                LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(110, 110);
                kp.setMargins(16, 12, 16, 12);
                row.addView(btnKey, kp);
            }
            popupCard.addView(row);
        }

        // Bottom Action Bar: [S1/S2 SIM] [BIG GREEN CALL] [CALL LOGS]
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(0, 20, 0, 10);

        Button btnSimToggle = new Button(this);
        btnSimToggle.setText("SIM 1");
        btnSimToggle.setTextColor(Color.parseColor("#00E5FF"));
        btnSimToggle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnSimToggle.setTextSize(12f);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setShape(GradientDrawable.OVAL);
        sBg.setColor(Color.parseColor("#07222E"));
        sBg.setStroke(3, Color.parseColor("#00E5FF"));
        btnSimToggle.setBackground(sBg);
        final int[] currentSim = {TermuxCallManager.getDefaultSim(this)};
        btnSimToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSim[0] = (currentSim[0] == 1) ? 2 : 1;
                ((Button) v).setText("SIM " + currentSim[0]);
                TermuxCallManager.showToast(TermuxCallActivity.this, "Selected: SIM " + currentSim[0]);
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(110, 110);
        sp.setMargins(10, 0, 10, 0);
        actionRow.addView(btnSimToggle, sp);

        Button btnBigCall = new Button(this);
        btnBigCall.setText("CALL");
        btnBigCall.setTextColor(Color.parseColor("#00E676"));
        btnBigCall.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnBigCall.setTextSize(16f);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setShape(GradientDrawable.OVAL);
        cBg.setColor(Color.parseColor("#063B20"));
        cBg.setStroke(4, Color.parseColor("#00E676"));
        btnBigCall.setBackground(cBg);
        btnBigCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String num = numberDisplay.getText().toString().trim();
                if (!num.isEmpty()) {
                    TermuxCallManager.startOutgoingCall(TermuxCallActivity.this, num, currentSim[0]);
                    finish();
                }
            }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(140, 140);
        cp.setMargins(14, 0, 14, 0);
        actionRow.addView(btnBigCall, cp);

        Button btnViewLogs = new Button(this);
        btnViewLogs.setText("LOGS");
        btnViewLogs.setTextColor(Color.parseColor("#00E5FF"));
        btnViewLogs.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnViewLogs.setTextSize(12f);
        GradientDrawable lBg = new GradientDrawable();
        lBg.setShape(GradientDrawable.OVAL);
        lBg.setColor(Color.parseColor("#1E1136"));
        lBg.setStroke(3, Color.parseColor("#7C3AED"));
        btnViewLogs.setBackground(lBg);
        btnViewLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buildCallHistoryScreen();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(110, 110);
        lp.setMargins(10, 0, 10, 0);
        actionRow.addView(btnViewLogs, lp);

        popupCard.addView(actionRow);
        root.addView(popupCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    // ================= SCREEN 3: CYBER CALL HISTORY & LOGS + VIDEO CALL + REC PLAYER =================
    private void buildCallHistoryScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#070506"));
        root.setPadding(24, 24, 24, 24);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0D080A"));
        bg.setCornerRadius(40f);
        bg.setStroke(4, Color.parseColor("#FF1744"));
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("RED-OS // CALL HISTORY & RECORDINGS");
        title.setTextColor(Color.parseColor("#FF1744"));
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextSize(14f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 16);
        card.addView(title);

        Button btnBackToDial = new Button(this);
        btnBackToDial.setText("◄ BACK TO DIALPAD");
        btnBackToDial.setTextColor(Color.parseColor("#00E5FF"));
        btnBackToDial.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnBackToDial.setBackgroundColor(Color.parseColor("#1E1136"));
        btnBackToDial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buildReactorDialpad("");
            }
        });
        card.addView(btnBackToDial, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private Button createHackerButton(String text, String textColor, String bgColor, String strokeColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(textColor));
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setTextSize(14f);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgColor));
        gd.setCornerRadius(24f);
        gd.setStroke(4, Color.parseColor(strokeColor));
        b.setBackground(gd);
        return b;
    }

    private Button createGridTile(String text) {
        Button b = new Button(this);
        b.setTextSize(11f);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        styleTile(b, text, false, "#4A1521");
        return b;
    }

    private void styleTile(Button b, String text, boolean active, String activeColor) {
        b.setText(text);
        b.setTextColor(active ? Color.parseColor(activeColor) : Color.WHITE);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(active ? "#260812" : "#1A0B0F"));
        gd.setCornerRadius(18f);
        gd.setStroke(3, Color.parseColor(active ? activeColor : "#4A1521"));
        b.setBackground(gd);
    }

    private void addTileToRow(LinearLayout row, Button tile) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 125, 1f);
        lp.setMargins(8, 8, 8, 8);
        row.addView(tile, lp);
    }

    private void showDtmfDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("DTMF KEYPAD");
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(24, 24, 24, 24);
        final String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        for (String[] r : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (final String k : r) {
                Button b = new Button(this);
                b.setText(k);
                b.setTextSize(18f);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TermuxCallManager.sendDtmf(k.charAt(0));
                    }
                });
                row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            pad.addView(row);
        }
        builder.setView(pad);
        builder.setPositiveButton("CLOSE", null);
        builder.show();
    }

    private void updateUiFromCall(Call call) {
        if (call == null) {
            saveScratchpadToClipboard();
            finish();
            return;
        }
        if (radarView == null) return;

        String num = TermuxCallManager.getCallerNumber(call);
        String name = TermuxCallManager.getContactName(this, num);
        String simLabel = TermuxCallManager.getSimBadgeLabel(this, call);

        callerNameText.setText(name != null ? name : "Unknown Caller");
        callerNumText.setText(num);
        radarView.setInitials(getInitials(name != null ? name : num));

        int state = call.getState();
        if (state == Call.STATE_RINGING) {
            radarView.setMode(TermuxRadarView.MODE_INCOMING);
            topCmdText.setText("root@red-os:~# ./telephony --incoming");
            simBadgeText.setText("[ INCOMING : " + simLabel + " ]");
            statusText.setText("STATUS: INCOMING_PACKET_RINGING...");
            incomingSection.setVisibility(View.VISIBLE);
            activeSection.setVisibility(View.GONE);
        } else if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
            radarView.setMode(TermuxRadarView.MODE_OUTGOING);
            topCmdText.setText("root@red-os:~# ./telephony --dial " + num);
            simBadgeText.setText("[ DIALING : " + simLabel + " ]");
            statusText.setText("STATUS: SYN_SENT [DIALING...]");
            telemetryText.setText("> Radar State : EMERALD_GREEN_SPIN\n> Auto-Recording : ARMED & STEALTH");
            incomingSection.setVisibility(View.GONE);
            activeSection.setVisibility(View.VISIBLE);
        } else if (state == Call.STATE_HOLDING) {
            radarView.setMode(TermuxRadarView.MODE_HOLD);
            topCmdText.setText("root@red-os:~# ./telephony --call-hold");
            simBadgeText.setText("[ ON HOLD : " + simLabel + " | PAUSED ]");
            statusText.setText("STATUS: STREAM_FROZEN [HOLD_MODE]");
            incomingSection.setVisibility(View.GONE);
            activeSection.setVisibility(View.VISIBLE);
        } else if (state == Call.STATE_ACTIVE) {
            radarView.setMode(TermuxRadarView.MODE_ACTIVE);
            topCmdText.setText("root@red-os:~# ./telephony --active-call");
            statusText.setText("STATUS: VOICE_TUNNEL_LOCKED [OK]");
            telemetryText.setText("> Audio Link : RECORDING_ACTIVE (/sdcard/TermuxCallRecords/)\n> Encryption : VERIFIED");
            incomingSection.setVisibility(View.GONE);
            activeSection.setVisibility(View.VISIBLE);
            if (callStartMillis == 0L) {
                callStartMillis = System.currentTimeMillis();
                startCallTimer(simLabel);
            }
        } else if (state == Call.STATE_DISCONNECTED) {
            saveScratchpadToClipboard();
            finish();
        }
    }

    private void startCallTimer(final String simLabel) {
        timerHandler.post(new Runnable() {
            @Override
            public void run() {
                if (TermuxCallManager.currentCall == null) return;
                long sec = (System.currentTimeMillis() - callStartMillis) / 1000L;
                String timeStr = String.format(Locale.ROOT, "%02d:%02d", sec / 60, sec % 60);
                if (TermuxCallManager.currentCall.getState() == Call.STATE_ACTIVE && simBadgeText != null) {
                    simBadgeText.setText("[ ACTIVE : " + simLabel + " | " + timeStr + " ]");
                }
                timerHandler.postDelayed(this, 1000L);
            }
        });
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "ID";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        return name.trim().substring(0, Math.min(2, name.trim().length())).toUpperCase();
    }

    private void saveScratchpadToClipboard() {
        if (scratchpadInput != null) {
            String note = scratchpadInput.getText().toString().trim();
            if (!note.isEmpty()) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("CallNote", note));
                    TermuxCallManager.showToast(this, "Call Note Copied to Clipboard!");
                }
                scratchpadInput.setText("");
            }
        }
    }

    @Override
    public void onCallStateChanged(final Call call, final int state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUiFromCall(call);
            }
        });
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            List<String> req = new ArrayList<>();
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) req.add(p);
            }
            if (!req.isEmpty()) requestPermissions(req.toArray(new String[0]), 301);
        }
    }

    private void acquireProximityLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "termux:call_proximity");
                proximityWakeLock.acquire(60 * 60 * 1000L);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacksAndMessages(null);
        if (TermuxCallManager.listener == this) {
            TermuxCallManager.listener = null;
        }
        try {
            if (proximityWakeLock != null && proximityWakeLock.isHeld()) {
                proximityWakeLock.release();
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
