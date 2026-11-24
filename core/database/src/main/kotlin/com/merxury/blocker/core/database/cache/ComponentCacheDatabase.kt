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

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.UserKind
import kotlin.time.Instant

/** The file name of [ComponentCacheDatabase]. */
internal const val COMPONENT_CACHE_DATABASE_NAME = "component_cache"

/**
 * A cache of system state: users, the apps installed for each user, component declarations,
 * per-user PackageManager settings and device-wide Intent Firewall state.
 *
 * It holds no user data and can be dropped and rebuilt from the system at any time, which is why
 * it is opened with destructive migration. Public so that tests of the repositories over it can
 * open an in-memory instance; production code gets [ComponentCacheDao] from Hilt.
 */
@Database(
    entities = [
        DeviceUserEntity::class,
        AppEntity::class,
        ComponentDeclarationEntity::class,
        ComponentScanEntity::class,
        ComponentPmSettingEntity::class,
        ComponentIfwBlockEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(CacheConverters::class)
abstract class ComponentCacheDatabase : RoomDatabase() {
    abstract fun componentCacheDao(): ComponentCacheDao
}

/** Stores enums by name, so reordering or adding constants cannot change what a row means. */
internal class CacheConverters {
    @TypeConverter fun instantToMillis(value: Instant): Long = value.toEpochMilliseconds()

    @TypeConverter fun millisToInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @TypeConverter fun userKindToName(value: UserKind): String = value.name

    @TypeConverter fun nameToUserKind(value: String): UserKind = UserKind.valueOf(value)

    @TypeConverter fun appEnabledSettingToName(value: AppEnabledSetting): String = value.name

    @TypeConverter fun nameToAppEnabledSetting(value: String): AppEnabledSetting = AppEnabledSetting.valueOf(value)

    @TypeConverter fun componentTypeToName(value: ComponentType): String = value.name

    @TypeConverter fun nameToComponentType(value: String): ComponentType = ComponentType.valueOf(value)

    @TypeConverter fun pmSettingToName(value: PmSetting): String = value.name

    @TypeConverter fun nameToPmSetting(value: String): PmSetting = PmSetting.valueOf(value)

    @TypeConverter fun ifwBlockToName(value: IfwBlock): String = value.name

    @TypeConverter fun nameToIfwBlock(value: String): IfwBlock = IfwBlock.valueOf(value)
}
