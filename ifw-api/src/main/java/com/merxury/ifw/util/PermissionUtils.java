package com.merxury.ifw.util;

import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public class PermissionUtils {
    private static final String CHMOD_TEMPLATE = "chmod %d %s";

    public static void setPermission(String filePath, int permission) throws IOException, TimeoutException, RootDeniedException {
        String comm = String.format(Locale.ENGLISH, CHMOD_TEMPLATE, permission, filePath);
        RootTools.getShell(true).add(new Command(0, comm));
    }
}
