package com.blacksoft.miuixpackageupdate;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedModule;

public final class UpdateModule extends XposedModule {
    private static final String TAG = "MiuixPackageUpdate";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String UPDATE_ACTIVITY =
            "com.android.wm.shell.packageupdate.PackageUpdateActivity";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!SYSTEM_UI.equals(param.getPackageName()) || !param.isFirstPackage()) {
            return;
        }

        try {
            Class<?> activity = Class.forName(UPDATE_ACTIVITY, false, param.getClassLoader());
            Method onCreate = activity.getDeclaredMethod("onCreate", Bundle.class);
            hook(onCreate).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    applyStyle((Activity) chain.getThisObject(), activity.getClassLoader());
                } catch (RuntimeException | LinkageError error) {
                    Log.e(TAG, "Unable to apply update UI style", error);
                }
                return result;
            });
            Log.i(TAG, "PackageUpdateActivity hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Log.e(TAG, "Unable to hook PackageUpdateActivity", error);
        }
    }

    private static void applyStyle(Activity activity, ClassLoader loader) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        ProgressBar original = findProgressBar(activity, content, loader);
        if (original != null && original.getParent() instanceof ViewGroup parent) {
            int index = parent.indexOfChild(original);
            ViewGroup.LayoutParams params = original.getLayoutParams();
            View spinner = MiuixUpdateStyle.createSpinner(original);
            spinner.setId(original.getId());
            spinner.setVisibility(original.getVisibility());
            spinner.setContentDescription(original.getContentDescription());
            spinner.setImportantForAccessibility(original.getImportantForAccessibility());
            parent.removeViewAt(index);
            try {
                parent.addView(spinner, index, params);
            } catch (RuntimeException error) {
                if (spinner.getParent() == parent) {
                    parent.removeView(spinner);
                }
                parent.addView(original, index, params);
                throw error;
            }
        } else {
            Log.w(TAG, "Update progress indicator could not be identified");
        }

        MiuixUpdateStyle.applyBackground(activity, content);
        Log.i(TAG, original == null ? "Applied background" : "Applied background and spinner");
    }

    private static ProgressBar findProgressBar(Activity activity, View content, ClassLoader loader) {
        try {
            Class<?> ids = Class.forName("com.android.wm.shell.R$id", false, loader);
            Field field = ids.getDeclaredField("circular_progress_indicator");
            field.setAccessible(true);
            if (activity.findViewById(field.getInt(null)) instanceof ProgressBar progress) {
                return progress;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Some HyperOS builds rename or relocate resource IDs.
        }
        List<ProgressBar> bars = new ArrayList<>(2);
        collectProgressBars(content, bars);
        return bars.size() == 1 ? bars.get(0) : null;
    }

    private static void collectProgressBars(View view, List<ProgressBar> bars) {
        if (view instanceof ProgressBar progress) {
            bars.add(progress);
        } else if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                collectProgressBars(group.getChildAt(index), bars);
            }
        }
    }
}
