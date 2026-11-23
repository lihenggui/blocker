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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Instant

/**
 * Most names bound into one `IN (...)` list. Stays well below SQLite's limit of 999 bind variables
 * on older Android versions, leaving room for the other arguments of the statement.
 */
internal const val MAX_BOUND_NAMES = 500

private const val COMPONENT_COLUMNS = "d.*, p.setting AS pm_setting, i.block AS ifw_block"
private const val JOIN_STATE_FOR_USER =
    "LEFT JOIN component_pm_setting AS p ON p.package_name = d.package_name " +
        "AND p.class_name = d.class_name AND p.user_id = :userId " +
        "LEFT JOIN component_ifw_block AS i ON i.package_name = d.package_name AND i.class_name = d.class_name"

/**
 * Every component of the packages installed for `:userId`. Driven by the user's apps from the
 * covering `(user_id, package_name)` index, with every joined table searched by its key.
 */
internal const val ALL_COMPONENTS_QUERY =
    "SELECT $COMPONENT_COLUMNS FROM app AS a " +
        "JOIN component_declaration AS d ON d.package_name = a.package_name " +
        JOIN_STATE_FOR_USER +
        " WHERE a.user_id = :userId"

/** The app index already yields package order, so SQLite sorts only the classes of each package. */
internal const val COMPONENT_ORDER = " ORDER BY a.package_name, d.class_name"

/**
 * Reads and writes the component cache.
 *
 * Referential rules, which keep the cache equal to a snapshot of the system:
 * - Removing a user removes its apps; removing an app for a user removes that user's
 *   PackageManager settings for the package. Both are foreign keys with `ON DELETE CASCADE`.
 * - Removing a declaration removes its PackageManager settings and Intent Firewall state, also by
 *   cascade.
 * - When no user has a package any more, its declarations (and so its Intent Firewall state) and
 *   its scan marker are removed. No table holds one row per package to cascade from, so the
 *   methods that remove apps delete these explicitly in the same transaction.
 *
 * The foreign keys also mean a user must be stored before its apps, and settings are stored only
 * for declared components of installed apps. Parents are only ever upserted, never replaced, as
 * `INSERT OR REPLACE` would delete a row's dependants.
 *
 * Observers ignore re-queries that return the same result: Room re-runs a query after any write to
 * a table it reads, so a write for another user or package still costs a query but does not emit.
 */
@Dao
abstract class ComponentCacheDao {

    // Users

    /** All users, by id. */
    fun observeUsers(): Flow<List<DeviceUserEntity>> = queryUsers().distinctUntilChanged()

    /**
     * Makes the stored users equal to [users]: changed ones are updated and users not in the list
     * are removed with their apps, settings, and the packages no remaining user has.
     */
    @Transaction
    open suspend fun replaceUsers(users: List<DeviceUserEntity>) {
        val existing = getUsers().associateBy { it.userId }
        val incoming = users.associateBy { it.userId }
        val removed = (existing.keys - incoming.keys).toList()
        if (removed.isNotEmpty()) {
            val packages = removed.chunked(MAX_BOUND_NAMES).flatMap { packagesOfUsers(it) }.toSet()
            removed.chunked(MAX_BOUND_NAMES).forEach { deleteUsers(it) }
            deleteUnusedPackages(packages)
        }
        upsertUsers(incoming.values.filter { existing[it.userId] != it })
    }

    // Apps

    /** The apps installed for [userId], by package name. */
    fun observeApps(userId: Int): Flow<List<AppEntity>> = queryApps(userId).distinctUntilChanged()

    /** The app for [userId], or null while it is not installed for that user. */
    fun observeApp(packageName: String, userId: Int): Flow<AppEntity?> = queryApp(packageName, userId).distinctUntilChanged()

    /** The apps installed for [userId], by package name. */
    @Query("SELECT * FROM app WHERE user_id = :userId ORDER BY package_name")
    abstract suspend fun getApps(userId: Int): List<AppEntity>

    /**
     * The apps for [userId] whose label or package name contains [keyword], ignoring ASCII case.
     * `%` and `_` in [keyword] match themselves, not any text.
     */
    fun searchApps(userId: Int, keyword: String): Flow<List<AppEntity>> = querySearchApps(userId, keyword.escapeLikeWildcards()).distinctUntilChanged()

