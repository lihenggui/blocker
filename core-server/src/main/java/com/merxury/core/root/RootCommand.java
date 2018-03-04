package com.merxury.core.root;

import android.util.Log;

import com.merxury.exception.ProcessUnexpectedTerminateException;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.functions.Function;

/**
 * Created by Mercury on 2018/2/4.
 */

public class RootCommand {
    private static final String TAG = "RootCommand";

    public static String runBlockingCommand(final String comm) throws RootDeniedException, IOException, TimeoutException {
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
        }).blockingFirst("");
    }
}
