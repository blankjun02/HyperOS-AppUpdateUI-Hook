package com.blacksoft.miuixpackageupdate;

import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class DeviceCompatibility {
    private static final String HYPER_OS_PROPERTY = "ro.mi.os.version.code";

    private DeviceCompatibility() {}

    static boolean hasSupportedAndroidVersion() {
        return Build.VERSION.SDK_INT >= 37;
    }

    static boolean isHyperOs() {
        String versionCode = readHyperOsVersionCode();
        try {
            return Float.parseFloat(versionCode) > 0f;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String readHyperOsVersionCode() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            return (String) properties.getMethod("get", String.class)
                    .invoke(null, HYPER_OS_PROPERTY);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // The settings Activity can run with hidden API restrictions.
            return readPublicProperty();
        }
    }

    private static String readPublicProperty() {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", HYPER_OS_PROPERTY).start();
            if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    return line == null ? "" : line;
                }
            }
        } catch (IOException | SecurityException ignored) {
            return "";
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            if (process != null) process.destroy();
        }
        return "";
    }
}
