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

import android.app.ActivityManager
import android.app.IActivityManager
import android.app.IApplicationThread
import android.app.ProfilerInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.IPackageDataObserver
import android.content.pm.IPackageDeleteObserver
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ParceledListSlice
import android.content.pm.UserInfo
import android.os.Bundle
import android.os.IBinder
import android.os.IUserManager

/** A system with packages and users per user id, recording what was changed and how. */
internal class FakeSystemServices : SystemServices {
    override val packageManager = FakePackageManager()
    override val activityManager = FakeActivityManager()
    override val userManager = FakeUserManager()
    override val packageInstaller = FakePackageInstaller()
    override val callerPackage = "com.merxury.blocker.test"
    override var callerUid = 0
}

internal class FakePackageManager : IPackageManager {
    /** Installed packages per user. */
    val installed = mutableMapOf<Int, MutableList<PackageInfo>>()
    val componentSettings = mutableMapOf<Pair<ComponentName, Int>, Int>()
    val appSettings = mutableMapOf<Pair<String, Int>, Int>()

    /** Names the system rejects, as releases before Android 14 do for undeclared components. */
    val rejectedComponents = mutableSetOf<ComponentName>()

    /** Accepts app setting changes without storing them, so a read back shows the old value. */
    var ignoreAppSettingChanges = false

    /** Thrown by every call when set, as a dead or refusing system would. */
    var failure: Exception? = null

    /** Each system call that changed something, by method name. */
    val changes = mutableListOf<String>()
    val flagsSeen = mutableListOf<Long>()

    fun install(user: Int, packageName: String) {
        installed.getOrPut(user) { mutableListOf() } += PackageInfo().apply { this.packageName = packageName }
    }

    private fun check() {
        failure?.let { throw it }
    }

    private fun find(packageName: String, user: Int) = installed[user]?.find { it.packageName == packageName }

    override fun getInstalledPackages(flags: Int, userId: Int): ParceledListSlice<PackageInfo> = getInstalledPackages(flags.toLong(), userId)

    override fun getInstalledPackages(flags: Long, userId: Int): ParceledListSlice<PackageInfo> {
        check()
        flagsSeen += flags
        return ParceledListSlice(installed[userId].orEmpty().toList())
    }

    override fun getPackageInfo(packageName: String, flags: Int, userId: Int): PackageInfo? = getPackageInfo(packageName, flags.toLong(), userId)

    override fun getPackageInfo(packageName: String, flags: Long, userId: Int): PackageInfo? {
        check()
        flagsSeen += flags
        return find(packageName, userId)
    }

    override fun getComponentEnabledSetting(componentName: ComponentName, userId: Int): Int {
        check()
        require(componentName !in rejectedComponents) { "Unknown component: $componentName" }
        return componentSettings[componentName to userId] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }

    /** The flags of each setComponentEnabledSetting, in order. */
    val componentFlags = mutableListOf<Int>()

    /** Accepts component setting changes without storing them, so a read back shows the old value. */
    var ignoreComponentSettingChanges = false

    override fun setComponentEnabledSetting(componentName: ComponentName, newState: Int, flags: Int, userId: Int) {
        check()
        changes += "setComponentEnabledSetting"
        componentFlags += flags
        if (!ignoreComponentSettingChanges) componentSettings[componentName to userId] = newState
    }

    override fun setComponentEnabledSetting(
        componentName: ComponentName,
        newState: Int,
        flags: Int,
        userId: Int,
        callingPackage: String,
    ) {
        check()
        changes += "setComponentEnabledSetting/$callingPackage"
        componentFlags += flags
        if (!ignoreComponentSettingChanges) componentSettings[componentName to userId] = newState
    }

    override fun setComponentEnabledSettings(settings: List<PackageManager.ComponentEnabledSetting>, userId: Int) {
        check()
        changes += "setComponentEnabledSettings"
        settings.forEach { componentSettings[it.componentName!! to userId] = it.enabledState }
    }

    override fun setComponentEnabledSettings(
        settings: List<PackageManager.ComponentEnabledSetting>,
        userId: Int,
        callingPackage: String,
    ) {
        check()
        changes += "setComponentEnabledSettings/$callingPackage"
        settings.forEach { componentSettings[it.componentName!! to userId] = it.enabledState }
    }

