package com.merxury.function.root;

import android.util.Log;

import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootShell.execution.Shell;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by Mercury on 2017/12/31.
 * A class that controls the state of application components
 */

public class ComponentController {
    private static final String TAG = "ComponentController";
    private static final String DISABLE_COMPONENT_TEMPLATE = "pm disable %s/.%s";
    private static final String ENABLE_COMPONENT_TEMPLATE = "pm enable %s/.%s";
    private static final String FAILED_EXCEPTION = "java.lang.IllegalArgumentException";

    private Shell shell;

    /**
     * a method to disable components
     *
     * @param packageName package name
     * @param componentName component name
     * @return true : disabled component successfully
     *          false: cannot disable component
     */
    public boolean disableComponent(String packageName, String componentName) {
        String comm = String.format(DISABLE_COMPONENT_TEMPLATE, packageName, componentName);
        boolean success;
        Command command = new Command(0, comm){
            boolean success;
            @Override
            public void commandOutput(int id, String line) {
                //TODO get output
                Log.d(TAG, "commandOutput");
                Log.d(TAG, line);
                if(line.contains(FAILED_EXCEPTION)) {
                    this.success = false;
                } else {
                    this.success = true;
                }
                super.commandOutput(id, line);
            }

            @Override
            public void commandTerminated(int id, String reason) {
                Log.d(TAG, "commandTerminated");
                this.success = false;
                synchronized (ComponentController.this) {
                    ComponentController.this.notify();
                }
                super.commandTerminated(id, reason);
            }

            @Override
            public void commandCompleted(int id, int exitcode) {
                Log.d(TAG, "commandCompleted");
                synchronized (ComponentController.this) {
                    ComponentController.this.notify();
                }
                super.commandCompleted(id, exitcode);
            }
        };

        try {
            RootTools.getShell(true).add(command);
            synchronized (this){
                wait();
            }
        }catch (RootDeniedException | TimeoutException | InterruptedException |IOException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public boolean enableComponent(String packageName, String componentName) {
        String comm = String.format(DISABLE_COMPONENT_TEMPLATE, packageName, componentName);
        Command command = new Command(0, comm);
        try {
            shell.add(command);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
        return false;
    }

    private boolean

    private static class ComponentControllerHolder {
        private static final ComponentController INSTANCE = new ComponentController();
    }

    private ComponentController() {
        RootTools.debugMode = true;
        try {
            shell = RootTools.getShell(true);
        } catch (RootDeniedException | TimeoutException | IOException e) {
            e.printStackTrace();
        }
    }


    public static ComponentController getInstance() {
        return ComponentControllerHolder.INSTANCE;
    }
}
