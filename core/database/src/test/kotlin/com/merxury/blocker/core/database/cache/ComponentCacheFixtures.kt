/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.database.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.UserKind
import org.junit.After
import kotlin.time.Instant

internal const val OWNER = 0
internal const val WORK = 10

/** Opens an in-memory component cache for each test. Subclasses run with Robolectric. */
abstract class ComponentCacheTestBase {
    internal val db: ComponentCacheDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        ComponentCacheDatabase::class.java,
    ).allowMainThreadQueries().build()
    internal val dao: ComponentCacheDao = db.componentCacheDao()

    @After
    fun closeDatabase() = db.close()

    /** Stores the owner and a work profile, and [packages] installed for [userIds]. */
    internal suspend fun install(vararg packages: String, userIds: List<Int> = listOf(OWNER, WORK)) {
        dao.replaceUsers(listOf(user(OWNER), user(WORK)))
        userIds.forEach { userId ->
            packages.forEach { dao.upsertApp(app(it, userId)) }
        }
    }
}

internal fun user(
    id: Int,
    kind: UserKind = if (id == OWNER) UserKind.OWNER else UserKind.WORK_PROFILE,
    name: String? = "User $id",
    parentId: Int? = if (id == OWNER) null else OWNER,
    running: Boolean = true,
) = DeviceUserEntity(userId = id, name = name, kind = kind, parentId = parentId, running = running)

internal fun app(
    packageName: String,
    userId: Int = OWNER,
    label: String = packageName,
    lastUpdateTime: Long = 2_000,
    enabledSetting: AppEnabledSetting = AppEnabledSetting.DEFAULT,
) = AppEntity(
    packageName = packageName,
    userId = userId,
    uid = 10_000 + userId * 100_000,
    label = label,
    versionName = "1.0",
    versionCode = 1,
    isSystem = false,
    enabledSetting = enabledSetting,
    apkPath = "/data/app/$packageName/base.apk",
    firstInstallTime = Instant.fromEpochMilliseconds(1_000),
    lastUpdateTime = Instant.fromEpochMilliseconds(lastUpdateTime),
    minSdk = 26,
    targetSdk = 35,
)

internal fun declaration(
    packageName: String,
    className: String = "$packageName.Main",
    type: ComponentType = ComponentType.ACTIVITY,
    exported: Boolean = false,
    enabledInManifest: Boolean = true,
) = ComponentDeclarationEntity(packageName, className, type, exported, enabledInManifest)

internal suspend fun ComponentCacheDao.declare(packageName: String, vararg declarations: ComponentDeclarationEntity) {
    check(replaceDeclarations(packageName, declarations.toList(), Instant.fromEpochMilliseconds(2_000)))
}