    override fun getApplicationEnabledSetting(packageName: String, userId: Int): Int {
        check()
        requireNotNull(find(packageName, userId)) { "Unknown package: $packageName" }
        return appSettings[packageName to userId] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }

    override fun setApplicationEnabledSetting(
        packageName: String,
        newState: Int,
        flags: Int,
        userId: Int,
        callingPackage: String,
    ) {
        check()
        changes += "setApplicationEnabledSetting/$callingPackage"
        if (!ignoreAppSettingChanges) appSettings[packageName to userId] = newState
    }

    override fun getPackageInstaller(): IPackageInstaller = unused()
    override fun deletePackage(packageName: String, observer: IPackageDeleteObserver, flags: Int) = unused()
    override fun deletePackageAsUser(packageName: String, observer: IPackageDeleteObserver, flags: Int, userId: Int) = unused()
    override fun clearApplicationUserData(packageName: String, observer: IPackageDataObserver, userId: Int) = unused()
    override fun deleteApplicationCacheFiles(packageName: String, observer: IPackageDataObserver) = unused()

    /** What clearing a cache reports: success, failure, or null for no answer, as the shell gets. */
    var cacheReply: Boolean? = true
    val cacheCleared = mutableListOf<Pair<String, Int>>()

    override fun deleteApplicationCacheFilesAsUser(packageName: String, userId: Int, observer: IPackageDataObserver) {
        check()
        cacheCleared += packageName to userId
        cacheReply?.let { observer.onRemoveCompleted(packageName, it) }
    }
    override fun asBinder(): IBinder = unused()
}

internal class FakeActivityManager : IActivityManager {
    val runningUsers = mutableSetOf(0)
    val unlockedUsers = mutableSetOf(0)
    val forceStopped = mutableListOf<Pair<String, Int>>()
    var failure: Exception? = null

    override fun forceStopPackage(packageName: String, userId: Int) {
        failure?.let { throw it }
        forceStopped += packageName to userId
    }

    override fun isUserRunning(userId: Int, flags: Int): Boolean = userId in runningUsers && (flags == 0 || userId in unlockedUsers)

    val processes = mutableListOf<ActivityManager.RunningAppProcessInfo>()
    val services = mutableListOf<ActivityManager.RunningServiceInfo>()
    var maxServicesAsked = 0

    override fun getRunningAppProcesses(): List<ActivityManager.RunningAppProcessInfo> = processes

    override fun getServices(maxNum: Int, flags: Int): List<ActivityManager.RunningServiceInfo> {
        maxServicesAsked = maxNum
        return services.take(maxNum)
    }

    override fun killBackgroundProcesses(packageName: String) = unused()
    override fun getMemoryInfo(outInfo: ActivityManager.MemoryInfo) = unused()

    /** What startService answers; null starts the service and answers its name. */
    var startAnswer: ComponentName? = null
    var startAnswersNull = false

    /** Each startService and stopService by argument count, with the component and user. */
    val serviceCalls = mutableListOf<String>()

    override fun startService(caller: IApplicationThread?, service: Intent, resolvedType: String?, callingPackage: String, userId: Int): ComponentName? = started(5, service, userId)

    override fun startService(
        caller: IApplicationThread?,
        service: Intent,
        resolvedType: String?,
        requireForeground: Boolean,
        callingPackage: String,
        userId: Int,
    ): ComponentName? = started(6, service, userId)

    override fun startService(
        caller: IApplicationThread?,
        service: Intent,
        resolvedType: String?,
        requireForeground: Boolean,
        callingPackage: String,
        callingFeatureId: String?,
        userId: Int,
    ): ComponentName? = started(7, service, userId)

    private fun started(arity: Int, service: Intent, userId: Int): ComponentName? {
        failure?.let { throw it }
        serviceCalls += "start$arity ${service.component!!.flattenToShortString()} $userId"
        return if (startAnswersNull) null else startAnswer ?: service.component
    }

    /** What an activity start answers, as the activity manager's start result. */
    var startActivityResult = 0

