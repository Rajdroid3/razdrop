package com.razdrop.app;

import android.app.Application;
import android.util.Log;

public class RazDropApplication extends Application {
    private static final String TAG = "RazDropApp";
    private static RazDropApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "RazDrop Application initialized");
    }

    public static RazDropApplication getInstance() {
        return instance;
    }
}
