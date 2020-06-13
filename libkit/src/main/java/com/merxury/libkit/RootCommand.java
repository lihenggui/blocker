package com.merxury.libkit;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.topjohnwu.superuser.Shell;

import java.util.List;

/**
 * Created by Mercury on 2018/2/4.
 */

public class RootCommand {
    private static Logger logger = XLog.tag("RootCommand").build();

    @NonNull
    public synchronized static String runBlockingCommand(final String comm) {
        logger.d("Execute command " + comm);
        List<String> result = Shell.su(comm).exec().getOut();
        return TextUtils.join("\n", result);
    }
}
