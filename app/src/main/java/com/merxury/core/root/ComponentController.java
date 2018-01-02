package com.merxury.core.root;

import android.util.Log;

import com.merxury.exception.ProcessUnexpectedTerminateException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootShell.execution.Shell;
import com.stericson.RootTools.RootTools;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.functions.Function;

/**
 * Created by Mercury on 2017/12/31.
 * A class that controls the state of application components
 */

public class ComponentController {
    private static final String TAG = "ComponentController";
    private static final String DISABLE_COMPONENT_TEMPLATE = "pm disable %s/.%s";
    private static final String ENABLE_COMPONENT_TEMPLATE = "pm enable %s/.%s";
    private static final String FAILED_EXCEPTION_MSG = "java.lang.IllegalArgumentException";

    private Shell shell;

    private ComponentController() {
        RootTools.debugMode = true;
    }

    public static ComponentController getInstance() {
        return ComponentControllerHolder.INSTANCE;
    }

    /**
     * a method to change a component's state
     *
     * @param packageName   package name
     * @param componentName component name
     * @param state         true: enable component
     *                      false: disable component
     * @return true : changed component state successfully
     * false: cannot disable component
     */
    public boolean switchComponent(String packageName, String componentName, boolean state) {
        final String comm;
        if (state) {
            comm = String.format(ENABLE_COMPONENT_TEMPLATE, packageName, componentName);
        } else {
            comm = String.format(DISABLE_COMPONENT_TEMPLATE, packageName, componentName);
        }
        return Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(final ObservableEmitter<String> emitter) throws Exception {
                Command command = new Command(0, comm) {
                    @Override
                    public void commandOutput(int id, String line) {
                        //TODO get output
                        Log.d(TAG, "commandOutput");
                        Log.d(TAG, line);
                        emitter.onNext(line);
                        super.commandOutput(id, line);
                    }

                    @Override
                    public void commandTerminated(int id, String reason) {
                        String msg = "commandTerminated";
                        Log.d(TAG, msg);
                        emitter.onError(new ProcessUnexpectedTerminateException(msg));
                        super.commandTerminated(id, reason);
                    }

                    @Override
                    public void commandCompleted(int id, int exitcode) {
                        Log.d(TAG, "commandCompleted");
                        emitter.onComplete();
                        super.commandCompleted(id, exitcode);
                    }
                };
                RootTools.getShell(true).add(command);
            }
        }).map(new Function<String, Boolean>() {
            @Override
            public Boolean apply(String s) throws Exception {
                return !s.contains(FAILED_EXCEPTION_MSG);
            }
        }).blockingFirst();
    }

    private static class ComponentControllerHolder {
        private static final ComponentController INSTANCE = new ComponentController();
    }
}
