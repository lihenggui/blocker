package com.merxury.blocker;

import android.app.Application;
import android.content.Context;

import moe.shizuku.api.ShizukuClient;

public class LibApplication extends Application {

    private static LibApplication instance;

    public LibApplication() {
        instance = this;
    }

    /**
     * Gets the application context.
     *
     * @return the application context
     */
    public static Context getContext() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ShizukuClient.initialize(this);
    }
}
