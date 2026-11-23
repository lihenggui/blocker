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
 * Whether [name] can be the package name of a package's rule file, `<name>.xml`, in the Intent
 * Firewall directory: the file stays inside the directory, is not hidden like the temporary files
 * of [replaceIfwFile], and is not one of the files the wake feature manages.
 *
 * Rejects an empty name and any name that contains `/`, `..` or NUL, or starts with `.`. Every
 * real package name passes.
 */
fun isPackageRuleFileName(name: String): Boolean = name.isNotEmpty() &&
    !name.startsWith('.') &&
    '/' !in name &&
    ".." !in name &&
    '\u0000' !in name &&
    !isManagedWakeName(name)
