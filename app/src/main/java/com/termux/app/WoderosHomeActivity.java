package com.termux.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.app.call.TermuxCallActivity;
import com.termux.app.sms.TermuxSmsInboxActivity;
import com.termux.shared.logger.Logger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * woderOS home (launcher) screen.
 *
 * A simple, fast home screen in the woderOS style: black background, green
 * accents, a clock header, a search box, a grid of every launchable app and
 * a dock with Terminal / Phone / Messages / Contacts. Registered with the
 * HOME category in the manifest, so the user can pick woderOS as the
 * default home app.
 */
public class WoderosHomeActivity extends Activity {

    private static final String LOG_TAG = "WoderosHome";
    private static final int GREEN = Color.rgb(0, 230, 118);
    private static final int DARK_PANEL = Color.rgb(16, 16, 16);

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextView mClockView;
    private AppGridAdapter mAdapter;

    private final Runnable mClockTick = new Runnable() {
        @Override
        public void run() {
            if (mClockView != null) {
                mClockView.setText(new SimpleDateFormat("EEE, d MMM  •  hh:mm a", Locale.getDefault()).format(new Date()));
            }
            mHandler.postDelayed(this, 20000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad / 2);

        TextView title = new TextView(this);
        title.setText("woderOS");
        title.setTextColor(GREEN);
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        root.addView(title);

        mClockView = new TextView(this);
        mClockView.setTextColor(Color.LTGRAY);
        mClockView.setTextSize(14);
        root.addView(mClockView);

        EditText search = new EditText(this);
        search.setHint("Apps khojo...");
        search.setHintTextColor(Color.GRAY);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackgroundColor(DARK_PANEL);
        int searchPad = dp(10);
        search.setPadding(searchPad, searchPad, searchPad, searchPad);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = dp(12);
        root.addView(search, searchParams);

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(10));
        grid.setHorizontalSpacing(dp(6));
        grid.setPadding(0, dp(12), 0, dp(8));
        mAdapter = new AppGridAdapter();
        grid.setAdapter(mAdapter);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(grid, gridParams);

        grid.setOnItemClickListener((parent, view, position, id) -> {
            ResolveInfo info = mAdapter.getItem(position);
            if (info == null) return;
            try {
                ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                Intent launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory(Intent.CATEGORY_LAUNCHER);
                launch.setComponent(component);
                launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launch);
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "App launch failed: " + e.getMessage());
                Toast.makeText(this, "App nahi khul paya", Toast.LENGTH_SHORT).show();
            }
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            ResolveInfo info = mAdapter.getItem(position);
            if (info == null) return true;
            try {
                Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                details.setData(Uri.parse("package:" + info.activityInfo.packageName));
                startActivity(details);
            } catch (Exception ignored) {}
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setBackgroundColor(DARK_PANEL);
        dock.setPadding(dp(6), dp(8), dp(6), dp(8));
        dock.addView(dockButton("Terminal", v -> startActivity(new Intent(this, TermuxActivity.class))));
        dock.addView(dockButton("Phone", v -> {
            try {
                startActivity(new Intent(this, TermuxCallActivity.class));
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_DIAL));
            }
        }));
        dock.addView(dockButton("Messages", v -> {
            try {
                startActivity(new Intent(this, TermuxSmsInboxActivity.class));
            } catch (Exception e) {
                Toast.makeText(this, "Messages nahi khul paye", Toast.LENGTH_SHORT).show();
            }
        }));
        dock.addView(dockButton("Contacts", v -> startActivity(new Intent(this, WoderosContactsActivity.class))));
        dock.addView(dockButton("Admin", v -> startActivity(new Intent(this, WoderosAdminActivity.class))));
        dock.addView(dockButton("Theme", v -> startActivity(new Intent(this, WoderosThemeActivity.class))));
        root.addView(dock);

        setContentView(root);
        loadApps();
    }

    private TextView dockButton(String label, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(GREEN);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), dp(10), dp(4), dp(10));
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private void loadApps() {
        new Thread(() -> {
            List<ResolveInfo> apps = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                Intent query = new Intent(Intent.ACTION_MAIN);
                query.addCategory(Intent.CATEGORY_LAUNCHER);
                apps = pm.queryIntentActivities(query, 0);
                Collections.sort(apps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                    String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "App list failed: " + e.getMessage());
            }
            List<ResolveInfo> result = apps;
            mHandler.post(() -> mAdapter.setApps(result));
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mClockTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mClockTick);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class AppGridAdapter extends BaseAdapter {
        private final List<ResolveInfo> mAll = new ArrayList<>();
        private final List<ResolveInfo> mShown = new ArrayList<>();

        void setApps(List<ResolveInfo> apps) {
            mAll.clear();
            mAll.addAll(apps);
            mShown.clear();
            mShown.addAll(apps);
            notifyDataSetChanged();
        }

        void filter(String text) {
            String needle = text == null ? "" : text.trim().toLowerCase(Locale.getDefault());
            mShown.clear();
            PackageManager pm = getPackageManager();
            for (ResolveInfo info : mAll) {
                if (needle.isEmpty() || String.valueOf(info.loadLabel(pm)).toLowerCase(Locale.getDefault()).contains(needle)) {
                    mShown.add(info);
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return mShown.size(); }
        @Override public ResolveInfo getItem(int position) {
            return position >= 0 && position < mShown.size() ? mShown.get(position) : null;
        }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(WoderosHomeActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(2), dp(6), dp(2), dp(6));
                icon = new ImageView(WoderosHomeActivity.this);
                int iconSize = dp(46);
                icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
                label = new TextView(WoderosHomeActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(11);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(icon);
                cell.addView(label);
            }
            ResolveInfo info = getItem(position);
            if (info != null) {
                PackageManager pm = getPackageManager();
                Drawable drawable = null;
                try { drawable = info.loadIcon(pm); } catch (Exception ignored) {}
                icon.setImageDrawable(drawable);
                label.setText(info.loadLabel(pm));
            }
            return cell;
        }
    }
}
