package com.merxury.blocker.core.root.service;

import android.os.Looper;
import android.os.ServiceManager;

public class ServiceRegister {
    public static final String TAG = "ServiceRegister";

    public static void main(String[] args) {
        try {
            Looper.prepare();
            ServiceManager.addService(RootService.NAME, new RootService());
            Looper.loop();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}
