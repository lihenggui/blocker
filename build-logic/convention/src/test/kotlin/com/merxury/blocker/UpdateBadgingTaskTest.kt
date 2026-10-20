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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateBadgingTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var runner: GradleRunner
    private lateinit var mappingInput: File
    private lateinit var generatedBadging: File
    private lateinit var goldenBadging: File

    @Before
    fun setUp() {
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
        mappingInput = projectDirectory.resolve("compose-mapping-errors.txt").apply {
            writeText("Unrelated mapping input")
        }
        generatedBadging = projectDirectory.resolve("generated-badging.txt").apply {
            writeText("minSdkVersion:'24'")
        }
        goldenBadging = projectDirectory.resolve("marketRelease-badging.txt").apply {
            writeText("minSdkVersion:'23'")
        }
        runner = GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments(
                "reportComposeMappingErrors",
                "updateBadging",
                "--configuration-cache",
                "--configuration-cache-problems=fail",
                "--offline",
                "--stacktrace",
            )
    }

    @Test
    fun givenUnrelatedFileConsumer_whenUpdatingBadging_thenOnlyBaselineChanges() {
        // Given
        val expectedBadging = generatedBadging.readText()

        // When
        val result = runner.build()

        // Then
        assertEquals(TaskOutcome.SUCCESS, result.task(":updateBadging")?.outcome)
        assertEquals(expectedBadging, goldenBadging.readText())
        assertEquals("Unrelated mapping input", mappingInput.readText())
    }

    @Test
    fun givenCompletedUpdate_whenRunningAgain_thenReusesConfigurationCacheAndIsUpToDate() {
        // Given
        runner.build()

        // When
        val cached = runner.build()

        // Then
        assertTrue(cached.output.contains("Reusing configuration cache."))
        assertEquals(TaskOutcome.UP_TO_DATE, cached.task(":updateBadging")?.outcome)
    }

    @Test
    fun givenUpdatedBaseline_whenGeneratedBadgingChanges_thenRegeneratesBaseline() {
        // Given
        runner.build()
        generatedBadging.writeText("minSdkVersion:'24'\ntargetSdkVersion:'36'")

        // When
        val changed = runner.build()

        // Then
        assertEquals(TaskOutcome.SUCCESS, changed.task(":updateBadging")?.outcome)
        assertEquals(generatedBadging.readText(), goldenBadging.readText())
    }
}
