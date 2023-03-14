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

package com.merxury.blocker.search

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

fun MacrobenchmarkScope.goToSearchScreen() {
    device.findObject(By.text("Search")).click()
    device.waitForIdle()
    // Wait until interests are shown on screen
    device.wait(Until.hasObject(By.res("blockerTopAppBar")), 2_000)
    val topAppBar = device.findObject(By.res("blockerTopAppBar"))
    topAppBar.wait(Until.hasObject(By.text("Apps, components and rules")), 2_000)

    device.wait(Until.gone(By.res("loadingWheel")), 5_000)
}
