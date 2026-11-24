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

package com.merxury.core.ifw

import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** The editors have always opened the rule files through root, regardless of controller mode. */
suspend fun <T> withIfwEditorAccess(block: suspend () -> T): T = withContext(IfwEditorAccess) { block() }

suspend fun isIfwEditorAccess(): Boolean = currentCoroutineContext()[IfwEditorAccess.Key] != null

private object IfwEditorAccess : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<IfwEditorAccess>
}

fun <T> IfwResult<T>.getOrThrow(): T = when (this) {
    is IfwResult.Success -> value

    is IfwResult.Failure -> throw IOException(
        when (val failure = reason) {
            IfwFailure.NoAccess -> "Root is not available"
            is IfwFailure.Unreadable -> "Cannot read IFW rules for ${failure.packageName}"
            is IfwFailure.WriteFailed -> failure.message ?: "Cannot save IFW rules for ${failure.packageName}"
        },
    )
}
