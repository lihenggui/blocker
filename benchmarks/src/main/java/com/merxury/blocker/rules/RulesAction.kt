/*
 * Copyright 2023 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.rules

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.merxury.blocker.flingElementDownUp

fun MacrobenchmarkScope.goToRulesScreen() {
    device.findObject(By.text("Rules")).click()
    device.waitForIdle()
    // Wait until saved title are shown on screen
    // Timeout here is quite big, because sometimes data loading takes a long time
    device.wait(Until.hasObject(By.res("blockerTopAppBar")), 60_000)
    device.wait(Until.hasObject(By.res("rule:list")), 60_000)
}

fun MacrobenchmarkScope.rulesScrollListDownUp() {
    val feedList = device.findObject(By.res("rule:list"))
    device.flingElementDownUp(feedList)
}
