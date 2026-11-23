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

package com.merxury.blocker.core.rule.work

import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.backup.RuleRunResult
import com.merxury.core.ifw.IIntentFirewall
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Brings the component list the app list and app detail screens still read up to date after a
 * restore or reset changed the system through the new repositories. That list holds only the user
 * Blocker runs in and reads the firewall through [IIntentFirewall], whose rule cache is dropped.
 */
class LegacyComponentCache @Inject constructor(
    private val componentRepository: ComponentRepository,
    private val intentFirewall: IIntentFirewall,
) {
    /** Re-reads the packages [result] changed, when they belong to [user] or [user] is null. */
    suspend fun refreshAfter(result: RuleRunResult, user: UserId?) {
        val packages = (result as? RuleRunResult.Completed)?.summary?.changedPackages.orEmpty()
        if (packages.isEmpty()) return
        intentFirewall.resetCache()
        if (user != null && user != ownUser()) return
        packages.sorted().forEach { packageName ->
            try {
                componentRepository.updateComponentList(packageName).collect {}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Cannot refresh the component list of $packageName")
            }
        }
    }
}
