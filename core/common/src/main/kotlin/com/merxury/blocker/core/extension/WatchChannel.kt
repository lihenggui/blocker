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

package com.merxury.blocker.core.extension

import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import com.merxury.blocker.core.extension.KWatchChannel.Mode
import com.merxury.blocker.core.extension.KWatchChannel.Mode.Recursive
import com.merxury.blocker.core.extension.KWatchChannel.Mode.SingleFile
import com.merxury.blocker.core.extension.KWatchEvent.Kind.Created
import com.merxury.blocker.core.extension.KWatchEvent.Kind.Deleted
import com.merxury.blocker.core.extension.KWatchEvent.Kind.Initialized
import com.merxury.blocker.core.extension.KWatchEvent.Kind.Modified
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes

/**
 * Watches directory. If file is supplied it will use parent directory. If it's an intent to watch just file,
 * developers must filter for the file related events themselves.
 *
 * @param [mode] - mode in which we should observe changes, can be SingleFile, SingleDirectory, Recursive
 * @param [tag] - any kind of data that should be associated with this channel
 * @param [scope] - coroutine context for the channel, optional
 */
@RequiresApi(VERSION_CODES.O)
fun File.asWatchChannel(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    mode: Mode? = null,
    tag: Any? = null,
) = KWatchChannel(
    file = this,
    mode = mode ?: if (isFile) SingleFile else Recursive,
    scope = scope,
    dispatcher = dispatcher,
    tag = tag,
)

/**
 * Channel based wrapper for Java's WatchService
 *
 * @param [file] - file or directory that is supposed to be monitored by WatchService
 * @param [scope] - CoroutineScope in within which Channel's sending loop will be running
 * @param [mode] - channel can work in one of the three modes: watching a single file,
 * watching a single directory or watching directory tree recursively
 * @param [tag] - any kind of data that should be associated with this channel, optional
 */
@OptIn(DelicateCoroutinesApi::class)
@RequiresApi(VERSION_CODES.O)
class KWatchChannel(
    val file: File,
    val scope: CoroutineScope,
    val dispatcher: CoroutineDispatcher,
    val mode: Mode,
    val tag: Any? = null,
    private val channel: Channel<KWatchEvent> = Channel(),
) : Channel<KWatchEvent> by channel {

    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val registeredKeys = ArrayList<WatchKey>()
    private val path: Path = if (file.isFile) {
        file.parentFile
    } else {
        file
    }.toPath()

    /**
     * Registers this channel to watch any changes in path directory and its subdirectories
     * if applicable. Removes any previous subscriptions.
     */
    private fun registerPaths() {
        registeredKeys.apply {
            forEach { it.cancel() }
            clear()
        }
        if (mode == Recursive) {
            Files.walkFileTree(
                path,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        subPath: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        registeredKeys += subPath.register(
                            watchService,
                            ENTRY_CREATE,
                            ENTRY_MODIFY,
                            ENTRY_DELETE,
                        )
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } else {
            registeredKeys += path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
        }
    }

    init {
        // commence emitting events from channel
        scope.launch(dispatcher) {

            // sending channel initialization event
            channel.send(
                KWatchEvent(
                    file = path.toFile(),
                    tag = tag,
                    kind = Initialized,
                ),
            )

            var shouldRegisterPath = true

            while (!isClosedForSend) {

                if (shouldRegisterPath) {
                    registerPaths()
                    shouldRegisterPath = false
                }

                val monitorKey = watchService.take()
                val dirPath = monitorKey.watchable() as? Path ?: break
                monitorKey.pollEvents().forEach {
                    val eventPath = dirPath.resolve(it.context() as Path)

                    if (mode == SingleFile && eventPath.toFile().absolutePath != file.absolutePath) {
                        return@forEach
                    }

                    val eventType = when (it.kind()) {
                        ENTRY_CREATE -> Created
                        ENTRY_DELETE -> Deleted
                        else -> Modified
                    }

                    val event = KWatchEvent(
                        file = eventPath.toFile(),
                        tag = tag,
                        kind = eventType,
                    )

                    // if any folder is created or deleted... and we are supposed
                    // to watch subtree we re-register the whole tree
                    if (mode == Recursive &&
                        event.kind in listOf(Created, Deleted) &&
                        event.file.isDirectory
                    ) {
                        shouldRegisterPath = true
                    }

                    channel.send(event)
                }

                if (!monitorKey.reset()) {
                    monitorKey.cancel()
                    close()
                    break
                } else if (isClosedForSend) {
                    break
                }
            }
        }
    }

    override fun close(cause: Throwable?): Boolean {
        Timber.v("Closing watch channel for path: $path, cause: $cause")
        registeredKeys.apply {
            forEach { it.cancel() }
            clear()
        }

        return channel.close(cause)
    }

    /**
     * Describes the mode this channels is running in
     */
    enum class Mode {
        /**
         * Watches only the given file
         */
        SingleFile,

        /**
         * Watches changes in the given directory, changes in subdirectories will be
         * ignored
         */
        SingleDirectory,

        /**
         * Watches changes in subdirectories
         */
        Recursive,
    }
}

/**
 * Wrapper around [WatchEvent] that comes with properly resolved absolute path
 */
data class KWatchEvent(
    /**
     * Abolute path of modified folder/file
     */
    val file: File,

    /**
     * Kind of file system event
     */
    val kind: Kind,

    /**
     * Optional extra data that should be associated with this event
     */
    val tag: Any?,
) {
    /**
     * File system event, wrapper around [WatchEvent.Kind]
     */
    enum class Kind(val kind: String) {
        /**
         * Triggered upon initialization of the channel
         */
        Initialized("initialized"),

        /**
         * Triggered when file or directory is created
         */
        Created("created"),

        /**
         * Triggered when file or directory is modified
         */
        Modified("modified"),

        /**
         * Triggered when file or directory is deleted
         */
        Deleted("deleted"),
    }
}
