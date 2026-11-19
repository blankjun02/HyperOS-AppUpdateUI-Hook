package com.blacksoft.miuixpackageupdate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModuleSettingsActivity extends Activity {
    private static final AtomicBoolean RESTART_IN_PROGRESS = new AtomicBoolean();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        setTheme(nightMode == Configuration.UI_MODE_NIGHT_YES
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(savedInstanceState);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(32), dp(24), dp(24));
        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        scrollView.addView(content);
        setContentView(scrollView);
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            var safeArea = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
            return insets;
        });

        addText(content, getString(R.string.module_name), 24, 0);
        addText(content, getString(R.string.status_android, Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT), 16, 24);
        TextView hyperOsStatus = addText(content, getString(R.string.status_hyperos_checking),
                16, 12);
        TextView compatibility = addText(content, "", 16, 12);
        new Thread(() -> {
            boolean hyperOs = DeviceCompatibility.isHyperOs();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                hyperOsStatus.setText(hyperOs ? R.string.status_hyperos_yes : R.string.status_hyperos_no);
                compatibility.setText(DeviceCompatibility.hasSupportedAndroidVersion() && hyperOs
                        ? R.string.status_compatible : R.string.status_incompatible);
            });
        }, "HyperOS-compatibility").start();

        Button restart = new Button(this);
        restart.setText(R.string.restart_system_ui);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(24);
        content.addView(restart, buttonParams);
        restart.setOnClickListener(view -> {
            if (RESTART_IN_PROGRESS.get()) {
                Toast.makeText(this, R.string.restart_system_ui_working, Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.restart_system_ui)
                    .setMessage(R.string.restart_system_ui_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.restart_system_ui_action,
                            (dialog, which) -> restartSystemUi(restart))
                    .show();
        });
    }

    private TextView addText(LinearLayout content, String text, int size, int topMargin) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        content.addView(view, params);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void restartSystemUi(Button button) {
        if (!RESTART_IN_PROGRESS.compareAndSet(false, true)) return;
        button.setEnabled(false);
        button.setText(R.string.restart_system_ui_working);
        new Thread(() -> {
            int message = R.string.restart_system_ui_failed;
            Process process = null;
            try {
                process = new ProcessBuilder("su", "-c",
                        "pids=$(pidof com.android.systemui) && [ -n \"$pids\" ] && kill -KILL $pids")
                        .redirectErrorStream(true)
                        .redirectOutput(new File("/dev/null"))
                        .start();
                if (!process.waitFor(60, TimeUnit.SECONDS)) {
                    message = R.string.restart_system_ui_timeout;
                } else if (process.exitValue() == 0) {
                    message = R.string.restart_system_ui_requested;
                }
            } catch (IOException | SecurityException error) {
                Log.w("MiuixPackageUpdate", "Unable to restart System UI", error);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                if (process != null) process.destroy();
                RESTART_IN_PROGRESS.set(false);
            }
            int result = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                button.setEnabled(true);
                button.setText(R.string.restart_system_ui);
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            });
        }, "HyperOS-restart-system-ui").start();
    }
}
