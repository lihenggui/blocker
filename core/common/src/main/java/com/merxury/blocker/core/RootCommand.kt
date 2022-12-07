/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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