    /** Each activity start by argument count, with the calling package, component, flags and user. */
    val activityStarts = mutableListOf<String>()

    override fun startActivityAsUser(
        caller: IApplicationThread?,
        callingPackage: String,
        intent: Intent,
        resolvedType: String?,
        resultTo: IBinder?,
        resultWho: String?,
        requestCode: Int,
        flags: Int,
        profilerInfo: ProfilerInfo?,
        options: Bundle?,
        userId: Int,
    ): Int = startedActivity(11, callingPackage, intent, userId)

    override fun startActivityAsUserWithFeature(
        caller: IApplicationThread?,
        callingPackage: String,
        callingFeatureId: String?,
        intent: Intent,
        resolvedType: String?,
        resultTo: IBinder?,
        resultWho: String?,
        requestCode: Int,
        flags: Int,
        profilerInfo: ProfilerInfo?,
        options: Bundle?,
        userId: Int,
    ): Int = startedActivity(12, callingPackage, intent, userId)

    private fun startedActivity(arity: Int, callingPackage: String, intent: Intent, userId: Int): Int {
        failure?.let { throw it }
        activityStarts += "start$arity $callingPackage ${intent.component!!.flattenToShortString()} ${intent.flags} $userId"
        return startActivityResult
    }

    /** What stopService answers: 1 stopped, 0 not running, -1 not accessible. */
    var stopAnswer = 1

    override fun stopService(caller: IApplicationThread?, service: Intent, resolvedType: String?, userId: Int): Int {
        failure?.let { throw it }
        serviceCalls += "stop ${service.component!!.flattenToShortString()} $userId"
        return stopAnswer
    }

    /** Whether clearApplicationUserData accepts the package, and what it then reports, or null for no answer. */
    var clearDataAccepted = true
    var clearDataReply: Boolean? = true

    /** Each clearApplicationUserData by argument count, with the package and user. */
    val dataCleared = mutableListOf<String>()

    override fun clearApplicationUserData(packageName: String, observer: IPackageDataObserver, userId: Int): Boolean = clearData(3, packageName, observer, userId)

    override fun clearApplicationUserData(packageName: String, keepState: Boolean, observer: IPackageDataObserver, userId: Int): Boolean = clearData(4, packageName, observer, userId)

    private fun clearData(arity: Int, packageName: String, observer: IPackageDataObserver, userId: Int): Boolean {
        failure?.let { throw it }
        dataCleared += "$arity $packageName $userId"
        if (!clearDataAccepted) {
            observer.onRemoveCompleted(packageName, false)
            return false
        }
        clearDataReply?.let { observer.onRemoveCompleted(packageName, it) }
        return true
    }

    override fun asBinder(): IBinder = unused()
}

internal class FakeUserManager : IUserManager {
    val users = mutableListOf<UserInfo>()
    val parents = mutableMapOf<Int, Int>()

    /** Which getUsers overload was called: 1 or 3 arguments. */
    var getUsersArity = 0

    /** Refuses getProfileParent, as Android 9 and earlier do for the shell. */
    var refuseProfileParent = false

    fun addUser(id: Int, name: String, flags: Int = 0, parent: Int? = null) {
        users += UserInfo(id, name, flags).apply { profileGroupId = parent ?: id }
        if (parent != null) parents[id] = parent
    }

    override fun getUsers(excludeDying: Boolean): List<UserInfo> {
        getUsersArity = 1
        return users
    }

    override fun getUsers(excludePartial: Boolean, excludeDying: Boolean, excludePreCreated: Boolean): List<UserInfo> {
        getUsersArity = 3
        return users
    }

    override fun getProfileParent(userId: Int): UserInfo? {
        if (refuseProfileParent) throw SecurityException("You need MANAGE_USERS permission to: get the profile parent")
        return parents[userId]?.let { parent -> users.single { it.id == parent } }
    }

    override fun getUserInfo(userId: Int): UserInfo? = users.find { it.id == userId }

    override fun asBinder(): IBinder = unused()
}

private fun unused(): Nothing = throw UnsupportedOperationException("Not used by privileged calls")
