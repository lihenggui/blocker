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
package com.merxury.blocker.core.model.data

/**
 * Where a recorded component state change was initiated from.
 */
enum class OperationSource {
    APP_DETAIL,
    SEARCH,
    GENERAL_RULE,
    RULE_DETAIL,
    DEBLOATER,
    EXTERNAL_PROVIDER,
    IMPORT_BLOCKER_RULE,
    IMPORT_IFW_RULE,
    IMPORT_MAT_RULE,
    RESET_IFW,
    UNDO,
}
