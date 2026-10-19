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

package com.merxury.blocker

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateBadgingTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun updatesOnlyBaselineAlongsideUnrelatedProjectFileConsumer() {
        val projectDirectory = temporaryFolder.root
        val taskClasspath = File(UpdateBadgingTask::class.java.protectionDomain.codeSource.location.toURI())
            .absolutePath.replace("\\", "\\\\").replace("'", "\\'")
        projectDirectory.resolve("settings.gradle").writeText("rootProject.name = 'badging-test'")
        projectDirectory.resolve("build.gradle").writeText(
            """
            import com.merxury.blocker.UpdateBadgingTask

            buildscript {
                dependencies {
                    classpath files('$taskClasspath')
                }
            }

            tasks.register('reportComposeMappingErrors') {
                inputs.file(layout.projectDirectory.file('compose-mapping-errors.txt'))
                doLast { logger.lifecycle('Checked mapping input') }
            }
            tasks.register('updateBadging', UpdateBadgingTask) {
                generatedBadging.set(layout.projectDirectory.file('generated-badging.txt'))
                goldenBadging.set(layout.projectDirectory.file('marketRelease-badging.txt'))
            }
            """.trimIndent(),
        )
        val mappingInput = projectDirectory.resolve("compose-mapping-errors.txt").apply {
            writeText("Unrelated mapping input")
        }
        val generatedBadging = projectDirectory.resolve("generated-badging.txt").apply {
            writeText("minSdkVersion:'24'")
        }
        val goldenBadging = projectDirectory.resolve("marketRelease-badging.txt").apply {
            writeText("minSdkVersion:'23'")
        }
        val runner = GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments(
                "reportComposeMappingErrors",
                "updateBadging",
                "--configuration-cache",
                "--configuration-cache-problems=fail",
                "--offline",
                "--stacktrace",
            )

        val first = runner.build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":updateBadging")?.outcome)
        assertEquals(generatedBadging.readText(), goldenBadging.readText())
        assertEquals("Unrelated mapping input", mappingInput.readText())

        val cached = runner.build()
        assertTrue(cached.output.contains("Reusing configuration cache."))
        assertEquals(TaskOutcome.UP_TO_DATE, cached.task(":updateBadging")?.outcome)

        generatedBadging.writeText("minSdkVersion:'24'\ntargetSdkVersion:'36'")
        val changed = runner.build()
        assertEquals(TaskOutcome.SUCCESS, changed.task(":updateBadging")?.outcome)
        assertEquals(generatedBadging.readText(), goldenBadging.readText())
    }
}
