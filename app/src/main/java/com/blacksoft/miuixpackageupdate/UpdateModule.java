package com.blacksoft.miuixpackageupdate;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceControl;
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

    private Boolean freeformUpdatesEnabled;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!SYSTEM_UI.equals(param.getPackageName()) || !param.isFirstPackage()) {
            return;
        }

        if (!DeviceCompatibility.hasSupportedAndroidVersion() || !DeviceCompatibility.isHyperOs()) {
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
            hookFreeformPackageUpdates(param.getClassLoader());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Log.e(TAG, "Unable to hook PackageUpdateActivity", error);
        }
    }

    private void hookFreeformPackageUpdates(ClassLoader loader) {
        try {
            Class<?> listener = Class.forName("com.android.wm.shell.freeform.FreeformTaskListener", false, loader);
            Class<?> organizer = Class.forName("com.android.wm.shell.ShellTaskOrganizer", false, loader);
            Class<?> transaction = Class.forName("android.window.WindowContainerTransaction");
            Class<?> token = Class.forName("android.window.WindowContainerToken");

            Method appeared = listener.getDeclaredMethod("onTaskAppeared",
                    ActivityManager.RunningTaskInfo.class, SurfaceControl.class);
            Method vanished = listener.getDeclaredMethod("onTaskVanished", ActivityManager.RunningTaskInfo.class);
            Field organizerField = listener.getDeclaredField("mShellTaskOrganizer");
            organizerField.setAccessible(true);
            Field contextField = listener.getDeclaredField("mContext");
            contextField.setAccessible(true);
            Field tokenField = ActivityManager.RunningTaskInfo.class.getField("token");
            Method hasParentTask = ActivityManager.RunningTaskInfo.class.getMethod("hasParentTask");
            Method getActivityType = ActivityManager.RunningTaskInfo.class.getMethod("getActivityType");
            var newTransaction = transaction.getConstructor();
            Method setHandleUpdate = transaction.getMethod("setHandlePackageUpdateForRootContainer",
                    token, boolean.class);
            Method applyTransaction = organizer.getMethod("applyTransaction", transaction);

            for (Method callback : List.of(appeared, vanished)) {
                boolean enable = callback == appeared;
                hook(callback).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        ActivityManager.RunningTaskInfo task = (ActivityManager.RunningTaskInfo) chain.getArg(0);
                        // Only standard root tasks can own package-update handling.
                        if ((boolean) hasParentTask.invoke(task) || (int) getActivityType.invoke(task) != 1) {
                            return result;
                        }
                        if (freeformUpdatesEnabled == null) {
                            Context context = (Context) contextField.get(chain.getThisObject());
                            Bundle settings = context.getContentResolver().call(ModuleSettingsProvider.URI,
                                    ModuleSettingsProvider.KEY_FREEFORM_UPDATES, null, null);
                            if (settings == null) return result;
                            freeformUpdatesEnabled = settings.getBoolean(ModuleSettingsProvider.KEY_FREEFORM_UPDATES);
                        }
                        if (!freeformUpdatesEnabled) return result;
                        Object taskToken = tokenField.get(task);
                        if (taskToken == null) return result;

                        Object change = newTransaction.newInstance();
                        setHandleUpdate.invoke(change, taskToken, enable);
                        applyTransaction.invoke(organizerField.get(chain.getThisObject()), change);
                        Log.d(TAG, "Freeform package update handling=" + enable + " task=" + task.taskId);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        Log.w(TAG, "Unable to set freeform package update handling", error);
                    }
                    return result;
                });
            }
            Log.i(TAG, "Freeform package update hooks installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Log.w(TAG, "Freeform package update hooks unavailable", error);
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
