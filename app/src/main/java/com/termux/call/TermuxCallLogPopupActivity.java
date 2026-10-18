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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
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
        int type;
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

    private LinearLayout rootContainer;
    private EditText searchInput;
    private LogListAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        setContentView(rootContainer);

        if (getWindow() != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int width = (int) (dm.widthPixels * 0.94f);
            int height = (int) (dm.heightPixels * 0.84f);
            getWindow().setLayout(width, height);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        // Load logs safely in background async task to prevent any ANR crash!
        new LoadLogsTask().execute();
    }

    private class LoadLogsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                // Scan recordings & query call logs in background thread
                String[] dirs = {"/sdcard/TermuxCallRecords", "/sdcard/Recordings/Call", "/sdcard/Call"};
                List<File> recs = new ArrayList<>();
                for (String d : dirs) {
                    File f = new File(d);
                    if (f.exists() && f.isDirectory()) {
                        File[] list = f.listFiles();
                        if (list != null) for (File sf : list) if (sf.isFile()) recs.add(sf);
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                    return null;
                }

                Cursor c = getContentResolver().query(
                        CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC");
                if (c != null) {
                    int idxNum = c.getColumnIndex(CallLog.Calls.NUMBER);
                    int idxName = c.getColumnIndex(CallLog.Calls.CACHED_NAME);
                    int idxType = c.getColumnIndex(CallLog.Calls.TYPE);
                    int idxDate = c.getColumnIndex(CallLog.Calls.DATE);
                    int idxDur = c.getColumnIndex(CallLog.Calls.DURATION);
                    int idxFeat = c.getColumnIndex(CallLog.Calls.FEATURES);

                    Map<String, GroupedContactLog> map = new LinkedHashMap<>();
                    int count = 0;
                    while (c.moveToNext() && count < 150) {
                        count++;
                        CallRecordItem item = new CallRecordItem();
                        item.number = (idxNum != -1 && c.getString(idxNum) != null) ? c.getString(idxNum) : "Unknown";
                        item.name = (idxName != -1) ? c.getString(idxName) : null;
                        item.type = (idxType != -1) ? c.getInt(idxType) : CallLog.Calls.INCOMING_TYPE;
                        item.dateMillis = (idxDate != -1) ? c.getLong(idxDate) : System.currentTimeMillis();
                        item.durationSec = (idxDur != -1) ? c.getLong(idxDur) : 0L;
                        int features = (idxFeat != -1) ? c.getInt(idxFeat) : 0;
                        item.isVideo = (features & CallLog.Calls.FEATURES_VIDEO) == CallLog.Calls.FEATURES_VIDEO;
                        item.simLabel = "SIM 1";

                        rawLogs.add(item);
                        String key = item.number.replaceAll("[^0-9]", "");
                        if (key.length() > 10) key = key.substring(key.length() - 10);
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
                    c.close();
                    groupedLogs.addAll(map.values());
                }
            } catch (Exception ignored) {}
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            buildMainLogsPanel();
        }
    }

    private void buildMainLogsPanel() {
        rootContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 28, 28, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0D080A"));
        bg.setCornerRadius(36f);
        bg.setStroke(5, Color.parseColor("#FF1744"));
        card.setBackground(bg);

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
        btnCloseX.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(btnCloseX);
        card.addView(topBar);

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
        searchInput.setHint(">> Search Name / Number...");
        searchInput.setHintTextColor(Color.parseColor("#FF8A80"));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        searchInput.setTextSize(12.5f);
        searchInput.setSingleLine(true);
        searchInput.setBackground(null);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(searchRow, srParams);

        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
        listView.setDividerHeight(14);
        listAdapter = new LogListAdapter();
        listView.setAdapter(listAdapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.setMargins(0, 14, 0, 12);
        card.addView(listView, lp);

        Button btnDialpad = createRoundReactorButton("DIALPAD\n[KEYPAD]", 130, "#00E5FF", "#7C3AED", "#1E1136", 11f);
        btnDialpad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(TermuxCallLogPopupActivity.this, TermuxCallActivity.class);
                i.putExtra("call_cli_cmd", "popup");
                startActivity(i);
                finish();
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(130, 130);
        bp.gravity = Gravity.CENTER;
        card.addView(btnDialpad, bp);

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

    private void applyFilters() {
        filteredGroups.clear();
        String q = (searchInput != null && searchInput.getText() != null)
                ? searchInput.getText().toString().trim().toLowerCase(Locale.ROOT) : "";

        for (GroupedContactLog g : groupedLogs) {
            if (!q.isEmpty()) {
                boolean matchName = g.displayName.toLowerCase(Locale.ROOT).contains(q);
                boolean matchNum = g.displayNumber.toLowerCase(Locale.ROOT).contains(q);
                if (!matchName && !matchNum) continue;
            }
            filteredGroups.add(g);
        }
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    private class LogListAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredGroups.size(); }
        @Override public Object getItem(int position) { return filteredGroups.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final GroupedContactLog group = filteredGroups.get(position);
            final CallRecordItem item = group.latest;

            String accentColor = getColorForType(item.type);
            String coreBg = getCoreBgForType(item.type);
            String iconSymbol = getIconForType(item.type);

            LinearLayout cardWrap = new LinearLayout(TermuxCallLogPopupActivity.this);
            cardWrap.setOrientation(LinearLayout.HORIZONTAL);
            cardWrap.setGravity(Gravity.CENTER_VERTICAL);
            cardWrap.setPadding(20, 18, 20, 18);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.parseColor("#14080C"));
            cBg.setCornerRadius(22f);
            cBg.setStroke(2, Color.parseColor("#3B0D16"));
            cardWrap.setBackground(cBg);

            Button statusCircle = createRoundReactorButton(iconSymbol, 74, accentColor, accentColor, coreBg, 12f);
            statusCircle.setFocusable(false);
            statusCircle.setClickable(false);
            cardWrap.addView(statusCircle);

            LinearLayout textCol = new LinearLayout(TermuxCallLogPopupActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(18, 0, 10, 0);

            TextView tName = new TextView(TermuxCallLogPopupActivity.this);
            tName.setText(group.displayName);
            tName.setTextColor(Color.WHITE);
            tName.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tName.setTextSize(13.5f);
            textCol.addView(tName);

            TextView tSub = new TextView(TermuxCallLogPopupActivity.this);
            tSub.setText(group.displayNumber);
            tSub.setTextColor(Color.parseColor(accentColor));
            tSub.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tSub.setTextSize(10.5f);
            textCol.addView(tSub);

            cardWrap.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button btnCall = createRoundReactorButton("CALL", 74, "#00E676", "#00E676", "#063B20", 9f);
            btnCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TermuxCallManager.startOutgoingCall(TermuxCallLogPopupActivity.this, group.displayNumber, 0);
                    finish();
                }
            });
            cardWrap.addView(btnCall);

            return cardWrap;
        }
    }

    private Button createRoundReactorButton(String text, int sizePx, String textCol, String borderCol, String fillCol, float textSizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(textCol));
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setTextSize(textSizeSp);
        b.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(fillCol));
        gd.setStroke(4, Color.parseColor(borderCol));
        b.setBackground(gd);
        b.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        return b;
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
}
