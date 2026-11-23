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

package com.merxury.blocker.core.privileged

/**
 * Waits until every [CallBytes] pipe writer has ended. Robolectric backs a pipe with a file, so a
 * reader that starts before its writer ends sees a short file instead of waiting, and only one
 * descriptor of a parceled pipe works at a time. Readers in tests therefore read the original
 * descriptor once its writer is done; the device tests cover real pipes.
 *
 * @return false when a writer is still running after five seconds
 */
internal fun awaitPipeWriters(): Boolean {
    val end = System.currentTimeMillis() + 5_000
    while (System.currentTimeMillis() < end) {
        if (Thread.getAllStackTraces().keys.none { it.name == "CallBytes" && it.isAlive }) return true
        Thread.sleep(10)
    }
    return false
}
