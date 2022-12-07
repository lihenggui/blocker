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

package com.merxury.ifw.util

import com.elvishew.xlog.XLog
import com.merxury.ifw.entity.Rules
import java.io.InputStream
import org.simpleframework.xml.core.Persister

object RuleSerializer {
    private val serializer by lazy { Persister() }
    private val logger = XLog.tag("RuleSerializer").build()

    fun deserialize(inStream: InputStream): Rules? {
        return try {
            serializer.read(Rules::class.java, inStream)
        } catch (e: Exception) {
            logger.e("Not a valid ifw rule, skipping", e)
            null
        }
    }
}
