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

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer

internal const val PKG = "com.example"

internal fun declaration(simpleName: String, type: ComponentType, packageName: String = PKG) = ComponentDeclaration(
    id = ComponentId(packageName, "$packageName.$simpleName"),
    type = type,
    exported = true,
    enabledInManifest = true,
)

internal val MAIN = declaration("Main", ComponentType.ACTIVITY)
internal val SETTINGS = declaration("Settings", ComponentType.ACTIVITY)
internal val SYNC = declaration("Sync", ComponentType.SERVICE)
internal val BOOT = declaration("Boot", ComponentType.RECEIVER)
internal val FILES = declaration("Files", ComponentType.PROVIDER)

/** How Blocker wrote rules before this store: one rule per type with only component-filters. */
internal val LEGACY_XML = """
    <rules>
      <activity block="true" log="false">
        <component-filter name="$PKG/$PKG.Main" />
      </activity>
      <service block="true" log="false">
        <component-filter name="$PKG/$PKG.Sync" />
      </service>
      <broadcast block="true" log="false">
        <component-filter name="$PKG/$PKG.Boot" />
      </broadcast>
    </rules>
""".trimIndent()

/** A hand-written rule that blocks Main only when a sender outside the system starts it. */
internal val CONDITIONAL_MAIN_XML = """
    <rules>
      <activity block="true" log="true">
        <component-filter name="$PKG/$PKG.Main" />
        <not>
          <sender type="system" />
        </not>
      </activity>
    </rules>
""".trimIndent()

internal fun storeOver(fileSystem: FakeIfwFileSystem) = IntentFirewallStore(fileSystem, IfwXmlSerializer(), IfwXmlDeserializer())

internal fun FakeIfwFileSystem.rulesOf(packageName: String = PKG): IfwRules? = fileContent(packageName)?.let { IfwXmlDeserializer().deserialize(it) }
