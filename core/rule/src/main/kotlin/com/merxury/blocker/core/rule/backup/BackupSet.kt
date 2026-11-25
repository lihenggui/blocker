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

package com.merxury.blocker.core.rule.backup

import com.merxury.blocker.core.model.component.UserId

/** The extension of a Blocker rule backup file, `<package>.json`. */
internal const val BLOCKER_RULE_EXTENSION = ".json"

private const val USER_FOLDER_PREFIX = "user-"

/**
 * The Blocker rule backups of one user in a backup folder, one `<package>.json` per app.
 *
 * The owner's set, user 0, lies directly in the folder, where every earlier version of Blocker
 * reads and writes it, so those versions and this one share it. Any other user's set lies in the
 * sub-folder `user-<id>`, which earlier versions do not look into. User ids differ between
 * devices, so a set can be restored into any user.
 *
 * @property user whose state the set was exported from
 */
data class BackupSet(val user: UserId) {
    /** The set's folder, relative to the backup folder; `""` for the owner. */
    val folder: String get() = if (user == UserId.SYSTEM) "" else "$USER_FOLDER_PREFIX${user.value}"

    /** The path of [packageName]'s backup file in this set. */
    fun fileOf(packageName: String): String = pathOf(folder, packageName + BLOCKER_RULE_EXTENSION)

    companion object {
        val OWNER = BackupSet(UserId.SYSTEM)

        /**
         * The set kept in the sub-folder [name], or null when [name] is not `user-<id>` for a
         * user other than the owner, written as Android writes the id: digits without leading
         * zeros.
         */
        fun fromFolderName(name: String): BackupSet? {
            val digits = name.removePrefix(USER_FOLDER_PREFIX)
            if (digits == name || digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
            val id = digits.toIntOrNull() ?: return null
            if (id == 0 || id.toString() != digits) return null
            return BackupSet(UserId(id))
        }
    }
}

/**
 * The sets in [storage] that hold at least one backup file, the owner's first and then by user
 * id, so a caller can offer to restore set X into user Y.
 *
 * @throws BackupStorageException when the folder cannot be listed
 */
suspend fun listBackupSets(storage: BackupStorage): List<BackupSet> {
    val others = storage.listFolders("").mapNotNull(BackupSet::fromFolderName).sortedBy { it.user.value }
    return (listOf(BackupSet.OWNER) + others).filter { set ->
        storage.hasBlockerRules(set)
    }
}

/** Other sets to offer when the target's own set contains no Blocker rules. */
internal suspend fun BackupStorage.alternativeBackupSets(target: UserId): List<BackupSet> {
    val ownSet = BackupSet(target)
    return if (hasBlockerRules(ownSet)) emptyList() else listBackupSets(this).filterNot { it == ownSet }
}

private suspend fun BackupStorage.hasBlockerRules(set: BackupSet): Boolean = listFiles(set.folder).any { it.endsWith(BLOCKER_RULE_EXTENSION) }
