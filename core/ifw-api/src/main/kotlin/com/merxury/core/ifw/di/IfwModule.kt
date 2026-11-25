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

package com.merxury.core.ifw.di

import com.merxury.blocker.core.di.AppPackageName
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.IntentFirewallStore
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IfwModule {

    @Provides
    fun providesIfwXmlSerializer(): IfwXmlSerializer = IfwXmlSerializer()

    @Provides
    fun providesIfwXmlDeserializer(): IfwXmlDeserializer = IfwXmlDeserializer()

    @Singleton
    @Provides
    fun providesIfwStore(
        fileSystem: IfwFileSystem,
        serializer: IfwXmlSerializer,
        deserializer: IfwXmlDeserializer,
        @AppPackageName appPackageName: String,
    ): IfwStore = IntentFirewallStore(fileSystem, serializer, deserializer, appPackageName)
}
