package com.termux.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * woderOS contacts screen.
 *
 * Reads the phone's contacts (name + number) with the READ_CONTACTS
 * permission, with search. Tap a contact to dial it (through the default
 * dialer — woderOS itself when its dialer role is set), long-press to
 * message it.
 */
public class WoderosContactsActivity extends Activity {

    private static final String LOG_TAG = "WoderosContacts";
    private static final int GREEN = Color.rgb(0, 230, 118);
    private static final int REQUEST_READ_CONTACTS = 42;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ContactAdapter mAdapter;

    private static class Contact {
        final String name;
        final String number;
        Contact(String name, String number) { this.name = name; this.number = number; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Contacts");
        title.setTextColor(GREEN);
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Tap = call • Der tak dabao = message");
        hint.setTextColor(Color.GRAY);
        hint.setTextSize(12);
        root.addView(hint);

        EditText search = new EditText(this);
        search.setHint("Naam ya number khojo...");
        search.setHintTextColor(Color.GRAY);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setBackgroundColor(Color.rgb(16, 16, 16));
        int sp = dp(10);
        search.setPadding(sp, sp, sp, sp);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = dp(10);
        root.addView(search, searchParams);

        ListView list = new ListView(this);
        list.setDividerHeight(0);
        mAdapter = new ContactAdapter();
        list.setAdapter(mAdapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.topMargin = dp(8);
        root.addView(list, listParams);

        list.setOnItemClickListener((parent, view, position, id) -> {
            Contact contact = mAdapter.getItem(position);
            if (contact == null) return;
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(contact.number))));
            } catch (Exception e) {
                Toast.makeText(this, "Dialer nahi khul paya", Toast.LENGTH_SHORT).show();
            }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Contact contact = mAdapter.getItem(position);
            if (contact == null) return true;
            try {
                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(contact.number))));
            } catch (Exception e) {
                Toast.makeText(this, "Messaging nahi khul paya", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        setContentView(root);

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M
            || checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_READ_CONTACTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContacts();
            } else {
                Toast.makeText(this, "Contacts padhne ki anumati chahiye", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadContacts() {
        new Thread(() -> {
            List<Contact> contacts = new ArrayList<>();
            try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
                if (cursor != null) {
                    String lastKey = null;
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(0);
                        String number = cursor.getString(1);
                        if (number == null) continue;
                        if (name == null) name = number;
                        String key = name + "|" + number.replaceAll("[^0-9+]", "");
                        if (key.equals(lastKey)) continue;
                        lastKey = key;
                        contacts.add(new Contact(name, number));
                    }
                }
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Contacts load failed: " + e.getMessage());
            }
            List<Contact> result = contacts;
            mHandler.post(() -> mAdapter.setContacts(result));
        }).start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class ContactAdapter extends BaseAdapter {
        private final List<Contact> mAll = new ArrayList<>();
        private final List<Contact> mShown = new ArrayList<>();

        void setContacts(List<Contact> contacts) {
            mAll.clear();
            mAll.addAll(contacts);
            mShown.clear();
            mShown.addAll(contacts);
            notifyDataSetChanged();
        }

        void filter(String text) {
            String needle = text == null ? "" : text.trim().toLowerCase(Locale.getDefault());
            mShown.clear();
            for (Contact contact : mAll) {
                if (needle.isEmpty()
                    || contact.name.toLowerCase(Locale.getDefault()).contains(needle)
                    || contact.number.replaceAll("[^0-9]", "").contains(needle.replaceAll("[^0-9]", ""))) {
                    mShown.add(contact);
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return mShown.size(); }
        @Override public Contact getItem(int position) {
            return position >= 0 && position < mShown.size() ? mShown.get(position) : null;
        }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView name;
            TextView number;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                name = (TextView) row.getChildAt(0);
                number = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(WoderosContactsActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(6), dp(9), dp(6), dp(9));
                name = new TextView(WoderosContactsActivity.this);
                name.setTextColor(Color.WHITE);
                name.setTextSize(16);
                number = new TextView(WoderosContactsActivity.this);
                number.setTextColor(Color.GRAY);
                number.setTextSize(13);
                row.addView(name);
                row.addView(number);
            }
            Contact contact = getItem(position);
            if (contact != null) {
                name.setText(contact.name);
                number.setText(contact.number);
            }
            return row;
        }
    }
}
