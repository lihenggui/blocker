/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.core.database.instantinfo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface InstantComponentInfoDao {
    @Query(
        "SELECT * FROM instant_component_info WHERE package_path LIKE:packagePath " +
            "AND component_name LIKE:componentName LIMIT 1"
    )
    fun find(packagePath: String, componentName: String): InstantComponentInfo?

    @Insert
    fun insert(vararg components: InstantComponentInfo)
}
