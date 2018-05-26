package com.merxury.ifw.util;

import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootTools.RootTools;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public class PermissionUtils {
    private static final String CHMOD_TEMPLATE = "chmod %d %s";
    private static final String CHMOD_TEMPLATE_RECURSIVELY = "chmod -R %d %s";

    public static void setPermission(String filePath, int permission) throws IOException, TimeoutException, RootDeniedException {
        String comm = String.format(Locale.ENGLISH, CHMOD_TEMPLATE, permission, filePath);
        RootTools.getShell(true).add(new Command(0, comm));
    }

    public static void setPermissionRecursively(String filePath, int permission) throws IOException, TimeoutException, RootDeniedException {
        String comm = String.format(Locale.ENGLISH, CHMOD_TEMPLATE_RECURSIVELY, permission, filePath);
        RootTools.getShell(true).add(new Command(0, comm));
    }

    public static void setIfwReadable() throws IOException, TimeoutException, RootDeniedException{
        String ifwFolder = StorageUtils.getIfwFolder();
        setPermissionRecursively(ifwFolder, 777);
    }

    public static void resetIfwPermission() throws IOException, TimeoutException, RootDeniedException{
        String ifwFolder = StorageUtils.getIfwFolder();
        setPermissionRecursively(ifwFolder, 644);
    }
}
