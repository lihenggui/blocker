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

package com.merxury.blocker.core.model.history

/** The action that caused an operation. */
enum class OperationSource {
    /** A change made from the app detail screen. */
    APP_DETAIL,

    /** A change made from search results. */
    SEARCH,

    /** A change made from the general rules screen. */
    GENERAL_RULE,

    /** A change made from a rule detail screen. */
    RULE_DETAIL,

    /** A change made from the debloater. */
    DEBLOATER,

    /** A request from another app through the content provider. */
    EXTERNAL_PROVIDER,

    /** An import of Blocker rules. */
    IMPORT_BLOCKER_RULE,

    /** An import of My Android Tools rules. */
    IMPORT_MAT_RULE,

    /** A reset of PackageManager component settings. */
    RESET_PM,

    /** A reversal of one or more recorded operations. */
    UNDO,
}
