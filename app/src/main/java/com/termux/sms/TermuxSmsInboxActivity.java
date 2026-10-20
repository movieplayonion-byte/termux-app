package com.termux.app.sms;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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

import java.util.ArrayList;

public class TermuxSmsInboxActivity extends Activity {

    private ListView listView;
    private ArrayList<SmsThreadItem> threadList;
    private SmsInboxAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Mobile screen se chhota aur centered floating popup style
        getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
            (int) (getResources().getDisplayMetrics().heightPixels * 0.82)
        );
        getWindow().setGravity(Gravity.CENTER);

        // 24-hours auto-clean background engine run karein
        TermuxSmsAutoCleaner.cleanSpamAndOtps(this);

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

        // Top Terminal Bar
        TextView topBar = new TextView(this);
        topBar.setText("root@red-os:~# ./sms_engine --inbox");
        topBar.setTextColor(Color.parseColor("#FF5252"));
        topBar.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        topBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        topBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        topBar.setBackground(makeBox("#17090D", "#3B0D16", 6, 1));
        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ListView for threads
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(dp(8));
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
                    threadList.add(new SmsThreadItem(address, body));
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
            LinearLayout card = new LinearLayout(TermuxSmsInboxActivity.appContext != null ? TermuxSmsInboxActivity.appContext : TermuxSmsInboxActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            card.setBackground(makeBox("#120609", "#3B0D16", 8, 1));

            TextView tvNum = new TextView(getContext());
            tvNum.setText(item.address != null ? item.address : "Unknown");
            tvNum.setTextColor(Color.parseColor("#00E5FF"));
            tvNum.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            tvNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);

            TextView tvSnip = new TextView(getContext());
            tvSnip.setText(item.snippet != null ? item.snippet : "");
            tvSnip.setTextColor(Color.parseColor("#E2E8F0"));
            tvSnip.setTypeface(Typeface.MONOSPACE);
            tvSnip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            tvSnip.setPadding(0, dp(3), 0, 0);

            card.addView(tvNum);
            card.addView(tvSnip);
            return card;
        }
    }
    public static Activity appContext;
}
