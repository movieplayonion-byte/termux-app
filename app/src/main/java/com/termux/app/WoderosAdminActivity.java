package com.termux.app;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.app.call.TermuxCallManager;
import com.termux.shared.logger.Logger;

/**
 * woderOS admin / system-roles screen.
 *
 * One place to see and set everything that makes the phone "run from
 * woderOS": device-admin status, default dialer, default SMS app, default
 * home app and battery-optimisation exemption. Each button opens the
 * system's own approval screen — Android decides, the user confirms.
 */
public class WoderosAdminActivity extends Activity {

    private static final String LOG_TAG = "WoderosAdmin";
    private static final int GREEN = Color.rgb(0, 230, 118);
    private static final int PANEL = Color.rgb(16, 16, 16);

    private LinearLayout mRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        mRoot = new LinearLayout(this);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        mRoot.setPadding(pad, pad, pad, pad);
        scroll.addView(mRoot);
        setContentView(scroll);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildUi(); // Refresh statuses after returning from a system screen.
    }

    private void buildUi() {
        mRoot.removeAllViews();

        TextView title = new TextView(this);
        title.setText("woderOS Admin");
        title.setTextColor(GREEN);
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        mRoot.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Phone ko woderOS se chalane ke liye ye adhikaar set karo. Har button par Android ki apni manjuri wali screen khulegi.");
        intro.setTextColor(Color.GRAY);
        intro.setTextSize(13);
        intro.setPadding(0, dp(6), 0, dp(10));
        mRoot.addView(intro);

        // Device admin
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, TermuxDeviceAdminReceiver.class);
        boolean adminActive = dpm != null && dpm.isAdminActive(adminComponent);
        addRow("Device Admin",
            adminActive ? "ON hai" : "OFF hai",
            adminActive ? null : "On karo",
            v -> {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "woderOS ko device admin adhikaar chahiye taaki phone ke system kaam (lock, policy) sambhal sake.");
                startActivity(intent);
            });

        // Default dialer
        String defaultDialer = null;
        try {
            TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) defaultDialer = tm.getDefaultDialerPackage();
        } catch (Exception ignored) {}
        boolean isDialer = getPackageName().equals(defaultDialer);
        addRow("Default Phone App (Dialer)",
            isDialer ? "woderOS hi hai" : "Abhi: " + (defaultDialer == null ? "pata nahi" : defaultDialer),
            isDialer ? null : "woderOS ko banao",
            v -> {
                try {
                    startActivity(TermuxCallManager.getDefaultDialerRoleIntent(this));
                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "Dialer role request failed: " + e.getMessage());
                    Toast.makeText(this, "Dialer screen nahi khul payi", Toast.LENGTH_SHORT).show();
                }
            });

        // Default SMS app
        String defaultSms = null;
        try {
            defaultSms = Telephony.Sms.getDefaultSmsPackage(this);
        } catch (Exception ignored) {}
        boolean isSms = getPackageName().equals(defaultSms);
        addRow("Default SMS App",
            isSms ? "woderOS hi hai" : "Abhi: " + (defaultSms == null ? "koi nahi" : defaultSms),
            isSms ? null : "woderOS ko banao",
            v -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        RoleManager rm = getSystemService(RoleManager.class);
                        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                            startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_SMS));
                            return;
                        }
                    }
                    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                    startActivity(intent);
                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "SMS role request failed: " + e.getMessage());
                    Toast.makeText(this, "SMS screen nahi khul payi", Toast.LENGTH_SHORT).show();
                }
            });

        // Default home app
        String homePackage = null;
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolved = getPackageManager().resolveActivity(homeIntent, 0);
            if (resolved != null && resolved.activityInfo != null) homePackage = resolved.activityInfo.packageName;
        } catch (Exception ignored) {}
        boolean isHome = getPackageName().equals(homePackage);
        addRow("Default Home App (Launcher)",
            isHome ? "woderOS hi hai" : "Abhi: " + (homePackage == null ? "pata nahi" : homePackage),
            isHome ? null : "Home app badlo",
            v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                    if (getPackageManager().resolveActivity(intent, 0) == null) {
                        intent = new Intent(Settings.ACTION_SETTINGS);
                    }
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Settings nahi khul payi", Toast.LENGTH_SHORT).show();
                }
            });

        // Battery optimisation
        boolean ignoringBattery = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                ignoringBattery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            } catch (Exception ignored) {}
        }
        addRow("Battery Bandish se Chhoot",
            ignoringBattery ? "Mili hui hai (background chalega)" : "Nahi mili — background ruk sakta hai",
            ignoringBattery ? null : "Chhoot lo",
            v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Battery screen nahi khul payi", Toast.LENGTH_SHORT).show();
                }
            });

        TextView note = new TextView(this);
        note.setText("Note: Device Owner / kiosk mode sirf naye (factory-reset) phone par provisioning se milta hai — wo is screen se nahi ho sakta.");
        note.setTextColor(Color.GRAY);
        note.setTextSize(12);
        note.setPadding(0, dp(14), 0, 0);
        mRoot.addView(note);
    }

    private void addRow(String titleText, String statusText, String buttonText, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(PANEL);
        int p = dp(12);
        row.setPadding(p, p, p, p);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(10);
        row.setLayoutParams(rowParams);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        row.addView(title);

        TextView status = new TextView(this);
        status.setText(statusText);
        status.setTextColor(Color.GRAY);
        status.setTextSize(13);
        row.addView(status);

        if (buttonText != null && listener != null) {
            Button button = new Button(this);
            button.setText(buttonText);
            button.setOnClickListener(listener);
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonParams.topMargin = dp(6);
            row.addView(button, buttonParams);
        }
        mRoot.addView(row);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
