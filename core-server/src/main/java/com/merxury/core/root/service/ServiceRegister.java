package com.merxury.core.root.service;

import android.os.Binder;
import android.os.Looper;
import android.os.ServiceManager;
import android.util.Log;

import com.merxury.core.root.RootCommand;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

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