    /**
     * Makes the apps stored for [userId] equal to [apps]: changed ones are updated and apps not in
     * the list are removed with their settings for this user, and with the package's declarations
     * when no user has it any more. The user must be stored; otherwise this throws a constraint
     * error. A later entry for the same package wins.
     */
    @Transaction
    open suspend fun replaceApps(userId: Int, apps: List<AppEntity>) {
        require(apps.all { it.userId == userId }) { "An app does not belong to user $userId" }
        val existing = getApps(userId).associateBy { it.packageName }
        val incoming = apps.associateBy { it.packageName }
        val removed = existing.keys - incoming.keys
        removed.chunked(MAX_BOUND_NAMES).forEach { deleteApps(userId, it) }
        deleteUnusedPackages(removed)
        upsertApps(incoming.values.filter { existing[it.packageName] != it })
    }

    /** Stores [app], keeping its settings. Its user must be stored; otherwise this throws a constraint error. */
    @Upsert
    abstract suspend fun upsertApp(app: AppEntity)

    /**
     * Removes the app for [userId] with that user's settings for it, and with the package's
     * declarations when no user has it any more.
     */
    @Transaction
    open suspend fun deleteApp(packageName: String, userId: Int) {
        deleteApps(userId, listOf(packageName))
        deleteUnusedPackages(setOf(packageName))
    }

    // Components

    /** The components of [packageName] with [userId]'s settings, by class name. */
    fun observeComponents(packageName: String, userId: Int): Flow<List<ComponentRow>> = queryComponents(packageName, userId).distinctUntilChanged()

    /** One component with [userId]'s setting, or null while it is not declared. */
    fun observeComponent(packageName: String, className: String, userId: Int): Flow<ComponentRow?> = queryComponent(packageName, className, userId).distinctUntilChanged()

    /** The declarations stored for [packageName], by class name. Empty when it has not been read. */
    @Query("SELECT * FROM component_declaration WHERE package_name = :packageName ORDER BY class_name")
    abstract suspend fun getDeclarations(packageName: String): List<ComponentDeclarationEntity>

    /** Every component of every package installed for [userId], by package and class name. */
    fun observeAllComponents(userId: Int): Flow<List<ComponentRow>> = queryAllComponents(userId).distinctUntilChanged()

    /**
     * The components of packages installed for [userId] whose class name contains [keyword],
     * ignoring ASCII case. `%` and `_` in [keyword] match themselves, not any text.
     */
    fun searchComponents(userId: Int, keyword: String): Flow<List<ComponentRow>> = querySearchComponents(userId, keyword.escapeLikeWildcards()).distinctUntilChanged()

    /** The stored settings equal to [setting] for [userId], such as every disabled component. */
    @Query("SELECT * FROM component_pm_setting WHERE user_id = :userId AND setting = :setting ORDER BY package_name, class_name")
    abstract suspend fun getPmSettings(userId: Int, setting: PmSetting): List<ComponentPmSettingEntity>

    /**
     * The app `lastUpdateTime` at which the declarations of [packageName] were last read, or null
     * when they have not been read.
     */
    @Query("SELECT app_last_update_time FROM component_scan WHERE package_name = :packageName")
    abstract suspend fun scanMarker(packageName: String): Instant?

    /**
     * Makes the declarations of [packageName] equal to [declarations] and records
     * [appLastUpdateTime] as its scan marker. Declarations not in the list are removed with their
     * settings; the settings of the others are kept. A later entry for the same class wins.
     *
     * @return false, writing nothing, when no user has the package, so a package removed during
     * the read leaves nothing behind
     */
    @Transaction
    open suspend fun replaceDeclarations(
        packageName: String,
        declarations: List<ComponentDeclarationEntity>,
        appLastUpdateTime: Instant,
    ): Boolean {
        require(declarations.all { it.packageName == packageName }) { "A declaration is not in $packageName" }
        if (!isInstalledForAnyUser(packageName)) return false
        val existing = getDeclarations(packageName).associateBy { it.className }
        val incoming = declarations.associateBy { it.className }
        (existing.keys - incoming.keys).chunked(MAX_BOUND_NAMES).forEach { deleteDeclarations(packageName, it) }
        upsertDeclarations(incoming.values.filter { existing[it.className] != it })
        upsertScan(ComponentScanEntity(packageName, appLastUpdateTime))
        return true
    }

