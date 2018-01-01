package com.merxury.exception;

/**
 * Created by Mercury on 2018/1/1.
 * ProcessUnexpectedTerminateException
 */

public class ProcessUnexpectedTerminateException extends Exception {
    public ProcessUnexpectedTerminateException() {
        super();
    }

    public ProcessUnexpectedTerminateException(String message) {
        super(message);
    }

    public ProcessUnexpectedTerminateException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProcessUnexpectedTerminateException(Throwable cause) {
        super(cause);
    }
}
