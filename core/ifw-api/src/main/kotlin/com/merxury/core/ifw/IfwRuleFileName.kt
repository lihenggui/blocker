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

package com.merxury.core.ifw

/**
 * Whether `<name>.xml` is a rule file Blocker may open in the Intent Firewall directory: the file
 * stays inside the directory, is not hidden like the temporary files of [replaceIfwFile], and is
 * not one of the files the wake feature manages. It says nothing about whose rules the file holds;
 * a user's own file, such as `my-rules.xml`, passes.
 *
 * Rejects an empty name and any name that contains `/`, `..` or NUL, or starts with `.`.
 */
fun isSafeRuleFileName(name: String): Boolean = name.isNotEmpty() &&
    !name.startsWith('.') &&
    '/' !in name &&
    ".." !in name &&
    '\u0000' !in name &&
    !isManagedWakeName(name)

/**
 * Whether `<name>.xml` is the rule file of the package [name]: a [isSafeRuleFileName] whose name
 * is an Android package name, segments of letters, digits and underscores that each start with a
 * letter, joined by dots. A single segment such as `android` is a package name; a name with a
 * hyphen, a space or a segment that starts with a digit is not, so a user's own file is never
 * taken for a package's.
 */
fun isPackageRuleFileName(name: String): Boolean = PACKAGE_NAME.matches(name) && isSafeRuleFileName(name)

private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)*""")