    /**
     * Stores [userId]'s PackageManager settings for components of [packageName], keyed by class
     * name. Classes without a declaration, and every entry while the app is not installed for
     * [userId], are skipped: the cache describes only declared components of installed apps.
     */
    @Transaction
    open suspend fun upsertPmSettings(packageName: String, userId: Int, settings: Map<String, PmSetting>) {
        if (settings.isEmpty() || !isInstalled(packageName, userId)) return
        val declared = declaredClassNames(packageName).toHashSet()
        val existing = pmSettingsOf(packageName, userId).associate { it.className to it.setting }
        val changed = settings.filter { (className, setting) -> className in declared && existing[className] != setting }
        upsertPmSettingRows(changed.map { (className, setting) -> ComponentPmSettingEntity(packageName, className, userId, setting) })
    }

    /**
     * Stores the Intent Firewall state of components of [packageName], keyed by class name.
     * Classes without a declaration are skipped.
     */
    @Transaction
    open suspend fun upsertIfwBlocks(packageName: String, blocks: Map<String, IfwBlock>) {
        if (blocks.isEmpty()) return
        val declared = declaredClassNames(packageName).toHashSet()
        val existing = ifwBlocksOf(packageName).associate { it.className to it.block }
        val changed = blocks.filter { (className, block) -> className in declared && existing[className] != block }
        upsertIfwBlockRows(changed.map { (className, block) -> ComponentIfwBlockEntity(packageName, className, block) })
    }

    /** Forgets the Intent Firewall state of [packageName], so its components read as not read yet. */
    @Query("DELETE FROM component_ifw_block WHERE package_name = :packageName")
    abstract suspend fun clearIfwBlocks(packageName: String)

    /** Removes everything about [packageName]: the app for every user, its declarations and settings. */
    @Transaction
    open suspend fun deletePackage(packageName: String) {
        deleteAppForAllUsers(packageName)
        deleteDeclarationsOf(packageName)
        deleteScan(packageName)
    }

    private suspend fun deleteUnusedPackages(packageNames: Collection<String>) {
        packageNames.chunked(MAX_BOUND_NAMES).forEach {
            deleteUnusedDeclarations(it)
            deleteUnusedScans(it)
        }
    }

    // Queries behind the public API

    @Query("SELECT * FROM device_user ORDER BY user_id")
    protected abstract fun queryUsers(): Flow<List<DeviceUserEntity>>

    @Query("SELECT * FROM device_user")
    protected abstract suspend fun getUsers(): List<DeviceUserEntity>

    @Upsert
    protected abstract suspend fun upsertUsers(users: List<DeviceUserEntity>)

    @Query("DELETE FROM device_user WHERE user_id IN (:userIds)")
    protected abstract suspend fun deleteUsers(userIds: List<Int>)

    @Query("SELECT DISTINCT package_name FROM app WHERE user_id IN (:userIds)")
    protected abstract suspend fun packagesOfUsers(userIds: List<Int>): List<String>

    @Query("SELECT * FROM app WHERE user_id = :userId ORDER BY package_name")
    protected abstract fun queryApps(userId: Int): Flow<List<AppEntity>>

    @Query("SELECT * FROM app WHERE package_name = :packageName AND user_id = :userId")
    protected abstract fun queryApp(packageName: String, userId: Int): Flow<AppEntity?>

    @Query(
        "SELECT * FROM app WHERE user_id = :userId " +
            "AND (label LIKE '%' || :keyword || '%' ESCAPE '\\' OR package_name LIKE '%' || :keyword || '%' ESCAPE '\\') " +
            "ORDER BY package_name",
    )
    protected abstract fun querySearchApps(userId: Int, keyword: String): Flow<List<AppEntity>>

    @Upsert
    protected abstract suspend fun upsertApps(apps: List<AppEntity>)

