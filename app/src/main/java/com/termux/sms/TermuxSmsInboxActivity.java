package com.termux.app.sms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class TermuxSmsInboxActivity extends Activity {

    private ListView listView;
    private ArrayList<SmsThreadItem> threadList;
    private SmsInboxAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Fixed Non-Resizing Standard Popup Window
        getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
            (int) (getResources().getDisplayMetrics().heightPixels * 0.75)
        );
        getWindow().setGravity(Gravity.CENTER);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        try {
            TermuxSmsAutoCleaner.cleanSpamAndOtps(this);
        } catch (Throwable ignored) {}

        buildInboxLayout();
        loadSmsThreads();
    }

    private void buildInboxLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D080A"));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.parseColor("#0D080A"));
        border.setStroke(dp(2), Color.parseColor("#FF1744"));
        border.setCornerRadius(dp(16));
        root.setBackground(border);

        TextView topBar = new TextView(this);
        topBar.setText("root@red-os:~# ./sms_engine --inbox");
        topBar.setTextColor(Color.parseColor("#FF5252"));
        topBar.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        topBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        topBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        topBar.setBackground(makeBox("#17090D", "#3B0D16", 6, 1));
        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(dp(6));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listLp.topMargin = dp(8);
        root.addView(listView, listLp);

        setContentView(root);
    }

    private void loadSmsThreads() {
        threadList = new ArrayList<>();
        try {
            ContentResolver resolver = getContentResolver();
            Uri uri = Uri.parse("content://sms/inbox");
            Cursor cursor = resolver.query(uri, new String[]{"address", "body", "date"}, null, null, "date DESC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String address = cursor.getString(0);
                    String body = cursor.getString(1);
                    boolean exists = false;
                    for (SmsThreadItem item : threadList) {
                        if (item.address != null && item.address.equals(address)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists && address != null) {
                        threadList.add(new SmsThreadItem(address, body));
                    }
                }
                cursor.close();
            }
        } catch (Exception ignored) {}

        adapter = new SmsInboxAdapter(this, threadList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SmsThreadItem item = threadList.get(position);
                Intent intent = new Intent(TermuxSmsInboxActivity.this, TermuxSmsChatActivity.class);
                intent.putExtra("sender_number", item.address);
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final SmsThreadItem item = threadList.get(position);
                new AlertDialog.Builder(TermuxSmsInboxActivity.this, android.R.style.Theme_DeviceDefault_Dialog)
                    .setTitle("DELETE CHAT")
                    .setMessage("Delete conversation with " + item.address + "?")
                    .setPositiveButton("DELETE", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            deleteConversation(item.address);
                        }
                    })
                    .setNegativeButton("CANCEL", null)
                    .show();
                return true;
            }
        });
    }

    private void deleteConversation(String address) {
        try {
            getContentResolver().delete(Uri.parse("content://sms/"), "address = ?", new String[]{address});
            Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show();
            loadSmsThreads();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private GradientDrawable makeBox(String fillHex, String strokeHex, int cornerDp, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(dp(cornerDp));
        gd.setStroke(dp(strokeDp), Color.parseColor(strokeHex));
        return gd;
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    private static class SmsThreadItem {
        String address, snippet;
        SmsThreadItem(String addr, String snip) {
            this.address = addr;
            this.snippet = snip;
        }
    }

    private class SmsInboxAdapter extends ArrayAdapter<SmsThreadItem> {
        public SmsInboxAdapter(Activity context, ArrayList<SmsThreadItem> items) {
            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SmsThreadItem item = getItem(position);
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            card.setBackground(makeBox("#120609", "#3B0D16", 8, 1));

            TextView tvNum = new TextView(getContext());
            tvNum.setText(item.address != null ? item.address : "Unknown");
            tvNum.setTextColor(Color.parseColor("#00E5FF"));
            tvNum.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            tvNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);

            TextView tvSnip = new TextView(getContext());
            tvSnip.setText(item.snippet != null ? item.snippet : "");
            tvSnip.setTextColor(Color.parseColor("#E2E8F0"));
            tvSnip.setTypeface(Typeface.MONOSPACE);
            tvSnip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            tvSnip.setSingleLine(true);
            tvSnip.setEllipsize(TextUtils.TruncateAt.END);
            tvSnip.setPadding(0, dp(2), 0, 0);

            card.addView(tvNum);
            card.addView(tvSnip);
            return card;
        }
    }
}
