/*
 * Copyright 2023 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.merxury.ifw.util

import timber.log.Timber
import java.io.File
import java.lang.reflect.InvocationTargetException

object IfwStorageUtils {
    private const val IFW_FOLDER = "/ifw"
    private val DATA_DIRECTORY = getDirectory("ANDROID_DATA", "/data")
    private val SECURE_DATA_DIRECTORY = getDirectory("ANDROID_SECURE_DATA", "/data/secure")
    private const val SYSTEM_PROPERTY_EFS_ENABLED = "persist.security.efs.enabled"
    private fun getDirectory(variableName: String, defaultPath: String): File {
        val path = System.getenv(variableName)
        return if (path == null) File(defaultPath) else File(path)
    }

    @get:SuppressWarnings("WeakerAccess")
    val systemSecureDirectory: File
        /**
         * Gets the system directory available for secure storage.
         * If Encrypted File system is enabled, it returns an encrypted directory (/data/secure/system).
         * Otherwise, it returns the unencrypted /data/system directory.
         *
         * @return File object representing the secure storage system directory.
         */
        get() = if (isEncryptedFilesystemEnabled) {
            File(SECURE_DATA_DIRECTORY, "system")
        } else {
            File(DATA_DIRECTORY, "system")
        }

    @get:SuppressWarnings("WeakerAccess")
    val isEncryptedFilesystemEnabled: Boolean
        /**
         * Returns whether the Encrypted File System feature is enabled on the device or not.
         *
         * @return `true` if Encrypted File System feature is enabled, `false`
         * if disabled.
         */
        get() = try {
            Class.forName("android.os.SystemProperties")
                .getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, SYSTEM_PROPERTY_EFS_ENABLED, false) as Boolean
        } catch (e: ClassNotFoundException) {
            Timber.e(e, "Cannot access internal method")
            false
        } catch (e: NoSuchMethodException) {
            Timber.e(e, "Cannot access internal method")
            false
        } catch (e: IllegalAccessException) {
            Timber.e(e, "Cannot access internal method")
            false
        } catch (e: InvocationTargetException) {
            Timber.e(e, "Cannot access internal method")
            false
        }
    val ifwFolder: String
        get() = systemSecureDirectory.toString() + IFW_FOLDER + File.separator
}
