package com.shivy.shnet;

import android.app.Service;
import android.content.Context;

public class ShnetRuntime {
    private static final String PREFS_NAME = "shnet_runtime";

    public static boolean isRunning(Context context, Class<? extends Service> serviceClass) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(key(serviceClass, "running"), false);
    }

    public static String getLastError(Context context, Class<? extends Service> serviceClass) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key(serviceClass, "error"), "");
    }

    public static long getLastStartAttempt(Context context, Class<? extends Service> serviceClass) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(key(serviceClass, "last_start"), 0L);
    }

    static void setRunning(Context context, Class<? extends Service> serviceClass, boolean running) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key(serviceClass, "running"), running)
                .apply();
    }

    static void setLastError(Context context, Class<? extends Service> serviceClass, String message) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key(serviceClass, "error"), message == null ? "" : message)
                .apply();
    }

    static void setLastStartAttempt(Context context, Class<? extends Service> serviceClass, long timestamp) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(key(serviceClass, "last_start"), timestamp)
                .apply();
    }

    private static String key(Class<? extends Service> serviceClass, String suffix) {
        String name = serviceClass == null ? "unknown" : serviceClass.getName();
        return name + ":" + suffix;
    }
}
