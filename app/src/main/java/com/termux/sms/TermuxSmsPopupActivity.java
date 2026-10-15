package com.termux.app.sms;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TermuxSmsPopupActivity extends Activity {

    private static class ContactItem {
        String name;
        String number;
        ContactItem(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }

    private final List<ContactItem> allContacts = new ArrayList<>();
    private final List<ContactItem> filteredContacts = new ArrayList<>();
    private ContactAdapter adapter;

    private EditText messageInput;
    private EditText searchInput;
    private TextView selectedInfoText;
    private Button sendButton;
    private RadioGroup simGroup;
    private ContactItem selectedContact = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // CRASH FIX: requestWindowFeature hamesha super.onCreate se PEHLE hona chahiye!
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        try {
            Intent intent = getIntent();
            if (intent != null && intent.hasExtra("cli_cmd")) {
                String cliCmd = intent.getStringExtra("cli_cmd");
                if ("set_sim".equals(cliCmd)) {
                    int sim = intent.getIntExtra("sim", 1);
                    TermuxSmsManager.setDefaultSim(this, sim);
                    finish();
                    return;
                } else if ("send".equals(cliCmd)) {
                    String number = intent.getStringExtra("number");
                    String text = intent.getStringExtra("text");
                    int sim = intent.getIntExtra("sim", 0);
                    TermuxSmsManager.sendSms(this, number, text, sim);
                    finish();
                    return;
                }
            }

            buildPopupUi();
            handleIncomingIntent(intent);
            checkPermissionsAndLoad();

        } catch (Exception e) {
            TermuxSmsManager.showToast(this, "Popup Error: " + e.getMessage());
            finish();
        }
    }

    private void buildPopupUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 36);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#121212"));
        bg.setCornerRadius(28f);
        bg.setStroke(3, Color.parseColor("#00E676"));
        root.setBackground(bg);

        TextView header = new TextView(this);
        header.setText("TERMUX FAST SMS");
        header.setTextColor(Color.parseColor("#00E676"));
        header.setTextSize(18f);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 0, 0, 20);
        root.addView(header);

        messageInput = new EditText(this);
        messageInput.setHint("Message text...");
        messageInput.setHintTextColor(Color.parseColor("#757575"));
        messageInput.setTextColor(Color.WHITE);
        messageInput.setTextSize(14f);
        messageInput.setMaxLines(4);
        messageInput.setPadding(24, 20, 24, 20);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Color.parseColor("#1E1E1E"));
        boxBg.setCornerRadius(14f);
        messageInput.setBackground(boxBg);
        root.addView(messageInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        simGroup = new RadioGroup(this);
        simGroup.setOrientation(LinearLayout.HORIZONTAL);
        simGroup.setPadding(0, 16, 0, 16);
        int defSim = TermuxSmsManager.getDefaultSim(this);

        RadioButton rbDef = createSimRadio("Default (SIM " + defSim + ")", 100, true);
        RadioButton rbSim1 = createSimRadio("SIM 1", 101, false);
        RadioButton rbSim2 = createSimRadio("SIM 2", 102, false);
        simGroup.addView(rbDef);
        simGroup.addView(rbSim1);
        simGroup.addView(rbSim2);
        root.addView(simGroup);

        searchInput = new EditText(this);
        searchInput.setHint("Search Contact Name or Number...");
        searchInput.setHintTextColor(Color.parseColor("#757575"));
        searchInput.setTextColor(Color.parseColor("#00E676"));
        searchInput.setSingleLine(true);
        searchInput.setPadding(24, 20, 24, 20);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.parseColor("#1E1E1E"));
        searchBg.setCornerRadius(14f);
        searchBg.setStroke(1, Color.parseColor("#333333"));
        searchInput.setBackground(searchBg);
        root.addView(searchInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView listView = new ListView(this);
        listView.setDividerHeight(1);
        adapter = new ContactAdapter();
        listView.setAdapter(adapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.setMargins(0, 16, 0, 16);
        root.addView(listView, listParams);

        selectedInfoText = new TextView(this);
        selectedInfoText.setTextColor(Color.parseColor("#B0BEC5"));
        selectedInfoText.setTextSize(13f);
        selectedInfoText.setPadding(8, 8, 8, 12);
        selectedInfoText.setText("Tap a contact above to select");
        root.addView(selectedInfoText);

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);

        Button cancelButton = new Button(this);
        cancelButton.setText("CLOSE");
        cancelButton.setTextColor(Color.WHITE);
        cancelButton.setBackgroundColor(Color.parseColor("#37474F"));
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cancelParams.setMargins(0, 0, 12, 0);
        bottomRow.addView(cancelButton, cancelParams);

        sendButton = new Button(this);
        sendButton.setText("SEND SMS");
        sendButton.setTextColor(Color.BLACK);
        sendButton.setTypeface(Typeface.DEFAULT_BOLD);
        sendButton.setBackgroundColor(Color.parseColor("#00E676"));
        sendButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f);
        bottomRow.addView(sendButton, sendParams);

        root.addView(bottomRow);
        setContentView(root);

        if (getWindow() != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int width = (int) (dm.widthPixels * 0.92f);
            int height = (int) (dm.heightPixels * 0.78f);
            getWindow().setLayout(width, height);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedContact = filteredContacts.get(position);
                selectedInfoText.setText("To: " + selectedContact.name + " (" + selectedContact.number + ")");
                selectedInfoText.setTextColor(Color.parseColor("#00E676"));
                sendButton.setVisibility(View.VISIBLE);
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedContact == null) return;
                String msg = messageInput.getText().toString().trim();
                if (msg.isEmpty()) {
                    TermuxSmsManager.showToast(TermuxSmsPopupActivity.this, "Please enter message text!");
                    return;
                }
                int checkedId = simGroup.getCheckedRadioButtonId();
                int simChoice = 0;
                if (checkedId == 101) simChoice = 1;
                else if (checkedId == 102) simChoice = 2;

                TermuxSmsManager.sendSms(TermuxSmsPopupActivity.this, selectedContact.number, msg, simChoice);
                finish();
            }
        });
    }

    private RadioButton createSimRadio(String label, int id, boolean checked) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setId(id);
        rb.setTextColor(Color.WHITE);
        rb.setChecked(checked);
        rb.setPadding(8, 0, 20, 0);
        return rb;
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra("request_default_sms", false)) {
            try {
                startActivityForResult(TermuxSmsManager.getDefaultSmsRoleIntent(this), 202);
            } catch (Exception ignored) {}
        }
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText == null) {
            sharedText = intent.getStringExtra("sms_body");
        }
        if (sharedText != null) {
            messageInput.setText(sharedText);
        }
        Uri data = intent.getData();
        if (data != null) {
            String scheme = data.getScheme();
            if ("sms".equals(scheme) || "smsto".equals(scheme) || "mms".equals(scheme) || "mmsto".equals(scheme)) {
                String num = data.getSchemeSpecificPart();
                if (num != null && !num.trim().isEmpty()) {
                    selectedContact = new ContactItem("Direct Number", num.trim());
                    selectedInfoText.setText("To: " + num.trim());
                    selectedInfoText.setTextColor(Color.parseColor("#00E676"));
                    sendButton.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> needed = new ArrayList<>();
            String[] perms = new String[]{
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_PHONE_STATE
            };
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(p);
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    needed.add("android.permission.POST_NOTIFICATIONS");
                }
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), 101);
                return;
            }
        }
        loadContacts();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        loadContacts();
    }

    private void loadContacts() {
        allContacts.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            Set<String> seen = new LinkedHashSet<>();
            Cursor cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );

            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(0);
                        String number = cursor.getString(1);
                        if (number != null) {
                            String normalized = number.replaceAll("[\\s\\-()]", "");
                            String key = (name != null ? name : "") + "|" + normalized;
                            if (!seen.contains(key)) {
                                seen.add(key);
                                allContacts.add(new ContactItem(name != null ? name : "Unknown", normalized));
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception ignored) {}
        filterContacts(searchInput != null ? searchInput.getText().toString() : "");
    }

    private void filterContacts(String query) {
        filteredContacts.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filteredContacts.addAll(allContacts);
        } else {
            for (ContactItem item : allContacts) {
                if (item.name.toLowerCase(Locale.ROOT).contains(q) || item.number.toLowerCase(Locale.ROOT).contains(q)) {
                    filteredContacts.add(item);
                }
            }
            if (q.matches("^[+0-9]{3,}$")) {
                filteredContacts.add(0, new ContactItem("Direct Number", q));
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private class ContactAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return filteredContacts.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredContacts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convertView;
            TextView nameView;
            TextView numView;

            if (row == null) {
                row = new LinearLayout(TermuxSmsPopupActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(20, 18, 20, 18);

                nameView = new TextView(TermuxSmsPopupActivity.this);
                nameView.setId(android.R.id.text1);
                nameView.setTextColor(Color.WHITE);
                nameView.setTextSize(15f);
                nameView.setTypeface(Typeface.DEFAULT_BOLD);

                numView = new TextView(TermuxSmsPopupActivity.this);
                numView.setId(android.R.id.text2);
                numView.setTextColor(Color.parseColor("#00E676"));
                numView.setTextSize(13f);

                row.addView(nameView);
                row.addView(numView);
            } else {
                nameView = row.findViewById(android.R.id.text1);
                numView = row.findViewById(android.R.id.text2);
            }

            ContactItem item = filteredContacts.get(position);
            nameView.setText(item.name);
            numView.setText(item.number);
            return row;
        }
    }
}
