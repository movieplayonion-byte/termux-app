package com.termux.app.call;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telephony.SmsManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class TermuxCallActivity extends Activity implements TermuxCallManager.CallStateListener {

    private TextView topCmdText;
    private TextView simBadgeText;
    private TermuxRadarView radarView;
    private TextView callerNameText;
    private TextView kycBadgeText;
    private TextView callerNumberText;
    private TextView callStatusText;
    private TextView telemetryLine1;
    private TextView telemetryLine2;

    private LinearLayout bottomContainer;

    private boolean isSpeakerOn = false;
    private boolean isMuted = false;
    private boolean isOnHold = false;
    private long callStartTimeMs = 0L;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (TermuxCallManager.currentCall != null && TermuxCallManager.currentCall.getState() == Call.STATE_ACTIVE) {
                long elapsedSec = (System.currentTimeMillis() - callStartTimeMs) / 1000L;
                long mins = elapsedSec / 60L;
                long secs = elapsedSec % 60L;
                String sim = TermuxCallManager.getSimBadgeLabel(TermuxCallActivity.this, TermuxCallManager.currentCall);
                simBadgeText.setText(String.format(Locale.ROOT, "[ ACTIVE : %s | %02d:%02d ]", sim, mins, secs));
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildCyberLayout();
        TermuxCallManager.listener = this;
        handleCliIntent(getIntent());
        refreshUiFromCurrentCall();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCliIntent(intent);
        refreshUiFromCurrentCall();
    }

    private void handleCliIntent(Intent intent) {
        if (intent == null) return;
        String cmd = intent.getStringExtra("call_cli_cmd");
        if ("dial".equalsIgnoreCase(cmd)) {
            String num = intent.getStringExtra("number");
            int sim = intent.getIntExtra("sim", 0);
            if (num != null && !num.isEmpty()) {
                TermuxCallManager.startOutgoingCall(this, num, sim);
            }
        }
    }

    private void buildCyberLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D080A"));
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable rootBorder = new GradientDrawable();
        rootBorder.setColor(Color.parseColor("#0D080A"));
        rootBorder.setStroke(dp(2), Color.parseColor("#FF1744"));
        rootBorder.setCornerRadius(dp(18));
        root.setBackground(rootBorder);

        // 1. Top Terminal Command Bar
        topCmdText = new TextView(this);
        topCmdText.setText("root@red-os:~# ./telephony --init");
        topCmdText.setTextColor(Color.parseColor("#FF5252"));
        topCmdText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        topCmdText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        topCmdText.setPadding(dp(10), dp(7), dp(10), dp(7));
        topCmdText.setBackground(makeBox("#17090D", "#3B0D16", 8, 1));
        root.addView(topCmdText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2. Top SIM Badge
        simBadgeText = new TextView(this);
        simBadgeText.setText("[ STANDBY : SIM 1 ]");
        simBadgeText.setTextColor(Color.parseColor("#FF5252"));
        simBadgeText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        simBadgeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        simBadgeText.setGravity(Gravity.CENTER);
        simBadgeText.setPadding(dp(16), dp(6), dp(16), dp(6));
        simBadgeText.setBackground(makeBox("#24070D", "#FF1744", 10, 2));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        badgeLp.topMargin = dp(10);
        root.addView(simBadgeText, badgeLp);

        // 3. 3-Ring Bi-Directional Hacker Radar Circle
        radarView = new TermuxRadarView(this);
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(dp(185), dp(185));
        radarLp.gravity = Gravity.CENTER_HORIZONTAL;
        radarLp.topMargin = dp(8);
        root.addView(radarView, radarLp);

        // 4. Caller Name (Never shows Unknown)
        callerNameText = new TextView(this);
        callerNameText.setText("TELECOM VERIFIED SUBSCRIBER");
        callerNameText.setTextColor(Color.WHITE);
        callerNameText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        callerNameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        callerNameText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(4);
        root.addView(callerNameText, nameLp);

        // 5. Court-Mandated Telecom CNAP / KYC Verified Badge
        kycBadgeText = new TextView(this);
        kycBadgeText.setText("✓ TELECOM CNAP / KYC ID : VERIFIED");
        kycBadgeText.setTextColor(Color.parseColor("#00E676"));
        kycBadgeText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        kycBadgeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        kycBadgeText.setGravity(Gravity.CENTER);
        kycBadgeText.setPadding(dp(12), dp(4), dp(12), dp(4));
        kycBadgeText.setBackground(makeBox("#041E13", "#059669", 6, 1));
        LinearLayout.LayoutParams kycLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kycLp.gravity = Gravity.CENTER_HORIZONTAL;
        kycLp.topMargin = dp(4);
        root.addView(kycBadgeText, kycLp);

        // 6. Caller Number & Status
        callerNumberText = new TextView(this);
        callerNumberText.setText("+91-VERIFIED-LINE");
        callerNumberText.setTextColor(Color.parseColor("#FF5252"));
        callerNumberText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        callerNumberText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        callerNumberText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        numLp.topMargin = dp(4);
        root.addView(callerNumberText, numLp);

        callStatusText = new TextView(this);
        callStatusText.setText("STATUS: SYN_SENT [DIALING...]");
        callStatusText.setTextColor(Color.parseColor("#94A3B8"));
        callStatusText.setTypeface(Typeface.MONOSPACE);
        callStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        callStatusText.setGravity(Gravity.CENTER);
        root.addView(callStatusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 7. Two Telemetry Lines right below Dialing Status (Radar State + Auto Recording)
        LinearLayout teleBox = new LinearLayout(this);
        teleBox.setOrientation(LinearLayout.VERTICAL);
        teleBox.setPadding(dp(10), dp(7), dp(10), dp(7));
        teleBox.setBackground(makeBox("#120609", "#3B0D16", 8, 1));
        LinearLayout.LayoutParams teleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        teleLp.topMargin = dp(8);

        telemetryLine1 = new TextView(this);
        telemetryLine1.setText("> RADAR_STATE : 3-RING_OPPOSITE_GLITCH [STANDBY]");
        telemetryLine1.setTextColor(Color.parseColor("#00E676"));
        telemetryLine1.setTypeface(Typeface.MONOSPACE);
        telemetryLine1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);

        telemetryLine2 = new TextView(this);
        telemetryLine2.setText("> AUTO_RECORD : STEALTH_AAC_STREAM [ARMED/REC]");
        telemetryLine2.setTextColor(Color.parseColor("#FF8A80"));
        telemetryLine2.setTypeface(Typeface.MONOSPACE);
        telemetryLine2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);

        teleBox.addView(telemetryLine1);
        teleBox.addView(telemetryLine2);
        teleBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.stopRingtone(TermuxCallActivity.this);
                Toast.makeText(TermuxCallActivity.this, "Ringtone Muted", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(teleBox, teleLp);

        // 8. MIDDLE EMPTY SPACE (Pushes everything after the 2 lines to the very bottom!)
        View middleEmptySpacer = new View(this);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.addView(middleEmptySpacer, spacerLp);

        // 9. BOTTOM DOCKED CONTAINER
        bottomContainer = new LinearLayout(this);
        bottomContainer.setOrientation(LinearLayout.VERTICAL);
        bottomContainer.setGravity(Gravity.BOTTOM);
        root.addView(bottomContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void refreshUiFromCurrentCall() {
        Call call = TermuxCallManager.currentCall;
        if (call == null) {
            finish();
            return;
        }

        String number = TermuxCallManager.getCallerNumber(call);
        String verifiedTitle = TermuxCallManager.getVerifiedCallerTitle(this, call, number);
        String kycBadge = TermuxCallManager.getTelecomKycBadgeText(this, call, number);
        String simLabel = TermuxCallManager.getSimBadgeLabel(this, call);

        callerNameText.setText(verifiedTitle);
        kycBadgeText.setText(kycBadge);
        callerNumberText.setText(number);
        radarView.setInitials(extractInitials(verifiedTitle));

        int state = call.getState();
        bottomContainer.removeAllViews();

        if (state == Call.STATE_RINGING) {
            topCmdText.setText("root@red-os:~# ./telephony --incoming " + number);
            simBadgeText.setText("[ INCOMING : " + simLabel + " ]");
            callStatusText.setText("STATUS: INCOMING_PACKET_RINGING...");
            telemetryLine1.setText("> RADAR_STATE : 3-RING_OPPOSITE_GLITCH [INCOMING]");
            telemetryLine1.setTextColor(Color.parseColor("#FF1744"));
            radarView.setMode("incoming");
            buildIncomingBottomControls(number);

        } else if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
            topCmdText.setText("root@red-os:~# ./telephony --dial " + number);
            simBadgeText.setText("[ DIALING : " + simLabel + " ]");
            callStatusText.setText("STATUS: SYN_SENT [DIALING...]");
            telemetryLine1.setText("> RADAR_STATE : 3-RING_OPPOSITE_GLITCH [OUTGOING]");
            telemetryLine1.setTextColor(Color.parseColor("#00E676"));
            radarView.setMode("outgoing");
            buildOutgoingBottomControls();

        } else if (state == Call.STATE_HOLDING) {
            isOnHold = true;
            topCmdText.setText("root@red-os:~# ./telephony --call-hold");
            simBadgeText.setText("[ ON HOLD : " + simLabel + " | PAUSED ]");
            callStatusText.setText("STATUS: STREAM_FROZEN [HOLD_MODE]");
            telemetryLine1.setText("> RADAR_STATE : 3-RING_OPPOSITE_GLITCH [HOLD]");
            telemetryLine1.setTextColor(Color.parseColor("#FFD600"));
            radarView.setMode("hold");
            buildActiveBottomControls();

        } else if (state == Call.STATE_ACTIVE) {
            isOnHold = false;
            if (callStartTimeMs == 0L) callStartTimeMs = System.currentTimeMillis();
            topCmdText.setText("root@red-os:~# ./telephony --active-call");
            callStatusText.setText("STATUS: VOICE_TUNNEL_LOCKED [OK]");
            telemetryLine1.setText("> RADAR_STATE : 3-RING_OPPOSITE_GLITCH [ACTIVE]");
            telemetryLine1.setTextColor(Color.parseColor("#00E5FF"));
            radarView.setMode("active");
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);
            buildActiveBottomControls();
        }
    }

    private void buildIncomingBottomControls(final String number) {
        TextView smsHeader = new TextView(this);
        smsHeader.setText("-- ONE-TAP QUICK SMS REJECT --");
        smsHeader.setTextColor(Color.parseColor("#94A3B8"));
        smsHeader.setTypeface(Typeface.MONOSPACE);
        smsHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        smsHeader.setGravity(Gravity.CENTER);
        bottomContainer.addView(smsHeader);

        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setWeightSum(3f);
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsLp.topMargin = dp(6);

        String[] quickMsgs = {"Busy, Call Later", "On Bike", "Text Me"};
        for (final String msg : quickMsgs) {
            Button chip = makeCyberButton("[" + msg + "]", "#F8FAFC", "#1A0B0F", "#4A1521", 10f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        SmsManager.getDefault().sendTextMessage(number, null, msg, null, null);
                        Toast.makeText(TermuxCallActivity.this, "Sent SMS: " + msg, Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                    TermuxCallManager.endCall();
                }
            });
            chipsRow.addView(chip, lp);
        }
        bottomContainer.addView(chipsRow, chipsLp);

        TextView voiceHint = new TextView(this);
        voiceHint.setText("VOICE AI : SAY 'PICKUP' OR 'CUT' | [TAP TO MUTE RING]");
        voiceHint.setTextColor(Color.parseColor("#00E5FF"));
        voiceHint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        voiceHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        voiceHint.setGravity(Gravity.CENTER);
        voiceHint.setPadding(dp(8), dp(6), dp(8), dp(6));
        voiceHint.setBackground(makeBox("#14070B", "#3B0D16", 6, 1));
        voiceHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.stopRingtone(TermuxCallActivity.this);
            }
        });
        LinearLayout.LayoutParams vhLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vhLp.topMargin = dp(8);
        bottomContainer.addView(voiceHint, vhLp);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setWeightSum(2f);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actLp.topMargin = dp(10);

        Button btnReject = makeCyberButton("[ REJECT ]", "#FF5252", "#3B0712", "#FF1744", 14f);
        LinearLayout.LayoutParams rejLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        rejLp.rightMargin = dp(6);
        btnReject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.endCall();
            }
        });

        Button btnAnswer = makeCyberButton("[ ANSWER ]", "#00E676", "#062918", "#00E676", 14f);
        LinearLayout.LayoutParams ansLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        ansLp.leftMargin = dp(6);
        btnAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.answerCall();
            }
        });

        actionRow.addView(btnReject, rejLp);
        actionRow.addView(btnAnswer, ansLp);
        bottomContainer.addView(actionRow, actLp);
    }

    private void buildOutgoingBottomControls() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(3f);

        Button btnSpk = makeCyberButton("SPEAKER\n[" + (isSpeakerOn ? "ON" : "OFF") + "]",
                isSpeakerOn ? "#FF1744" : "#F8FAFC", isSpeakerOn ? "#2E0812" : "#1A0B0F",
                isSpeakerOn ? "#FF1744" : "#4A1521", 11f);
        btnSpk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSpeakerOn = !isSpeakerOn;
                TermuxCallManager.toggleSpeaker(TermuxCallActivity.this, isSpeakerOn);
                refreshUiFromCurrentCall();
            }
        });

        Button btnMute = makeCyberButton("MIC MUTE\n[" + (isMuted ? "ON" : "OFF") + "]",
                isMuted ? "#FF1744" : "#F8FAFC", "#1A0B0F", "#4A1521", 11f);
        btnMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isMuted = !isMuted;
                TermuxCallManager.toggleMute(TermuxCallActivity.this, isMuted);
                refreshUiFromCurrentCall();
            }
        });

        Button btnDtmf = makeCyberButton("KEYPAD\n[DTMF]", "#F8FAFC", "#1A0B0F", "#4A1521", 11f);
        btnDtmf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDtmfDialog();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(btnSpk, lp);
        row.addView(btnMute, lp);
        row.addView(btnDtmf, lp);
        bottomContainer.addView(row);

        addEndCallButton();
    }

    private void buildActiveBottomControls() {
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setWeightSum(3f);

        Button btnSpk = makeCyberButton("SPEAKER\n[" + (isSpeakerOn ? "ON" : "OFF") + "]",
                isSpeakerOn ? "#FF1744" : "#E2E8F0", isSpeakerOn ? "#2E0812" : "#1A0B0F",
                isSpeakerOn ? "#FF1744" : "#4A1521", 11f);
        btnSpk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSpeakerOn = !isSpeakerOn;
                TermuxCallManager.toggleSpeaker(TermuxCallActivity.this, isSpeakerOn);
                refreshUiFromCurrentCall();
            }
        });

        Button btnMute = makeCyberButton("MIC MUTE\n[" + (isMuted ? "ON" : "OFF") + "]",
                isMuted ? "#FF1744" : "#E2E8F0", "#1A0B0F", "#4A1521", 11f);
        btnMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isMuted = !isMuted;
                TermuxCallManager.toggleMute(TermuxCallActivity.this, isMuted);
                refreshUiFromCurrentCall();
            }
        });

        Button btnHold = makeCyberButton("HOLD\n[" + (isOnHold ? "ON" : "OFF") + "]",
                isOnHold ? "#FFD600" : "#E2E8F0", isOnHold ? "#1F1704" : "#1A0B0F",
                isOnHold ? "#FFD600" : "#4A1521", 11f);
        btnHold.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isOnHold = !isOnHold;
                TermuxCallManager.toggleHold(isOnHold);
                refreshUiFromCurrentCall();
            }
        });

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lp1.setMargins(dp(3), 0, dp(3), 0);
        row1.addView(btnSpk, lp1);
        row1.addView(btnMute, lp1);
        row1.addView(btnHold, lp1);
        bottomContainer.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setWeightSum(3f);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = dp(6);

        Button btnDtmf = makeCyberButton("KEYPAD\n[DTMF]", "#E2E8F0", "#1A0B0F", "#4A1521", 11f);
        btnDtmf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDtmfDialog();
            }
        });

        Button btnTermux = makeCyberButton("TERMUX\n[MINI]", "#FF5252", "#290710", "#FF1744", 11f);
        btnTermux.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveTaskToBack(true);
            }
        });

        Button btnContacts = makeCyberButton("CONTACTS\n[POPUP]", "#E2E8F0", "#1A0B0F", "#4A1521", 11f);
        btnContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TermuxCallActivity.this, TermuxContactsPopupActivity.class));
            }
        });

        row2.addView(btnDtmf, lp1);
        row2.addView(btnTermux, lp1);
        row2.addView(btnContacts, lp1);
        bottomContainer.addView(row2, row2Lp);

        addEndCallButton();
    }

    private void addEndCallButton() {
        Button btnEnd = makeCyberButton("[ END CALL / HANGUP ]", "#FF5252", "#3B0712", "#FF1744", 14.5f);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        endLp.topMargin = dp(10);
        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.endCall();
            }
        });
        bottomContainer.addView(btnEnd, endLp);
    }

    private void showDtmfDialog() {
        final String[] digits = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog);
        b.setTitle("SEND DTMF TONE");
        b.setItems(digits, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TermuxCallManager.sendDtmf(digits[which].charAt(0));
            }
        });
        b.show();
    }

    private Button makeCyberButton(String text, String textColor, String fillHex, String strokeHex, float textSizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor(textColor));
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        b.setGravity(Gravity.CENTER);
        b.setBackground(makeBox(fillHex, strokeHex, 10, 2));
        return b;
    }

    private GradientDrawable makeBox(String fillHex, String strokeHex, int cornerDp, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(dp(cornerDp));
        gd.setStroke(dp(strokeDp), Color.parseColor(strokeHex));
        return gd;
    }

    private String extractInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "ID";
        String clean = name.replace("VERIFIED ID :", "").trim();
        String[] parts = clean.split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        if (clean.length() >= 2) {
            return clean.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        return "ID";
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    @Override
    public void onCallStateChanged(Call call, int state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (call == null || state == Call.STATE_DISCONNECTED) {
                    timerHandler.removeCallbacks(timerRunnable);
                    finish();
                } else {
                    refreshUiFromCurrentCall();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        if (TermuxCallManager.listener == this) {
            TermuxCallManager.listener = null;
        }
        super.onDestroy();
    }
}
