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
package com.merxury.blocker.core.controllers.root.api

import android.annotation.SuppressLint
import android.os.IBinder
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Helper class for accessing system services.
 * Copied from [...](https://github.com/RikkaApps/Shizuku-API/blob/01e08879d58a5cb11a333535c6ddce9f7b7c88ff/api/src/main/java/rikka/shizuku/SystemServiceHelper.java)
 */
@SuppressLint("PrivateApi")
object SystemServiceHelper {
    private val SYSTEM_SERVICE_CACHE: MutableMap<String, IBinder?> = HashMap()
    private var getService: Method? = null

    init {
        try {
            val sm = Class.forName("android.os.ServiceManager")
            getService = sm.getMethod("getService", String::class.java)
        } catch (e: ClassNotFoundException) {
            Timber.w(e)
        } catch (e: NoSuchMethodException) {
            Timber.w(e)
        }
    }

    /**
     * Returns a reference to a service with the given name.
     *
     * @param name the name of the service to get such as "package" for android.content.pm.IPackageManager
     * @return a reference to the service, or `null` if the service doesn't exist
     */
    fun getSystemService(name: String): IBinder? {
        var binder = SYSTEM_SERVICE_CACHE[name]
        if (binder == null) {
            try {
                binder = getService?.invoke(null, name) as? IBinder
            } catch (e: IllegalAccessException) {
                Timber.w(e)
            } catch (e: InvocationTargetException) {
                Timber.w(e)
            }
            SYSTEM_SERVICE_CACHE[name] = binder
        }
        return binder
    }
}
