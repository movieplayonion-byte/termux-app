package com.termux.app.call;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TermuxContactsPopupActivity extends Activity {

    private static class ContactItem {
        String lookupKey;
        String name;
        String number;
        String typeLabel;
        long contactId;
    }

    private final List<ContactItem> allContacts = new ArrayList<>();
    private final List<ContactItem> filteredContacts = new ArrayList<>();
    private LinearLayout rootContainer;
    private EditText searchInput;
    private ContactAdapter adapter;

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

        new LoadContactsTask().execute();
    }

    private class LoadContactsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    return null;
                }

                Set<String> seen = new LinkedHashSet<>();
                Cursor cursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                ContactsContract.CommonDataKinds.Phone.NUMBER,
                                ContactsContract.CommonDataKinds.Phone.LABEL,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
                        },
                        null, null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                );

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(0);
                        String number = cursor.getString(1);
                        String label = cursor.getString(2);
                        long id = cursor.getLong(3);
                        String lookup = cursor.getString(4);

                        if (number != null) {
                            String norm = number.replaceAll("[\\s\\-()]", "");
                            String key = (name != null ? name : "") + "|" + norm;
                            if (!seen.contains(key)) {
                                seen.add(key);
                                ContactItem item = new ContactItem();
                                item.name = (name != null && !name.isEmpty()) ? name : "Unknown";
                                item.number = norm;
                                item.typeLabel = (label != null && !label.isEmpty()) ? label : "MOBILE";
                                item.contactId = id;
                                item.lookupKey = lookup;
                                allContacts.add(item);
                            }
                        }
                    }
                    cursor.close();
                }
            } catch (Exception ignored) {}
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            buildContactsUi();
        }
    }

    private void buildContactsUi() {
        rootContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0D080A"));
        bg.setCornerRadius(36f);
        bg.setStroke(5, Color.parseColor("#FF1744"));
        card.setBackground(bg);

        // Header Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(20, 12, 20, 12);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(Color.parseColor("#1A090E"));
        topBg.setCornerRadius(14f);
        topBg.setStroke(2, Color.parseColor("#3B0D16"));
        topBar.setBackground(topBg);

        TextView cmdTitle = new TextView(this);
        cmdTitle.setText("root@red-os:~# cnc --contacts-manager");
        cmdTitle.setTextColor(Color.parseColor("#FF5252"));
        cmdTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        cmdTitle.setTextSize(11f);
        topBar.addView(cmdTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnCloseX = new TextView(this);
        btnCloseX.setText("[X]");
        btnCloseX.setTextColor(Color.parseColor("#FF1744"));
        btnCloseX.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        btnCloseX.setTextSize(12.5f);
        btnCloseX.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(btnCloseX);
        card.addView(topBar);

        // Search Bar
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(20, 6, 20, 6);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(Color.parseColor("#17070B"));
        sBg.setCornerRadius(18f);
        sBg.setStroke(3, Color.parseColor("#FF1744"));
        searchRow.setBackground(sBg);
        LinearLayout.LayoutParams srParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srParams.setMargins(0, 14, 0, 12);

        searchInput = new EditText(this);
        searchInput.setHint(">> Fast Search Name / Number...");
        searchInput.setHintTextColor(Color.parseColor("#FF8A80"));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        searchInput.setTextSize(12f);
        searchInput.setSingleLine(true);
        searchInput.setBackground(null);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(searchRow, srParams);

        // List
        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(Color.TRANSPARENT));
        listView.setDividerHeight(12);
        adapter = new ContactAdapter();
        listView.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.setMargins(0, 10, 0, 10);
        card.addView(listView, lp);

        // Bottom Dialpad Button
        Button btnDialpad = createButton("DIALPAD [KEYPAD]", "#00E5FF", "#1E1136", "#7C3AED", 11f);
        btnDialpad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(TermuxContactsPopupActivity.this, TermuxCallActivity.class);
                i.putExtra("call_cli_cmd", "popup");
                startActivity(i);
                finish();
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 110);
        card.addView(btnDialpad, bp);

        rootContainer.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
                showContactActionDialog(filteredContacts.get(position));
            }
        });

        filterContacts("");
    }

    private void filterContacts(String query) {
        filteredContacts.clear();
        String q = (searchInput != null && searchInput.getText() != null)
                ? searchInput.getText().toString().trim().toLowerCase(Locale.ROOT) : "";

        for (ContactItem item : allContacts) {
            if (!q.isEmpty()) {
                boolean matchName = item.name.toLowerCase(Locale.ROOT).contains(q);
                boolean matchNum = item.number.contains(q);
                if (!matchName && !matchNum) continue;
            }
            filteredContacts.add(item);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // ================= TAP ACTION SHEET (CALL, VID, EDIT) =================
    private void showContactActionDialog(final ContactItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(item.name);
        builder.setMessage("Number: " + item.number);

        builder.setPositiveButton("CALL", (dialog, which) -> {
            TermuxCallManager.startOutgoingCall(TermuxContactsPopupActivity.this, item.number, 0);
            finish();
        });

        builder.setNeutralButton("VIDEO CALL", (dialog, which) -> {
            TermuxCallManager.startOutgoingVideoCall(TermuxContactsPopupActivity.this, item.number, 0);
            finish();
        });

        builder.setNegativeButton("EDIT", (dialog, which) -> {
            try {
                Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, item.contactId);
                Intent editIntent = new Intent(Intent.ACTION_EDIT);
                editIntent.setDataAndType(contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                editIntent.putExtra("finishActivityOnSaveTrue", true);
                startActivity(editIntent);
            } catch (Exception e) {
                TermuxCallManager.showToast(TermuxContactsPopupActivity.this, "Edit Error: " + e.getMessage());
            }
        });

        builder.show();
    }

    private class ContactAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredContacts.size(); }
        @Override public Object getItem(int position) { return filteredContacts.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ContactItem item = filteredContacts.get(position);

            LinearLayout row = new LinearLayout(TermuxContactsPopupActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(18, 16, 18, 16);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.parseColor("#14080C"));
            cBg.setCornerRadius(18f);
            cBg.setStroke(2, Color.parseColor("#3B0D16"));
            row.setBackground(cBg);

            LinearLayout info = new LinearLayout(TermuxContactsPopupActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView tName = new TextView(TermuxContactsPopupActivity.this);
            tName.setText(item.name);
            tName.setTextColor(Color.WHITE);
            tName.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tName.setTextSize(13f);
            info.addView(tName);

            TextView tSub = new TextView(TermuxContactsPopupActivity.this);
            tSub.setText(item.number + " • " + item.typeLabel);
            tSub.setTextColor(Color.parseColor("#00E5FF"));
            tSub.setTypeface(Typeface.MONOSPACE);
            tSub.setTextSize(10.5f);
            info.addView(tSub);

            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button btnCall = createButton("CALL", "#00E676", "#063B20", "#00E676", 9f);
            btnCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TermuxCallManager.startOutgoingCall(TermuxContactsPopupActivity.this, item.number, 0);
                    finish();
                }
            });
            row.addView(btnCall, new LinearLayout.LayoutParams(110, 80));

            View sp = new View(TermuxContactsPopupActivity.this);
            row.addView(sp, new LinearLayout.LayoutParams(10, 10));

            Button btnVid = createButton("VID", "#00E5FF", "#1E1136", "#7C3AED", 9f);
            btnVid.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TermuxCallManager.startOutgoingVideoCall(TermuxContactsPopupActivity.this, item.number, 0);
                    finish();
                }
            });
            row.addView(btnVid, new LinearLayout.LayoutParams(110, 80));

            return row;
        }
    }

    private Button createButton(String text, String textCol, String fillCol, String borderCol, float textSize) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(textCol));
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setTextSize(textSize);
        b.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(16f);
        gd.setColor(Color.parseColor(fillCol));
        gd.setStroke(3, Color.parseColor(borderCol));
        b.setBackground(gd);
        return b;
    }
          }
      