    @Query("DELETE FROM app WHERE user_id = :userId AND package_name IN (:packageNames)")
    protected abstract suspend fun deleteApps(userId: Int, packageNames: List<String>)

    @Query("DELETE FROM app WHERE package_name = :packageName")
    protected abstract suspend fun deleteAppForAllUsers(packageName: String)

    @Query("SELECT EXISTS(SELECT 1 FROM app WHERE package_name = :packageName)")
    protected abstract suspend fun isInstalledForAnyUser(packageName: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM app WHERE package_name = :packageName AND user_id = :userId)")
    protected abstract suspend fun isInstalled(packageName: String, userId: Int): Boolean

    @Query(
        "SELECT $COMPONENT_COLUMNS FROM component_declaration AS d $JOIN_STATE_FOR_USER " +
            "WHERE d.package_name = :packageName ORDER BY d.class_name",
    )
    protected abstract fun queryComponents(packageName: String, userId: Int): Flow<List<ComponentRow>>

    @Query(
        "SELECT $COMPONENT_COLUMNS FROM component_declaration AS d $JOIN_STATE_FOR_USER " +
            "WHERE d.package_name = :packageName AND d.class_name = :className",
    )
    protected abstract fun queryComponent(packageName: String, className: String, userId: Int): Flow<ComponentRow?>

    @Query(ALL_COMPONENTS_QUERY + COMPONENT_ORDER)
    protected abstract fun queryAllComponents(userId: Int): Flow<List<ComponentRow>>

    @Query(ALL_COMPONENTS_QUERY + " AND d.class_name LIKE '%' || :keyword || '%' ESCAPE '\\'" + COMPONENT_ORDER)
    protected abstract fun querySearchComponents(userId: Int, keyword: String): Flow<List<ComponentRow>>

    @Query("SELECT class_name FROM component_declaration WHERE package_name = :packageName")
    protected abstract suspend fun declaredClassNames(packageName: String): List<String>

    @Upsert
    protected abstract suspend fun upsertDeclarations(declarations: List<ComponentDeclarationEntity>)

    @Query("DELETE FROM component_declaration WHERE package_name = :packageName AND class_name IN (:classNames)")
    protected abstract suspend fun deleteDeclarations(packageName: String, classNames: List<String>)

    @Query("DELETE FROM component_declaration WHERE package_name = :packageName")
    protected abstract suspend fun deleteDeclarationsOf(packageName: String)

    @Query(
        "DELETE FROM component_declaration WHERE package_name IN (:packageNames) " +
            "AND NOT EXISTS (SELECT 1 FROM app WHERE app.package_name = component_declaration.package_name)",
    )
    protected abstract suspend fun deleteUnusedDeclarations(packageNames: List<String>)

    @Upsert
    protected abstract suspend fun upsertScan(scan: ComponentScanEntity)

    @Query("DELETE FROM component_scan WHERE package_name = :packageName")
    protected abstract suspend fun deleteScan(packageName: String)

    @Query(
        "DELETE FROM component_scan WHERE package_name IN (:packageNames) " +
            "AND NOT EXISTS (SELECT 1 FROM app WHERE app.package_name = component_scan.package_name)",
    )
    protected abstract suspend fun deleteUnusedScans(packageNames: List<String>)

    @Query("SELECT * FROM component_pm_setting WHERE package_name = :packageName AND user_id = :userId")
    protected abstract suspend fun pmSettingsOf(packageName: String, userId: Int): List<ComponentPmSettingEntity>

    @Upsert
    protected abstract suspend fun upsertPmSettingRows(rows: List<ComponentPmSettingEntity>)

    @Query("SELECT * FROM component_ifw_block WHERE package_name = :packageName")
    protected abstract suspend fun ifwBlocksOf(packageName: String): List<ComponentIfwBlockEntity>

    @Upsert
    protected abstract suspend fun upsertIfwBlockRows(rows: List<ComponentIfwBlockEntity>)
}

/** Escapes `\`, `%` and `_` for a `LIKE` pattern that declares `ESCAPE '\'`. */
internal fun String.escapeLikeWildcards(): String = buildString(length) {
    for (char in this@escapeLikeWildcards) {
        if (char == '\\' || char == '%' || char == '_') append('\\')
        append(char)
    }
}
