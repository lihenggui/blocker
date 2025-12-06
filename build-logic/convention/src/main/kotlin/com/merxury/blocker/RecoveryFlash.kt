/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * This enables the APK to be flashed in recovery mode to remove IFW rules
 * that may cause boot issues. The mechanism works by:
 *
 * 1. Packaging shell scripts into META-INF/com/google/android/ directory
 * 2. When flashed in recovery, the update-binary script is executed
 * 3. The updater-script then removes all files in /data/system/ifw/
 */
internal fun Project.configureRecoveryFlash(
    androidComponentsExtension: ApplicationAndroidComponentsExtension,
) {
    androidComponentsExtension.onVariants { variant ->
        val variantName = variant.name
        val variantCapped = variantName.replaceFirstChar { it.uppercase() }
        val scriptsDir = rootDir.resolve("scripts")
        val syncRecoveryScripts = tasks.register<SyncRecoveryScriptsTask>(
            "sync${variantCapped}RecoveryScripts",
        ) {
            description = "Syncs recovery flash scripts for $variantName variant"
            group = "blocker"

            scriptsDirectory.set(scriptsDir)
            outputDirectory.set(
                layout.buildDirectory.dir("generated/recoveryScripts/$variantName"),
            )
        }
        variant.sources.resources?.addGeneratedSourceDirectory(
            syncRecoveryScripts,
            SyncRecoveryScriptsTask::outputDirectory,
        )
    }
}

/**
 * Task that copies recovery flash scripts to the output directory with proper structure.
 * The scripts are placed in META-INF/com/google/android/ to be recognized by recovery.
 */
@DisableCachingByDefault(because = "Simple file copy task")
internal abstract class SyncRecoveryScriptsTask : DefaultTask() {

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val scriptsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun syncScripts() {
        val outputDir = outputDirectory.get().asFile
        val scriptsDir = scriptsDirectory.get().asFile

        val metaInfDir = File(outputDir, "META-INF/com/google/android")
        metaInfDir.mkdirs()

        val updateBinarySrc = File(scriptsDir, "update_binary.sh")
        val updateBinaryDst = File(metaInfDir, "update-binary")
        if (updateBinarySrc.exists()) {
            updateBinarySrc.copyTo(updateBinaryDst, overwrite = true)
        }
        val flashScriptSrc = File(scriptsDir, "flash_script.sh")
        val flashScriptDst = File(metaInfDir, "updater-script")
        if (flashScriptSrc.exists()) {
            flashScriptSrc.copyTo(flashScriptDst, overwrite = true)
        }
    }
}
