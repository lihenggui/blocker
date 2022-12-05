package com.merxury.blocker.core.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class StorageUtils {
    private static final String IFW_FOLDER = "/ifw";
    private static final File DATA_DIRECTORY
            = getDirectory("ANDROID_DATA", "/data");
    private static final File SECURE_DATA_DIRECTORY
            = getDirectory("ANDROID_SECURE_DATA", "/data/secure");

    private static final String SYSTEM_PROPERTY_EFS_ENABLED = "persist.security.efs.enabled";

    private static File getDirectory(String variableName, String defaultPath) {
        String path = System.getenv(variableName);
        return path == null ? new File(defaultPath) : new File(path);
    }

    /**
     * Gets the system directory available for secure storage.
     * If Encrypted File system is enabled, it returns an encrypted directory (/data/secure/system).
     * Otherwise, it returns the unencrypted /data/system directory.
     *
     * @return File object representing the secure storage system directory.
     */

    public static File getSystemSecureDirectory() {
        if (isEncryptedFilesystemEnabled()) {
            return new File(SECURE_DATA_DIRECTORY, "system");
        } else {
            return new File(DATA_DIRECTORY, "system");
        }
    }


    /**
     * Returns whether the Encrypted File System feature is enabled on the device or not.
     *
     * @return <code>true</code> if Encrypted File System feature is enabled, <code>false</code>
     * if disabled.
     */
    public static boolean isEncryptedFilesystemEnabled() {
        try {
            return (boolean) Class.forName("android.os.SystemProperties")
                    .getMethod("getBoolean", String.class, boolean.class)
                    .invoke(null, SYSTEM_PROPERTY_EFS_ENABLED, false);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Log.e("StorgeUtils", "Cannot access internal method", e);
            return false;
        }
    }

    @NonNull
    public static String getIfwFolder() {
        return StorageUtils.getSystemSecureDirectory() + IFW_FOLDER + File.separator;
    }
}
