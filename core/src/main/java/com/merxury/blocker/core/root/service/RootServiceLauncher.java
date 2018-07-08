package com.merxury.blocker.core.root.service;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.merxury.libkit.RootCommand;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by Mercury on 2018/2/4.
 * Reserved code, for future use
 */

public class RootServiceLauncher {
    public static final String NICE_NAME = "blocker_server";
    public static final String SERVICE_REGISTER_CLASSPATH = ServiceRegister.class.getName();

    private static final String TAG = "RootServiceLauncher";
    private static final String COMMAND_TEMPLATE = "app_process -Djava.class.path=%s /system/bin --nice-name=%s %s &";
    private static final String sepolicy1 = "sepolicy-inject --live \"allow untrusted_app su binder transfer\"";
    private static final String sepolicy2 = "sepolicy-inject --live \"allow untrusted_app su binder call\"";

    public static boolean startService(Context context) {
        changeSELinuxPolicy();
        String path = null;
        try {
            path = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0).publicSourceDir;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        String comm = String.format(COMMAND_TEMPLATE, path, NICE_NAME, SERVICE_REGISTER_CLASSPATH);
        Log.i(TAG, comm);
        try {
            Command command = new Command(0, comm);
            RootTools.getShell(true).add(command);
        } catch (RootDeniedException | TimeoutException | IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static void changeSELinuxPolicy() {
        try {
            RootCommand.runBlockingCommand(sepolicy1);
            RootCommand.runBlockingCommand(sepolicy2);
        } catch (RootDeniedException | TimeoutException | IOException e) {
            System.err.println(e.getMessage());
            Log.e(TAG, "Failed to inject sepolicy");
            Log.e(TAG, e.getMessage());
        }
    }
}
