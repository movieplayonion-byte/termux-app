package com.termux.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * woderOS theme screen.
 *
 * Applies a terminal colour theme the same way Termux:Styling does: it
 * writes ~/.termux/colors.properties and broadcasts
 * {@link TermuxConstants.TERMUX_ACTIVITY#ACTION_RELOAD_STYLE} so the
 * running terminal picks the new colours up immediately. "Termux Default"
 * removes the file again.
 */
public class WoderosThemeActivity extends Activity {

    private static final String LOG_TAG = "WoderosTheme";
    private static final int GREEN = Color.rgb(0, 230, 118);

    private static final Map<String, String> THEMES = new LinkedHashMap<>();

    static {
        THEMES.put("Kali Green (woderOS)", String.join("\n",
            "foreground=#00E676",
            "background=#000000",
            "cursor=#00E676",
            "color0=#1B1B1B", "color1=#FF5555", "color2=#00E676", "color3=#FFB86C",
            "color4=#33CCFF", "color5=#FF79C6", "color6=#00E5FF", "color7=#F8F8F2",
            "color8=#555555", "color9=#FF6E6E", "color10=#69FF94", "color11=#FFFFA5",
            "color12=#8BE9FD", "color13=#FF92DF", "color14=#A4FFFF", "color15=#FFFFFF",
            ""));

        THEMES.put("Matrix", String.join("\n",
            "foreground=#00FF41",
            "background=#000000",
            "cursor=#00FF41",
            "color0=#0D0D0D", "color1=#00701A", "color2=#00C853", "color3=#43A047",
            "color4=#1B5E20", "color5=#2E7D32", "color6=#00E676", "color7=#C8E6C9",
            "color8=#1C2A1C", "color9=#00E676", "color10=#69F0AE", "color11=#B9F6CA",
            "color12=#388E3C", "color13=#4CAF50", "color14=#00FF41", "color15=#E8F5E9",
            ""));

        THEMES.put("Amber Retro", String.join("\n",
            "foreground=#FFB000",
            "background=#1A1000",
            "cursor=#FFB000",
            "color0=#1A1000", "color1=#CC7000", "color2=#FFB000", "color3=#FFD54F",
            "color4=#A1887F", "color5=#FF8F00", "color6=#FFCA28", "color7=#FFE082",
            "color8=#4E342E", "color9=#FF6F00", "color10=#FFC107", "color11=#FFF59D",
            "color12=#BCAAA4", "color13=#FFB300", "color14=#FFE57F", "color15=#FFF8E1",
            ""));

        THEMES.put("Solarized Dark", String.join("\n",
            "foreground=#839496",
            "background=#002B36",
            "cursor=#93A1A1",
            "color0=#073642", "color1=#DC322F", "color2=#859900", "color3=#B58900",
            "color4=#268BD2", "color5=#D33682", "color6=#2AA198", "color7=#EEE8D5",
            "color8=#002B36", "color9=#CB4B16", "color10=#586E75", "color11=#657B83",
            "color12=#839496", "color13=#6C71C4", "color14=#93A1A1", "color15=#FDF6E3",
            ""));

        THEMES.put("Dracula", String.join("\n",
            "foreground=#F8F8F2",
            "background=#282A36",
            "cursor=#F8F8F2",
            "color0=#21222C", "color1=#FF5555", "color2=#50FA7B", "color3=#F1FA8C",
            "color4=#BD93F9", "color5=#FF79C6", "color6=#8BE9FD", "color7=#F8F8F2",
            "color8=#6272A4", "color9=#FF6E6E", "color10=#69FF94", "color11=#FFFFA5",
            "color12=#D6ACFF", "color13=#FF92DF", "color14=#A4FFFF", "color15=#FFFFFF",
            ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("woderOS Themes");
        title.setTextColor(GREEN);
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("Terminal ke rang chunno — lagate hi badal jayenge.");
        intro.setTextColor(Color.GRAY);
        intro.setTextSize(13);
        intro.setPadding(0, dp(6), 0, dp(12));
        root.addView(intro);

        for (Map.Entry<String, String> entry : THEMES.entrySet()) {
            Button button = new Button(this);
            button.setText(entry.getKey());
            button.setOnClickListener(v -> applyTheme(entry.getKey(), entry.getValue()));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            root.addView(button, params);
        }

        Button reset = new Button(this);
        reset.setText("Termux Default (theme hatao)");
        reset.setOnClickListener(v -> {
            File file = colorsFile();
            if (file.exists() && !file.delete()) {
                Toast.makeText(this, "File nahi hati", Toast.LENGTH_SHORT).show();
                return;
            }
            reloadStyle();
            Toast.makeText(this, "Default theme lag gaya", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.topMargin = dp(6);
        root.addView(reset, resetParams);

        setContentView(scroll);
    }

    private File colorsFile() {
        return new File(TermuxConstants.TERMUX_HOME_DIR, ".termux/colors.properties");
    }

    private void applyTheme(String name, String content) {
        try {
            File file = colorsFile();
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, ".termux folder nahi ban paya", Toast.LENGTH_SHORT).show();
                return;
            }
            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(content);
            }
            reloadStyle();
            Toast.makeText(this, "Theme lag gaya: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Theme apply failed: " + e.getMessage());
            Toast.makeText(this, "Theme nahi lag paya", Toast.LENGTH_SHORT).show();
        }
    }

    private void reloadStyle() {
        try {
            Intent intent = new Intent(TermuxConstants.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Reload-style broadcast failed: " + e.getMessage());
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
