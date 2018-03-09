package com.merxury.core.root;

import android.content.pm.PackageManager;
import android.util.Log;

import com.merxury.core.IController;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by Mercury on 2017/12/31.
 * A class that controls the state of application components
 */

public class RootController implements IController {
    private static final String TAG = "RootController";
    private static final String DISABLE_COMPONENT_TEMPLATE = "pm disable %s/.%s";
    private static final String ENABLE_COMPONENT_TEMPLATE = "pm enable %s/.%s";
    private static final String FAILED_EXCEPTION_MSG = "java.lang.IllegalArgumentException";

    private RootController() {
        RootTools.debugMode = true;
    }

    public static RootController getInstance() {
        return RootControllerHolder.INSTANCE;
    }

    @Override
    public boolean switchComponent(String packageName, String componentName, int state) {
        final String comm;
        switch (state) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                comm = String.format(ENABLE_COMPONENT_TEMPLATE, packageName, componentName);
                break;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                comm = String.format(DISABLE_COMPONENT_TEMPLATE, packageName, componentName);
                break;
            default:
                return false;
        }
        try {
            String commandOutput = RootCommand.runBlockingCommand(comm);
            return !commandOutput.contains(FAILED_EXCEPTION_MSG);
        } catch (RootDeniedException | TimeoutException | IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    private static class RootControllerHolder {
        private static final RootController INSTANCE = new RootController();
    }
}
