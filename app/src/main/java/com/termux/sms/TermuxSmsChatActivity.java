package com.termux.app.sms;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class TermuxSmsChatActivity extends Activity {

    private String senderNumber = "";
    private LinearLayout messagesContainer;
    private EditText replyInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
            (int) (getResources().getDisplayMetrics().heightPixels * 0.82)
        );
        getWindow().setGravity(Gravity.CENTER);

        senderNumber = getIntent().getStringExtra("sender_number");
        if (senderNumber == null) senderNumber = "";

        buildChatLayout();
        loadConversation();
    }

    private void buildChatLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D080A"));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.parseColor("#0D080A"));
        border.setStroke(dp(2), Color.parseColor("#FF1744"));
        border.setCornerRadius(dp(16));
        root.setBackground(border);

        // Header
        TextView header = new TextView(this);
        header.setText("CHAT : " + senderNumber);
        header.setTextColor(Color.parseColor("#FFFFFF"));
        header.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackground(makeBox("#1A080D", "#FF1744", 8, 1));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Scrollable Messages Area
        ScrollView scroll = new ScrollView(this);
        messagesContainer = new LinearLayout(this);
        messagesContainer.setOrientation(LinearLayout.VERTICAL);
        messagesContainer.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(messagesContainer);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollLp.topMargin = dp(6);
        root.addView(scroll, scrollLp);

        // Reply Input & Send Buttons
        replyInput = new EditText(this);
        replyInput.setHint("Type cyber SMS reply...");
        replyInput.setHintTextColor(Color.parseColor("#94A3B8"));
        replyInput.setTextColor(Color.WHITE);
        replyInput.setTypeface(Typeface.MONOSPACE);
        replyInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        replyInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        replyInput.setBackground(makeBox("#14070B", "#FF1744", 8, 1));
        root.addView(replyInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout sendRow = new LinearLayout(this);
        sendRow.setOrientation(LinearLayout.HORIZONTAL);
        sendRow.setWeightSum(2f);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(6);

        Button btnSim1 = makeCyberButton("[ SEND SIM 1 ]", "#00E676", "#062918");
        btnSim1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSmsMessage(1);
            }
        });

        Button btnSim2 = makeCyberButton("[ SEND SIM 2 ]", "#00E5FF", "#062129");
        btnSim2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSmsMessage(2);
            }
        });

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        btnLp.setMargins(dp(2), 0, dp(2), 0);
        sendRow.addView(btnSim1, btnLp);
        sendRow.addView(btnSim2, btnLp);
        root.addView(sendRow, rowLp);

        setContentView(root);
    }

    private void loadConversation() {
        messagesContainer.removeAllViews();
        try {
            ContentResolver resolver = getContentResolver();
            Uri uri = Uri.parse("content://sms/inbox");
            Cursor cursor = resolver.query(uri, new String[]{"body", "date"}, "address = ?", new String[]{senderNumber}, "date ASC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String body = cursor.getString(0);
                    addMessageBubble(body);
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
    }

    private void addMessageBubble(String text) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(Color.WHITE);
        bubble.setTypeface(Typeface.MONOSPACE);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        bubble.setPadding(dp(10), dp(8), dp(10), dp(8));
        bubble.setBackground(makeBox("#1F080E", "#FF1744", 8, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        messagesContainer.addView(bubble, lp);
    }

    private void sendSmsMessage(int simSlot) {
        String text = replyInput.getText().toString().trim();
        if (text.isEmpty() || senderNumber.isEmpty()) {
            Toast.makeText(this, "Message or Number empty", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SmsManager.getDefault().sendTextMessage(senderNumber, null, text, null, null);
            Toast.makeText(this, "Sent via SIM " + simSlot, Toast.LENGTH_SHORT).show();
            replyInput.setText("");
            addMessageBubble(text);
        } catch (Exception e) {
            Toast.makeText(this, "Send Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Button makeCyberButton(String text, String textColor, String fillHex) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor(textColor));
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        b.setGravity(Gravity.CENTER);
        b.setBackground(makeBox(fillHex, textColor, 8, 1));
        return b;
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
}
