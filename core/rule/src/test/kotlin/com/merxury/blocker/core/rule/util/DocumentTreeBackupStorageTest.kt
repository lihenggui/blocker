// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.util

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.documentfile.provider.FakeDocumentTree
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.rule.backup.BackupStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric.setupContentProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

private const val AUTHORITY = "com.merxury.blocker.test.backup"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentTreeBackupStorageTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = FakeDocumentTree("backup", "content://$AUTHORITY/backup".toUri())
    private lateinit var provider: TempBackupProvider

    @Before
    fun setUp() {
        provider = setupContentProvider(TempBackupProvider::class.java, AUTHORITY)
        provider.root = temporaryFolder.root
    }

    @Test
    fun givenMixedEntries_whenListing_thenSeparatesFilesAndFoldersAndHandlesMissingPaths() = runTest {
        val folder = root.addExternalFolder("rules")
        folder.addExternalFolder("archive")
        folder.addExternalFile("first.json")
        folder.addExternalFile("second.json")
        val storage = storage()

        assertThat(storage.listFolders("rules")).containsExactly("archive")
        assertThat(storage.listFiles("rules")).containsExactly("first.json", "second.json")
        assertThat(storage.listFolders("rules/missing")).isEmpty()
        assertThat(storage.listFiles("rules/missing")).isEmpty()
    }

    @Test
    fun givenMissingFolders_whenWritingAndOverwritingJson_thenCreatesTheTreeAndTruncatesTheFile() = runTest {
        val storage = storage()
        val path = "rules/apps/settings.json"
        storage.write(path, "A much longer backup")
        val content = """{"enabled":true}"""

        storage.write(path, content)

        assertThat(root.directoryCreations).containsExactly("rules")
        val rules = checkNotNull(root.findFile("rules"))
        assertThat(rules.directoryCreations).containsExactly("apps")
        val apps = checkNotNull(rules.findFile("apps"))
        assertThat(apps.fileCreations).containsExactly("application/json" to "settings")
        assertThat(provider.writeModes).containsExactly("rwt", "rwt").inOrder()
        assertThat(storage.read(path)).isEqualTo(content)
    }

    @Test
    fun givenAFileAppearsAfterListing_whenWriting_thenFindsAndReusesIt() = runTest {
        val storage = storage()
        assertThat(storage.listFiles("")).isEmpty()
        val external = root.addExternalFile("settings.json")

        storage.write("settings.json", "new backup")

        assertThat(root.lookups).containsExactly("settings.json")
        assertThat(root.fileCreations).isEmpty()
        assertThat(root.listFiles().asList()).containsExactly(external)
        assertThat(storage.read("settings.json")).isEqualTo("new backup")
    }

    @Test
    fun givenAFolderAppearsAfterListing_whenWritingInsideIt_thenFindsAndReusesIt() = runTest {
        val storage = storage()
        assertThat(storage.listFolders("")).isEmpty()
        val external = root.addExternalFolder("rules")

        storage.write("rules/settings.json", "new backup")

        assertThat(root.lookups).containsExactly("rules")
        assertThat(root.directoryCreations).isEmpty()
        assertThat(root.listFiles().asList()).containsExactly(external)
        assertThat(external.fileCreations).containsExactly("application/json" to "settings")
        assertThat(storage.read("rules/settings.json")).isEqualTo("new backup")
    }

    @Test
    fun givenReadAccessIsRevoked_whenReading_thenWrapsTheSecurityException() = runTest {
        val file = root.addExternalFile("settings.json")
        val denied = SecurityException("Read access was revoked")
        shadowOf(context.contentResolver).registerInputStreamSupplier(file.uri) { throw denied }

        val failure = runCatching { storage().read("settings.json") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(BackupStorageException::class.java)
        val securityCause = generateSequence(failure) { it.cause }.filterIsInstance<SecurityException>().firstOrNull()
        assertThat(securityCause).isSameInstanceAs(denied)
    }

    @Test
    fun givenWriteAccessIsRevoked_whenWriting_thenWrapsTheSecurityException() = runTest {
        val file = root.addExternalFile("settings.json")
        val denied = SecurityException("Write access was revoked")
        shadowOf(context.contentResolver).registerOutputStreamSupplier(file.uri) { throw denied }

        val failure = runCatching { storage().write("settings.json", "backup") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(BackupStorageException::class.java)
        val securityCause = generateSequence(failure) { it.cause }.filterIsInstance<SecurityException>().firstOrNull()
        assertThat(securityCause).isSameInstanceAs(denied)
    }

    @Test
    fun givenCancelledAccess_whenReadingOrWriting_thenPropagatesTheOriginalCancellation() = runTest {
        val file = root.addExternalFile("settings.json")
        val cancelled = CancellationException("Backup was cancelled")
        shadowOf(context.contentResolver).apply {
            registerInputStreamSupplier(file.uri) { throw cancelled }
            registerOutputStreamSupplier(file.uri) { throw cancelled }
        }
        val storage = storage()

        val readFailure = runCatching { storage.read("settings.json") }.exceptionOrNull()
        val writeFailure = runCatching { storage.write("settings.json", "backup") }.exceptionOrNull()
        assertThat(readFailure).isInstanceOf(CancellationException::class.java)
        assertThat(readFailure).hasMessageThat().isEqualTo("Backup was cancelled")
        assertThat(writeFailure).isInstanceOf(CancellationException::class.java)
        assertThat(writeFailure).hasMessageThat().isEqualTo("Backup was cancelled")
    }

    @Test
    fun givenInvalidOrUnreachableTreeUris_whenOpening_thenReturnsNull() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        assertThat(DocumentTreeBackupStorage.open(context, "content://$AUTHORITY/not-a-tree", dispatcher)).isNull()
        assertThat(DocumentTreeBackupStorage.open(context, "content://unreachable.example/tree/backup", dispatcher)).isNull()
    }

    private fun TestScope.storage() = DocumentTreeBackupStorage(context, root, StandardTestDispatcher(testScheduler))
}

class TempBackupProvider : ContentProvider() {
    lateinit var root: File
    val writeModes = mutableListOf<String>()

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") writeModes += mode
        val file = root.resolve(uri.pathSegments.joinToString("/"))
        if (mode != "r") file.parentFile?.mkdirs()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unexpected insert")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected delete")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected update")
}
