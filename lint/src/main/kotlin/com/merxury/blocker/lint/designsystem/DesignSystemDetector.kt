/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.lint.designsystem

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable

/**
 * A detector that checks for incorrect usages of Compose Material APIs over equivalents in
 * the Blocker design system module.
 */
class DesignSystemDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(
        UCallExpression::class.java,
        UQualifiedReferenceExpression::class.java,
    )

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        val sourcePackage = context.uastFile?.packageName
        // The design system implements its wrappers by calling the underlying Material APIs.
        if (sourcePackage?.isInPackage(DESIGN_SYSTEM_PACKAGE) == true) return null

        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val name = node.methodName ?: return
                val preferredName = METHOD_NAMES[name] ?: return
                val declaringPackage = node.resolve()?.let { context.evaluator.getPackage(it)?.qualifiedName } ?: return
                if (!declaringPackage.isInPackage(MATERIAL_PACKAGE) && !declaringPackage.isInPackage(MATERIAL3_PACKAGE)) return
                reportIssue(context, node, name, preferredName)
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val receiver = node.receiver
                if (!receiver.isMaterialIcons()) return
                // Report once for a chain such as Icons.Filled.Home.
                if ((receiver as? UQualifiedReferenceExpression)?.receiver?.isMaterialIcons() == true) return
                reportIssue(context, node, "Icons", "BlockerIcons")
            }
        }
    }

    private fun UExpression.isMaterialIcons(): Boolean {
        val type = getExpressionType()?.canonicalText
            ?: ((this as? UResolvable)?.resolve() as? PsiClass)?.qualifiedName
            ?: return false
        return type == MATERIAL_ICONS || type.startsWith("$MATERIAL_ICONS.")
    }

    private fun String.isInPackage(packageName: String): Boolean = this == packageName || startsWith("$packageName.")

    companion object {
        private const val DESIGN_SYSTEM_PACKAGE = "com.merxury.blocker.core.designsystem"
        private const val MATERIAL_PACKAGE = "androidx.compose.material"
        private const val MATERIAL3_PACKAGE = "androidx.compose.material3"
        private const val MATERIAL_ICONS = "$MATERIAL_PACKAGE.icons.Icons"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DesignSystem",
            briefDescription = "Design system",
            explanation = "This check highlights calls in code that use Compose Material " +
                "composables instead of equivalents from the Blocker design system " +
                "module.",
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                DesignSystemDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        // Unfortunately :lint is a Java module and thus can't depend on the :core-designsystem
        // Android module, so we can't use composable function references (eg. ::Button.name)
        // instead of hardcoded names.
        val METHOD_NAMES = mapOf(
            "MaterialTheme" to "BlockerTheme",
            "Button" to "BlockerButton",
            "OutlinedButton" to "BlockerOutlinedButton",
            "TextButton" to "BlockerTextButton",
            "FilterChip" to "BlockerFilterChip",
            "ElevatedFilterChip" to "BlockerFilterChip",
            "DropdownMenu" to "BlockerDropdownMenu",
            "NavigationBar" to "BlockerNavigationSuiteScaffold",
            "NavigationBarItem" to "BlockerNavigationSuiteScaffold",
            "NavigationRail" to "BlockerNavigationSuiteScaffold",
            "NavigationRailItem" to "BlockerNavigationSuiteScaffold",
            "TabRow" to "BlockerTabRow",
            "Tab" to "BlockerTab",
            "IconToggleButton" to "BlockerIconToggleButton",
            "FilledIconToggleButton" to "BlockerIconToggleButton",
            "FilledTonalIconToggleButton" to "BlockerIconToggleButton",
            "OutlinedIconToggleButton" to "BlockerIconToggleButton",
            "CenterAlignedTopAppBar" to "BlockerTopAppBar",
            "SmallTopAppBar" to "BlockerTopAppBar",
            "MediumTopAppBar" to "BlockerTopAppBar",
            "LargeTopAppBar" to "BlockerTopAppBar",
            "Switch" to "BlockerSwitch",
        )

        private fun reportIssue(
            context: JavaContext,
            node: UElement,
            name: String,
            preferredName: String,
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using $name instead of $preferredName",
            )
        }
    }
}
