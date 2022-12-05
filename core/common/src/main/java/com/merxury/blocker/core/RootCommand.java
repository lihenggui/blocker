package com.merxury.blocker.core;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;

import java.util.List;

/**
 * Created by Mercury on 2018/2/4.
 */

public class RootCommand {

    @NonNull
    public synchronized static String runBlockingCommand(final String comm) {
        Boolean rootGranted = Shell.isAppGrantedRoot();
        if (rootGranted == null || Boolean.FALSE.equals(rootGranted)) {
            throw new RuntimeException("Root unavailable");
        }
        List<String> result = Shell.cmd(comm).exec().getOut();
        return TextUtils.join("\n", result);
    }
}
