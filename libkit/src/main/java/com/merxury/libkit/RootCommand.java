package com.merxury.libkit;

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
        if (!Shell.rootAccess()) {
            throw new RuntimeException("Root unavailable");
        }
        List<String> result = Shell.su(comm).exec().getOut();
        return TextUtils.join("\n", result);
    }
}
