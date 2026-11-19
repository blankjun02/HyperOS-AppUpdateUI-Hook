package com.blacksoft.miuixpackageupdate;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class ModuleSettingsProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://com.blacksoft.miuixpackageupdate.settings");
    static final String KEY_FREEFORM_UPDATES = "freeform_updates";
    static final boolean DEFAULT_FREEFORM_UPDATES = true;

    static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences("module_settings", Context.MODE_PRIVATE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"com.android.systemui".equals(getCallingPackage())) {
            throw new SecurityException("Only SystemUI can read module settings");
        }
        if (!KEY_FREEFORM_UPDATES.equals(method)) {
            throw new UnsupportedOperationException();
        }
        Bundle result = new Bundle();
        result.putBoolean(KEY_FREEFORM_UPDATES, preferences(getContext())
                .getBoolean(KEY_FREEFORM_UPDATES, DEFAULT_FREEFORM_UPDATES));
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
