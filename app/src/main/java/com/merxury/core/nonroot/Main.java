package com.merxury.core.nonroot;

import android.content.pm.PackageManager;
import android.net.Uri;

/**
 * Created by lihen on 2018/1/17.
 */

public class Main {
    public static void main(String args[]) {
        IPackageManager.Stub.asInterface(ServiceManager.getService("package"))
                .installPackageAsUser(Uri.parse("/sdcard/test.apk").toString(), new PackageManager.LegacyPackageInstallObserver(null).getBinder(), 0x00000002, "com.android.vending", 0);
    }
}
