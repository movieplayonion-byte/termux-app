package com.termux.app.call;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TermuxCallLogPopupActivity extends Activity {

    private static class CallRecordItem {
        String number;
        String name;
        int type; // 1=INCOMING, 2=OUTGOING, 3=MISSED, 5=REJECTED
        long dateMillis;
        long durationSec;
        String simLabel;
        boolean isVideo;
        File recordingFile;
    }

    private static class GroupedContactLog {
        String keyNumber;
        String displayName;
        String displayNumber;
        CallRecordItem latest;
        final List<CallRecordItem> allCalls = new ArrayList<>();
    }

    private final List<CallRecordItem> rawLogs = new ArrayList<>();
    private final List<GroupedContactLog> groupedLogs = new ArrayList<>();
    private final List<GroupedContactLog> filteredGroups = new ArrayList<>();
    private List<File> cachedRecordingFiles = new ArrayList<>();

    private int activeFilter = 0; // 0=ALL, 3=MISSED, 1=INCOMING, 2=OUTGOING
    private String expandedKeyNumber = null;
    private GroupedContactLog drilldownGroup = null;

    // Root container so we can switch between Panel 1/2 (Main List) and Panel 3 (Show More Dossier)
    private LinearLayout rootContainer;
    private EditText searchInput;
    private TextView btnFilterAll, btnFilterMissed, btnFilterIn, btnFilterOut;
    private LogListAdapter listAdapter;
    private FullHistoryAdapter historyAdapter;

    // Inline & Dock Audio Player
    private MediaPlayer mediaPlayer;
    private File currentPlayingFile = null;
    private boolean isFastSpeed = false;
    private boolean isSpeakerOn = true;
    private final Handler waveHandler = new Handler(Looper.getMainLooper());
    private TextView activePlayerStatusText;
    private TextView activePlayerProgressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        setContentView(rootContainer);

        // Configure Floating Popup Dimensions over Termux (92% width, 84% height)
        if (getWindow() != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int width = (int) (dm.widthPixels * 0.94f);
            int height = (int) (dm.heightPixels * 0.84f);
            getWindow().setLayout(width, height);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        checkPermsAndLoad();
    }

    private void checkPermsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE
            };
            List<String> needed = new ArrayList<>();
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(p);
                }
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), 501);
                buildMainLogsPanel();
                return;
            }
        }
        scanRecordingFiles();
        queryDeviceCallLogs();
        buildMainLogsPanel();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        scanRecordingFiles();
        queryDeviceCallLogs();
        buildMainLogsPanel();
    }

    // ================= PANEL 1 & 2: MAIN CALL LOGS + ROW EXPAND =================
    private void buildMainLogsPanel() {
        drilldownGroup = null;
        rootContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 28, 28, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0D080A"));
        bg.setCornerRadius(36f);
        bg.setStroke(5, Color.parseColor("#FF1744"));
        card.setBackground(bg);

        // Top Terminal Header Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(22, 14, 22, 14);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(Color.parseColor("#1A090E"));
        topBg.setCornerRadius(14f);
        topBg.setStroke(2, Color.parseColor("#3B0D16"));
        topBar.setBackground(topBg);

        TextView cmdTitle = new TextView(this);
        cmdTitle.setText("root@red-os:~# call log --interactive");
        cmdTitle.setTextColor(Color.parseColor("#FF5252"));
        cmdTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        cmdTitle.setTextSize(11.5f);
        topBar.addView(cmdTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnCloseX = new TextView(this);
        btnCloseX.setText("[X]");
        btnCloseX.setTextColor(Color.parseColor("#FF1744"));
        btnCloseX.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnCloseX.setTextSize(13f);
        btnCloseX.setPadding(12, 2, 4, 2);
        btnCloseX.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(btnCloseX);
        card.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Search Bar + [CLEAR]
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(22, 8, 22, 8);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(Color.parseColor("#17070B"));
        sBg.setCornerRadius(18f);
        sBg.setStroke(3, Color.parseColor("#FF1744"));
        searchRow.setBackground(sBg);
        LinearLayout.LayoutParams srParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srParams.setMargins(0, 16, 0, 14);

        searchInput = new EditText(this);
        searchInput.setHint(">> Search Name / Number / SIM...");
        searchInput.setHintTextColor(Color.parseColor("#FF8A80"));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        searchInput.setTextSize(12.5f);
        searchInput.setSingleLine(true);
        searchInput.setBackground(null);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnClear = new TextView(this);
        btnClear.setText("[CLEAR]");
        btnClear.setTextColor(Color.parseColor("#00E5FF"));
        btnClear.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnClear.setTextSize(11f);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { searchInput.setText(""); }
        });
        searchRow.addView(btnClear);
        card.addView(searchRow, srParams);

        // 4 Filter Chips: [ALL] [MISSED] [INCOMING] [OUTGOING]
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        int missedCount = countGroupsByType(CallLog.Calls.MISSED_TYPE);

        btnFilterAll = createFilterChip("ALL (" + groupedLogs.size() + ")", 0, "#FF1744", "#2E0812");
        btnFilterMissed = createFilterChip("MISSED (" + missedCount + ")", CallLog.Calls.MISSED_TYPE, "#FF5252", "#1A090E");
        btnFilterIn = createFilterChip("INCOMING", CallLog.Calls.INCOMING_TYPE, "#00E5FF", "#0A1922");
        btnFilterOut = createFilterChip("OUTGOING", CallLog.Calls.OUTGOING_TYPE, "#00E676", "#071F14");

        addChipToRow(chipRow, btnFilterAll);
        addChipToRow(chipRow, btnFilterMissed);
        addChipToRow(chipRow, btnFilterIn);
        addChipToRow(chipRow, btnFilterOut);
        card.addView(chipRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Main Call Logs ListView
        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
        listView.setDividerHeight(14);
        listAdapter = new LogListAdapter();
        listView.setAdapter(listAdapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.setMargins(0, 14, 0, 12);
        card.addView(listView, lp);

        // Bottom Center ONLY: Round DIALPAD [KEYPAD] Reactor Button
        LinearLayout bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER);

        Button btnDialpad = createRoundReactorButton("DIALPAD\n[KEYPAD]", 145, "#00E5FF", "#7C3AED", "#1E1136", 11.5f);
        btnDialpad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(TermuxCallLogPopupActivity.this, TermuxCallActivity.class);
                i.putExtra("call_cli_cmd", "popup");
                startActivity(i);
                finish();
            }
        });
        bottomBox.addView(btnDialpad);

        TextView footerHint = new TextView(this);
        footerHint.setText("[TAP ROW: EXPAND LOG + MIC]     [HIDE]");
        footerHint.setTextColor(Color.parseColor("#94A3B8"));
        footerHint.setTypeface(Typeface.MONOSPACE);
        footerHint.setTextSize(10f);
        footerHint.setGravity(Gravity.CENTER);
        footerHint.setPadding(0, 8, 0, 2);
        footerHint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bottomBox.addView(footerHint);
        card.addView(bottomBox);

        rootContainer.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        applyFilters();
    }

    // ================= PANEL 3: ON 'SHOW MORE' -> FULL NUMBER DOSSIER & MIC LIST =================
    private void buildShowMoreDossierPanel(final GroupedContactLog group) {
        drilldownGroup = group;
        rootContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 28, 28, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0D080A"));
        bg.setCornerRadius(36f);
        bg.setStroke(5, Color.parseColor("#FF1744"));
        card.setBackground(bg);

        // Top Terminal Header Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(22, 14, 22, 14);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(Color.parseColor("#1A090E"));
        topBg.setCornerRadius(14f);
        topBg.setStroke(2, Color.parseColor("#3B0D16"));
        topBar.setBackground(topBg);

        TextView cmdTitle = new TextView(this);
        cmdTitle.setText("root@red-os:~# call history --num " + group.displayNumber);
        cmdTitle.setTextColor(Color.parseColor("#FF5252"));
        cmdTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        cmdTitle.setTextSize(10.5f);
        topBar.addView(cmdTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnCloseX = new TextView(this);
        btnCloseX.setText("[X]");
        btnCloseX.setTextColor(Color.parseColor("#FF1744"));
        btnCloseX.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnCloseX.setTextSize(13f);
        btnCloseX.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(btnCloseX);
        card.addView(topBar);

        // Dossier Profile Box
        LinearLayout profBox = new LinearLayout(this);
        profBox.setOrientation(LinearLayout.HORIZONTAL);
        profBox.setGravity(Gravity.CENTER_VERTICAL);
        profBox.setPadding(22, 20, 22, 20);
        GradientDrawable pBg = new GradientDrawable();
        pBg.setColor(Color.parseColor("#13091E"));
        pBg.setCornerRadius(22f);
        pBg.setStroke(3, Color.parseColor("#00E5FF"));
        profBox.setBackground(pBg);
        LinearLayout.LayoutParams profParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        profParams.setMargins(0, 16, 0, 14);

        // Initials Circle
        Button avatarCircle = createRoundReactorButton(getInitials(group.displayName), 88,
                "#00E5FF", "#7C3AED", "#1E1136", 12f);
        profBox.addView(avatarCircle);

        // Name + Number + Stats
        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding(20, 0, 12, 0);

        TextView pName = new TextView(this);
        pName.setText(group.displayName);
        pName.setTextColor(Color.WHITE);
        pName.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pName.setTextSize(14f);
        infoCol.addView(pName);

        TextView pNum = new TextView(this);
        pNum.setText(group.displayNumber + " • [" + group.latest.simLabel + "]");
        pNum.setTextColor(Color.parseColor("#FF5252"));
        pNum.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pNum.setTextSize(11f);
        infoCol.addView(pNum);

        long totalSec = 0;
        int recCount = 0;
        for (CallRecordItem c : group.allCalls) {
            totalSec += c.durationSec;
            if (c.recordingFile != null) recCount++;
        }
        TextView pStats = new TextView(this);
        pStats.setText(group.allCalls.size() + " Calls | Talk: " + (totalSec / 60) + "m " + (totalSec % 60) + "s | Rec: " + recCount);
        pStats.setTextColor(Color.parseColor("#00E676"));
        pStats.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        pStats.setTextSize(10f);
        infoCol.addView(pStats);

        profBox.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Direct CALL & VID Buttons in Header
        Button btnCallHead = createRoundReactorButton("CALL", 76, "#00E676", "#00E676", "#063B20", 9.5f);
        btnCallHead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.startOutgoingCall(TermuxCallLogPopupActivity.this, group.displayNumber, 0);
                finish();
            }
        });
        profBox.addView(btnCallHead);

        View spacer = new View(this);
        profBox.addView(spacer, new LinearLayout.LayoutParams(12, 10));

        Button btnVidHead = createRoundReactorButton("▣▶\nVID", 76, "#00E5FF", "#7C3AED", "#1E1136", 8.5f);
        btnVidHead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TermuxCallManager.startOutgoingVideoCall(TermuxCallLogPopupActivity.this, group.displayNumber, 0);
                finish();
            }
        });
        profBox.addView(btnVidHead);
        card.addView(profBox, profParams);

        // Top Active Audio Dock
        LinearLayout playerDock = new LinearLayout(this);
        playerDock.setOrientation(LinearLayout.VERTICAL);
        playerDock.setPadding(22, 16, 22, 16);
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(Color.parseColor("#062015"));
        dockBg.setCornerRadius(18f);
        dockBg.setStroke(3, Color.parseColor("#00E676"));
        playerDock.setBackground(dockBg);

        activePlayerStatusText = new TextView(this);
        activePlayerStatusText.setText("▶ TAP ANY 'MIC' BUTTON BELOW TO PLAY RECORDING");
        activePlayerStatusText.setTextColor(Color.parseColor("#00E676"));
        activePlayerStatusText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        activePlayerStatusText.setTextSize(10.5f);
        playerDock.addView(activePlayerStatusText);

        activePlayerProgressText = new TextView(this);
        activePlayerProgressText.setText("00:00 [--------------------] 00:00   [SPEED: 1x] [SPK: ON]");
        activePlayerProgressText.setTextColor(Color.parseColor("#A7F3D0"));
        activePlayerProgressText.setTypeface(Typeface.MONOSPACE);
        activePlayerProgressText.setTextSize(10f);
        activePlayerProgressText.setPadding(0, 6, 0, 0);
        activePlayerProgressText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlaybackSpeed();
            }
        });
        playerDock.addView(activePlayerProgressText);
        card.addView(playerDock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Full Call History List for this Number
        ListView historyList = new ListView(this);
        historyList.setDivider(new ColorDrawable(Color.TRANSPARENT));
        historyList.setDividerHeight(12);
        historyAdapter = new FullHistoryAdapter(group.allCalls);
        historyList.setAdapter(historyAdapter);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        hlp.setMargins(0, 14, 0, 12);
        card.addView(historyList, hlp);

        // Bottom Center: Round ◄ BACK [ALL LOGS] Reactor Button
        LinearLayout bottomBox = new LinearLayout(this);
        bottomBox.setOrientation(LinearLayout.VERTICAL);
        bottomBox.setGravity(Gravity.CENTER);

        Button btnBack = createRoundReactorButton("◄ BACK\n[ALL LOGS]", 145, "#FF5252", "#FF1744", "#2B0710", 11f);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAudioPlayback();
                buildMainLogsPanel();
            }
        });
        bottomBox.addView(btnBack);

        TextView footer = new TextView(this);
        footer.setText("[PURE CALL & VIDEO ENGINE]     [HIDE]");
        footer.setTextColor(Color.parseColor("#94A3B8"));
        footer.setTypeface(Typeface.MONOSPACE);
        footer.setTextSize(10f);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 8, 0, 2);
        footer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bottomBox.addView(footer);
        card.addView(bottomBox);

        rootContainer.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ================= ADAPTER FOR PANEL 1 & PANEL 2 (ACCORDION EXPAND) =================
    private class LogListAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredGroups.size(); }
        @Override public Object getItem(int position) { return filteredGroups.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final GroupedContactLog group = filteredGroups.get(position);
            final CallRecordItem item = group.latest;
            final boolean isExpanded = group.keyNumber.equals(expandedKeyNumber);

            String accentColor = getColorForType(item.type);
            String coreBg = getCoreBgForType(item.type);
            String iconSymbol = getIconForType(item.type);
            String typeLabel = getLabelForType(item.type);

            LinearLayout cardWrap = new LinearLayout(TermuxCallLogPopupActivity.this);
            cardWrap.setOrientation(LinearLayout.VERTICAL);
            cardWrap.setPadding(20, 18, 20, 18);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.parseColor(isExpanded ? "#12080E" : "#14080C"));
            cBg.setCornerRadius(22f);
            cBg.setStroke(isExpanded ? 4 : 2, Color.parseColor(isExpanded ? "#00E5FF" : "#3B0D16"));
            cardWrap.setBackground(cBg);

            // Top Main Row
            LinearLayout mainRow = new LinearLayout(TermuxCallLogPopupActivity.this);
            mainRow.setOrientation(LinearLayout.HORIZONTAL);
            mainRow.setGravity(Gravity.CENTER_VERTICAL);

            // Left Accent Strip
            View strip = new View(TermuxCallLogPopupActivity.this);
            GradientDrawable stBg = new GradientDrawable();
            stBg.setColor(Color.parseColor(accentColor));
            stBg.setCornerRadius(6f);
            strip.setBackground(stBg);
            LinearLayout.LayoutParams stP = new LinearLayout.LayoutParams(8, 96);
            stP.setMargins(0, 0, 16, 0);
            mainRow.addView(strip, stP);

            // Left Status Circle
            Button statusCircle = createRoundReactorButton(iconSymbol, 74, accentColor, accentColor, coreBg, 12f);
            statusCircle.setFocusable(false);
            statusCircle.setClickable(false);
            mainRow.addView(statusCircle);

            // Center Text Info
            LinearLayout textCol = new LinearLayout(TermuxCallLogPopupActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(18, 0, 10, 0);

            TextView tName = new TextView(TermuxCallLogPopupActivity.this);
            tName.setText(group.displayName + (isExpanded ? " [v]" : ""));
            tName.setTextColor(Color.WHITE);
            tName.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tName.setTextSize(13.5f);
            textCol.addView(tName);

            TextView tSub = new TextView(TermuxCallLogPopupActivity.this);
            tSub.setText("[" + item.simLabel + ":" + typeLabel + "] " + group.displayNumber);
            tSub.setTextColor(Color.parseColor(accentColor));
            tSub.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tSub.setTextSize(10f);
            textCol.addView(tSub);

            String durPart = (item.type == CallLog.Calls.MISSED_TYPE)
                    ? "Ring • No Answer"
                    : "Talk: " + formatDuration(item.durationSec);
            if (item.recordingFile != null && !isExpanded) {
                durPart += " [MIC-REC]";
            }

            TextView tTime = new TextView(TermuxCallLogPopupActivity.this);
            tTime.setText(formatRelativeTime(item.dateMillis) + " • " + durPart);
            tTime.setTextColor(Color.parseColor("#94A3B8"));
            tTime.setTypeface(Typeface.MONOSPACE);
            tTime.setTextSize(10f);
            textCol.addView(tTime);

            mainRow.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Right Side: [CALL] and [▣▶ VID] Round Reactor Buttons
            Button btnCall = createRoundReactorButton("CALL", 74, "#00E676", "#00E676", "#063B20", 9f);
            btnCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TermuxCallManager.startOutgoingCall(TermuxCallLogPopupActivity.this, group.displayNumber, 0);
                    finish();
                }
            });
            mainRow.addView(btnCall);

            View sp = new View(TermuxCallLogPopupActivity.this);
            mainRow.addView(sp, new LinearLayout.LayoutParams(10, 10));

            Button btnVid = createRoundReactorButton("▣▶\nVID", 74, "#00E5FF", "#7C3AED", "#1E1136", 8.5f);
            btnVid.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TermuxCallManager.startOutgoingVideoCall(TermuxCallLogPopupActivity.this, group.displayNumber, 0);
                    finish();
                }
            });
            mainRow.addView(btnVid);

            cardWrap.addView(mainRow);

            // Toggle Accordion Expand on Row Click
            mainRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isExpanded) {
                        expandedKeyNumber = null;
                        stopAudioPlayback();
                    } else {
                        expandedKeyNumber = group.keyNumber;
                    }
                    notifyDataSetChanged();
                }
            });

            // ================= PANEL 2: INLINE EXPANDED LATEST LOG + MIC PLAYER + SHOW MORE =================
            if (isExpanded) {
                LinearLayout recPanel = new LinearLayout(TermuxCallLogPopupActivity.this);
                recPanel.setOrientation(LinearLayout.HORIZONTAL);
                recPanel.setGravity(Gravity.CENTER_VERTICAL);
                recPanel.setPadding(20, 16, 20, 16);
                GradientDrawable rpBg = new GradientDrawable();
                rpBg.setColor(Color.parseColor("#170B24"));
                rpBg.setCornerRadius(18f);
                rpBg.setStroke(2, Color.parseColor("#7C3AED"));
                recPanel.setBackground(rpBg);
                LinearLayout.LayoutParams rpParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rpParams.setMargins(0, 16, 0, 12);

                final File recFile = findLatestRecordingForGroup(group);
                final boolean isPlayingThis = (recFile != null && recFile.equals(currentPlayingFile) && mediaPlayer != null && mediaPlayer.isPlaying());

                Button btnMic = createRoundReactorButton(isPlayingThis ? "MIC\n■" : "MIC\n▶", 86,
                        "#00E5FF", "#00E676", "#082E38", 9.5f);
                recPanel.addView(btnMic);

                LinearLayout waveCol = new LinearLayout(TermuxCallLogPopupActivity.this);
                waveCol.setOrientation(LinearLayout.VERTICAL);
                waveCol.setPadding(18, 0, 0, 0);

                final TextView recTitle = new TextView(TermuxCallLogPopupActivity.this);
                recTitle.setText(recFile != null
                        ? "LATEST_REC: " + recFile.getName()
                        : "LATEST_LOG: " + formatFullDate(item.dateMillis));
                recTitle.setTextColor(Color.parseColor("#00E5FF"));
                recTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                recTitle.setTextSize(10f);
                waveCol.addView(recTitle);

                final TextView waveText = new TextView(TermuxCallLogPopupActivity.this);
                waveText.setText(recFile != null
                        ? (isPlayingThis ? "||.|..||||.|||.|||||..||..|.|| [PLAYING]" : "||.|..||||.|||.|||||..|| [TAP MIC TO PLAY]")
                        : "NO AUDIO REC FILE FOUND FOR THIS CALL");
                waveText.setTextColor(Color.parseColor("#00E676"));
                waveText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                waveText.setTextSize(10.5f);
                waveText.setPadding(0, 4, 0, 4);
                waveCol.addView(waveText);

                final TextView metaText = new TextView(TermuxCallLogPopupActivity.this);
                metaText.setText("Duration: " + formatDuration(item.durationSec) +
                        "   [" + (isFastSpeed ? "1.5x" : "1.0x") + "]  [SPK: " + (isSpeakerOn ? "ON" : "EAR") + "]");
                metaText.setTextColor(Color.parseColor("#E2E8F0"));
                metaText.setTypeface(Typeface.MONOSPACE);
                metaText.setTextSize(9.5f);
                metaText.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        togglePlaybackSpeed();
                        notifyDataSetChanged();
                    }
                });
                waveCol.addView(metaText);

                btnMic.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (recFile == null) {
                            TermuxCallManager.showToast(TermuxCallLogPopupActivity.this, "No recording found for this call");
                            return;
                        }
                        togglePlayFile(recFile);
                        notifyDataSetChanged();
                    }
                });

                recPanel.addView(waveCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                cardWrap.addView(recPanel, rpParams);

                // One-line "SHOW MORE" Bar
                TextView showMoreBar = new TextView(TermuxCallLogPopupActivity.this);
                showMoreBar.setText("[ >> SHOW MORE : VIEW ALL " + group.allCalls.size() + " LOGS & RECORDINGS << ]");
                showMoreBar.setTextColor(Color.parseColor("#FF8A80"));
                showMoreBar.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                showMoreBar.setTextSize(10f);
                showMoreBar.setGravity(Gravity.CENTER);
                showMoreBar.setPadding(16, 16, 16, 16);
                GradientDrawable smBg = new GradientDrawable();
                smBg.setColor(Color.parseColor("#260710"));
                smBg.setCornerRadius(14f);
                smBg.setStroke(2, Color.parseColor("#FF1744"));
                showMoreBar.setBackground(smBg);
                showMoreBar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        stopAudioPlayback();
                        buildShowMoreDossierPanel(group);
                    }
                });
                cardWrap.addView(showMoreBar, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            return cardWrap;
        }
    }

    // ================= ADAPTER FOR PANEL 3 (ALL CALLS OF SELECTED NUMBER) =================
    private class FullHistoryAdapter extends BaseAdapter {
        private final List<CallRecordItem> list;
        FullHistoryAdapter(List<CallRecordItem> l) { this.list = l; }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int i) { return list.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final CallRecordItem c = list.get(position);
            String col = getColorForType(c.type);
            String bg = getCoreBgForType(c.type);
            String icon = c.isVideo ? "▣▶" : getIconForType(c.type);
            String modeStr = c.isVideo ? "VIDEO CALL" : getLabelForType(c.type);

            LinearLayout row = new LinearLayout(TermuxCallLogPopupActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(20, 16, 20, 16);
            GradientDrawable rBg = new GradientDrawable();
            rBg.setColor(Color.parseColor("#14080C"));
            rBg.setCornerRadius(18f);
            rBg.setStroke(2, Color.parseColor("#3B0D16"));
            row.setBackground(rBg);

            Button stCircle = createRoundReactorButton(icon, 66, col, col, bg, 10.5f);
            stCircle.setFocusable(false);
            stCircle.setClickable(false);
            row.addView(stCircle);

            LinearLayout info = new LinearLayout(TermuxCallLogPopupActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(18, 0, 12, 0);

            TextView tDate = new TextView(TermuxCallLogPopupActivity.this);
            tDate.setText((position + 1) + ". " + formatFullDate(c.dateMillis));
            tDate.setTextColor(Color.WHITE);
            tDate.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tDate.setTextSize(12f);
            info.addView(tDate);

            TextView tDet = new TextView(TermuxCallLogPopupActivity.this);
            String dur = (c.type == CallLog.Calls.MISSED_TYPE)
                    ? "Missed (No Rec)"
                    : "Talk: " + formatDuration(c.durationSec);
            tDet.setText("[" + c.simLabel + " • " + modeStr + "] • " + dur);
            tDet.setTextColor(Color.parseColor(col));
            tDet.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tDet.setTextSize(10f);
            info.addView(tDet);

            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Right Side: Round MIC Recording Button
            if (c.recordingFile != null) {
                final boolean playing = (c.recordingFile.equals(currentPlayingFile) && mediaPlayer != null && mediaPlayer.isPlaying());
                Button btnMic = createRoundReactorButton(playing ? "MIC\n■" : "MIC\n▶", 78,
                        playing ? "#00E676" : "#00E5FF",
                        playing ? "#00E676" : "#7C3AED",
                        playing ? "#063B20" : "#1E1136", 9f);
                btnMic.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        togglePlayFile(c.recordingFile);
                        notifyDataSetChanged();
                    }
                });
                row.addView(btnMic);
            } else {
                Button btnNoRec = createRoundReactorButton("—", 70, "#64748B", "#475569", "#1E293B", 11f);
                btnNoRec.setEnabled(false);
                row.addView(btnNoRec);
            }

            return row;
        }
    }

    // ================= CALL LOG & RECORDING MATCHING ENGINE =================
    private void scanRecordingFiles() {
        cachedRecordingFiles.clear();
        String[] dirs = {
                "/sdcard/TermuxCallRecords",
                "/sdcard/Recordings/Call",
                "/sdcard/Call",
                "/sdcard/MIUI/sound_recorder/call_rec",
                "/sdcard/Sounds/CallRecord"
        };
        for (String d : dirs) {
            File folder = new File(d);
            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) cachedRecordingFiles.add(f);
                    }
                }
            }
        }
    }

    private File matchRecordingForCall(String number, long callDateMillis, long durationSec) {
        if (cachedRecordingFiles.isEmpty()) return null;
        String cleanNum = normalizeNumber(number);
        File bestMatch = null;
        long bestDiff = Long.MAX_VALUE;

        long callEndEst = callDateMillis + (durationSec * 1000L);
        for (File f : cachedRecordingFiles) {
            long diffStart = Math.abs(f.lastModified() - callDateMillis);
            long diffEnd = Math.abs(f.lastModified() - callEndEst);
            long diff = Math.min(diffStart, diffEnd);

            // Match within 3 minutes window or filename containing number
            if (diff <= 180000L || (!cleanNum.isEmpty() && f.getName().contains(cleanNum))) {
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestMatch = f;
                }
            }
        }
        return bestMatch;
    }

    private File findLatestRecordingForGroup(GroupedContactLog group) {
        for (CallRecordItem c : group.allCalls) {
            if (c.recordingFile != null) return c.recordingFile;
        }
        return null;
    }

    private void queryDeviceCallLogs() {
        rawLogs.clear();
        groupedLogs.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Cursor c = null;
        try {
            c = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null, null, null,
                    CallLog.Calls.DATE + " DESC"
            );
            if (c != null) {
                int idxNum = c.getColumnIndex(CallLog.Calls.NUMBER);
                int idxName = c.getColumnIndex(CallLog.Calls.CACHED_NAME);
                int idxType = c.getColumnIndex(CallLog.Calls.TYPE);
                int idxDate = c.getColumnIndex(CallLog.Calls.DATE);
                int idxDur = c.getColumnIndex(CallLog.Calls.DURATION);
                int idxSub = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID);
                int idxFeat = c.getColumnIndex(CallLog.Calls.FEATURES);

                Map<String, GroupedContactLog> map = new LinkedHashMap<>();
                int count = 0;

                while (c.moveToNext() && count < 250) {
                    count++;
                    CallRecordItem item = new CallRecordItem();
                    item.number = (idxNum != -1 && c.getString(idxNum) != null) ? c.getString(idxNum) : "Unknown";
                    item.name = (idxName != -1) ? c.getString(idxName) : null;
                    if (item.name == null || item.name.trim().isEmpty()) {
                        item.name = TermuxCallManager.getContactName(this, item.number);
                    }
                    item.type = (idxType != -1) ? c.getInt(idxType) : CallLog.Calls.INCOMING_TYPE;
                    item.dateMillis = (idxDate != -1) ? c.getLong(idxDate) : System.currentTimeMillis();
                    item.durationSec = (idxDur != -1) ? c.getLong(idxDur) : 0L;

                    int features = (idxFeat != -1) ? c.getInt(idxFeat) : 0;
                    item.isVideo = (features & CallLog.Calls.FEATURES_VIDEO) == CallLog.Calls.FEATURES_VIDEO;

                    String accId = (idxSub != -1) ? c.getString(idxSub) : null;
                    item.simLabel = resolveSimLabel(accId);
                    item.recordingFile = matchRecordingForCall(item.number, item.dateMillis, item.durationSec);

                    rawLogs.add(item);

                    String key = normalizeNumber(item.number);
                    if (key.isEmpty()) key = item.number;

                    GroupedContactLog grp = map.get(key);
                    if (grp == null) {
                        grp = new GroupedContactLog();
                        grp.keyNumber = key;
                        grp.displayNumber = item.number;
                        grp.displayName = (item.name != null && !item.name.isEmpty()) ? item.name : "Unknown Number";
                        grp.latest = item;
                        map.put(key, grp);
                    }
                    grp.allCalls.add(item);
                }
                groupedLogs.addAll(map.values());
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    private String resolveSimLabel(String accountId) {
        if (accountId == null || accountId.isEmpty()) return "SIM 1";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) {
                    List<PhoneAccountHandle> handles = tm.getCallCapablePhoneAccounts();
                    for (int i = 0; i < handles.size(); i++) {
                        if (accountId.contains(handles.get(i).getId())) {
                            return "SIM " + (i + 1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        if (accountId.equals("2") || accountId.endsWith("1")) return "SIM 2";
        return "SIM 1";
    }

    private void applyFilters() {
        filteredGroups.clear();
        String q = (searchInput != null && searchInput.getText() != null)
                ? searchInput.getText().toString().trim().toLowerCase(Locale.ROOT) : "";

        for (GroupedContactLog g : groupedLogs) {
            if (activeFilter != 0 && g.latest.type != activeFilter) {
                continue;
            }
            if (!q.isEmpty()) {
                boolean matchName = g.displayName.toLowerCase(Locale.ROOT).contains(q);
                boolean matchNum = g.displayNumber.toLowerCase(Locale.ROOT).contains(q);
                boolean matchSim = g.latest.simLabel.toLowerCase(Locale.ROOT).contains(q);
                if (!matchName && !matchNum && !matchSim) continue;
            }
            filteredGroups.add(g);
        }
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    // ================= AUDIO RECORDING PLAYER ENGINE =================
    private void togglePlayFile(File file) {
        try {
            if (currentPlayingFile != null && currentPlayingFile.equals(file) && mediaPlayer != null && mediaPlayer.isPlaying()) {
                stopAudioPlayback();
                return;
            }
            stopAudioPlayback();
            currentPlayingFile = file;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            applyPlaybackSpeed();

            if (activePlayerStatusText != null) {
                activePlayerStatusText.setText("▶ NOW PLAYING: " + file.getName() + " [" + (isFastSpeed ? "1.5x" : "1.0x") + "]");
            }
            startProgressUpdater();

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopAudioPlayback();
                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                    if (historyAdapter != null) historyAdapter.notifyDataSetChanged();
                }
            });
        } catch (Exception e) {
            TermuxCallManager.showToast(this, "Play Error: " + e.getMessage());
        }
    }

    private void togglePlaybackSpeed() {
        isFastSpeed = !isFastSpeed;
        applyPlaybackSpeed();
        TermuxCallManager.showToast(this, "Playback Speed: " + (isFastSpeed ? "1.5x" : "1.0x"));
    }

    private void applyPlaybackSpeed() {
        if (mediaPlayer != null && mediaPlayer.isPlaying() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PlaybackParams params = new PlaybackParams();
                params.setSpeed(isFastSpeed ? 1.5f : 1.0f);
                mediaPlayer.setPlaybackParams(params);
            } catch (Exception ignored) {}
        }
    }

    private void startProgressUpdater() {
        waveHandler.removeCallbacksAndMessages(null);
        waveHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int pos = mediaPlayer.getCurrentPosition() / 1000;
                    int dur = Math.max(1, mediaPlayer.getDuration() / 1000);
                    int filled = (pos * 18) / dur;
                    StringBuilder bar = new StringBuilder("[");
                    for (int i = 0; i < 18; i++) {
                        bar.append(i == filled ? "O" : (i < filled ? "=" : "-"));
                    }
                    bar.append("]");
                    if (activePlayerProgressText != null) {
                        activePlayerProgressText.setText(formatDuration(pos) + " " + bar + " " + formatDuration(dur) +
                                "   [SPEED: " + (isFastSpeed ? "1.5x" : "1x") + "]");
                    }
                    waveHandler.postDelayed(this, 500L);
                }
            }
        });
    }

    private void stopAudioPlayback() {
        waveHandler.removeCallbacksAndMessages(null);
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            }
        } catch (Exception ignored) {}
        mediaPlayer = null;
        currentPlayingFile = null;
    }

    // ================= UI HELPERS =================
    private TextView createFilterChip(String text, final int filterType, String strokeCol, String bgCol) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(strokeCol));
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setTextSize(10f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(12, 14, 12, 14);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgCol));
        gd.setCornerRadius(14f);
        gd.setStroke(2, Color.parseColor(strokeCol));
        tv.setBackground(gd);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeFilter = filterType;
                applyFilters();
            }
        });
        return tv;
    }

    private void addChipToRow(LinearLayout row, TextView chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(5, 0, 5, 0);
        row.addView(chip, lp);
    }

    private Button createRoundReactorButton(String text, int sizePx, String textCol, String borderCol, String fillCol, float textSizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(textCol));
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setTextSize(textSizeSp);
        b.setPadding(4, 4, 4, 4);
        b.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(fillCol));
        gd.setStroke(4, Color.parseColor(borderCol));
        b.setBackground(gd);
        b.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        return b;
    }

    private int countGroupsByType(int type) {
        int c = 0;
        for (GroupedContactLog g : groupedLogs) {
            if (g.latest.type == type) c++;
        }
        return c;
    }

    private String getColorForType(int type) {
        if (type == CallLog.Calls.MISSED_TYPE) return "#FF1744";
        if (type == CallLog.Calls.INCOMING_TYPE) return "#00E5FF";
        if (type == CallLog.Calls.OUTGOING_TYPE) return "#00E676";
        return "#FFD600";
    }

    private String getCoreBgForType(int type) {
        if (type == CallLog.Calls.MISSED_TYPE) return "#2B0710";
        if (type == CallLog.Calls.INCOMING_TYPE) return "#07222E";
        if (type == CallLog.Calls.OUTGOING_TYPE) return "#063B20";
        return "#241A05";
    }

    private String getIconForType(int type) {
        if (type == CallLog.Calls.MISSED_TYPE) return "↙!";
        if (type == CallLog.Calls.INCOMING_TYPE) return "↙";
        if (type == CallLog.Calls.OUTGOING_TYPE) return "↗";
        return "✕";
    }

    private String getLabelForType(int type) {
        if (type == CallLog.Calls.MISSED_TYPE) return "MISSED";
        if (type == CallLog.Calls.INCOMING_TYPE) return "INCOMING";
        if (type == CallLog.Calls.OUTGOING_TYPE) return "OUTGOING";
        return "REJECTED";
    }

    private String normalizeNumber(String num) {
        if (num == null) return "";
        String d = num.replaceAll("[^0-9]", "");
        if (d.length() > 10) return d.substring(d.length() - 10);
        return d;
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "ID";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        return name.trim().substring(0, Math.min(2, name.trim().length())).toUpperCase(Locale.ROOT);
    }

    private String formatDuration(long sec) {
        return String.format(Locale.ROOT, "%02dm %02ds", sec / 60, sec % 60);
    }

    private String formatRelativeTime(long millis) {
        long diffMin = Math.max(0L, (System.currentTimeMillis() - millis) / 60000L);
        if (diffMin < 1) return "Just now";
        if (diffMin < 60) return diffMin + "m ago";
        long diffHr = diffMin / 60;
        if (diffHr < 24) return diffHr + "h ago";
        return new SimpleDateFormat("dd MMM, hh:mm a", Locale.ROOT).format(new Date(millis));
    }

    private String formatFullDate(long millis) {
        return new SimpleDateFormat("dd MMM, hh:mm a", Locale.ROOT).format(new Date(millis));
    }

    @Override
    protected void onDestroy() {
        stopAudioPlayback();
        super.onDestroy();
    }
}
