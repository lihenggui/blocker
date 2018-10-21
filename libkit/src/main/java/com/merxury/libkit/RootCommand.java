package com.merxury.libkit;

import android.support.annotation.NonNull;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.merxury.libkit.exception.ProcessUnexpectedTerminateException;
import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * Created by Mercury on 2018/2/4.
 */

public class RootCommand {
    private static Logger logger = XLog.tag("RootCommand").build();

    @NonNull
    public synchronized static String runBlockingCommand(final String comm) throws RootDeniedException, IOException, TimeoutException {
        final StringBuilder commandOutput = new StringBuilder();
        final AtomicReference<Throwable> returnException = new AtomicReference<>();
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(final ObservableEmitter<String> emitter) throws Exception {
                Command command = new Command(0, 0, comm) {
                    @Override
                    public void commandOutput(int id, String line) {
                        emitter.onNext(line);
                        super.commandOutput(id, line);
                    }

                    @Override
                    public void commandTerminated(int id, String reason) {
                        logger.d(reason);
                        emitter.onError(new ProcessUnexpectedTerminateException(reason));
                        super.commandTerminated(id, reason);
                    }

                    @Override
                    public void commandCompleted(int id, int exitcode) {
                        emitter.onComplete();
                        super.commandCompleted(id, exitcode);
                    }
                };
                RootTools.getShell(true).add(command);
            }
        }).blockingSubscribe(new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(String s) {
                commandOutput.append(s).append("\n");
            }

            @Override
            public void onError(Throwable e) {
                returnException.set(e);
            }

            @Override
            public void onComplete() {
            }
        });

        if (returnException.get() != null) {
            Throwable exception = returnException.get();
            if (exception instanceof RootDeniedException) {
                throw (RootDeniedException) exception;
            } else if (exception instanceof TimeoutException) {
                throw (TimeoutException) exception;
            } else if (exception instanceof IOException) {
                throw (IOException) exception;
            } else {
                throw new RuntimeException(exception);
            }
        }

        String output = commandOutput.toString();
        if (BuildConfig.DEBUG) {
            logger.d("Command: " + comm + "\nOutput: " + output);
        }
        return output;
    }
}
