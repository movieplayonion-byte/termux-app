package com.termux.app;

import android.content.Context;
import android.system.Os;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * woderOS first-run setup.
 *
 * Runs once after the bootstrap has been installed (called from
 * {@link TermuxActivity} when the first session is about to be created):
 *
 * 1. Installs the Kali-style shell configs into the user's home directory
 *    (~/.zshrc and ~/.bashrc, from res/raw) — only if the user does not
 *    already have their own files. Existing files are never overwritten.
 * 2. Points the login shell symlink (~/.termux/shell, read by the `login`
 *    script from termux-tools) at zsh, like Kali's default shell. If zsh is
 *    not installed yet, `login` safely falls back to bash; the one-time
 *    setup block at the end of our .bashrc installs zsh on first launch.
 *
 * Every failure is only logged — setup must never break app startup.
 */
public class WoderosSetup {

    private static final String LOG_TAG = "WoderosSetup";

    public static void setupIfNeeded(Context context) {
        try {
            File homeDir = TermuxConstants.TERMUX_HOME_DIR;
            if (!homeDir.exists() && !homeDir.mkdirs()) {
                Logger.logError(LOG_TAG, "Could not create home directory: " + homeDir);
                return;
            }

            writeRawResourceIfAbsent(context, R.raw.woderos_zshrc, new File(homeDir, ".zshrc"));
            writeRawResourceIfAbsent(context, R.raw.woderos_bashrc, new File(homeDir, ".bashrc"));

            File termuxDir = new File(homeDir, ".termux");
            if (!termuxDir.exists() && !termuxDir.mkdirs()) {
                Logger.logError(LOG_TAG, "Could not create .termux directory: " + termuxDir);
                return;
            }

            File shellLink = new File(termuxDir, "shell");
            if (!shellLink.exists()) {
                String zshPath = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/zsh";
                try {
                    Os.symlink(zshPath, shellLink.getAbsolutePath());
                    Logger.logInfo(LOG_TAG, "Login shell set to zsh: " + shellLink + " -> " + zshPath);
                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "Could not create shell symlink: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "woderOS setup failed: " + e.getMessage());
        }
    }

    private static void writeRawResourceIfAbsent(Context context, int rawResId, File target) {
        if (target.exists()) {
            return; // Never overwrite the user's own config.
        }
        try (InputStream in = context.getResources().openRawResource(rawResId);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            Logger.logInfo(LOG_TAG, "Installed default config: " + target);
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Could not write " + target + ": " + e.getMessage());
        }
    }
}
