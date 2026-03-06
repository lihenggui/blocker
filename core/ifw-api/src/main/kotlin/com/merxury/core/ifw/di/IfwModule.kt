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

import android.content.pm.PackageManager
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import com.merxury.blocker.core.utils.ShellRootAvailabilityChecker
import com.merxury.core.ifw.ComponentTypeResolver
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.IntentFirewall
import com.merxury.core.ifw.PmComponentTypeResolver
import com.merxury.core.ifw.SuIfwFileSystem
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import nl.adaptivity.xmlutil.serialization.XML
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IfwModule {

    @Provides
    fun providesXmlParser(): XML = XML {
        indentString = "   "
    }

    @Singleton
    @Provides
    fun providesRootAvailabilityChecker(
        @Dispatcher(IO) dispatcher: CoroutineDispatcher,
    ): RootAvailabilityChecker = ShellRootAvailabilityChecker(dispatcher)

    @Singleton
    @Provides
    fun providesComponentTypeResolver(
        pm: PackageManager,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
        @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    ): ComponentTypeResolver = PmComponentTypeResolver(pm, ioDispatcher, cpuDispatcher)

    @Singleton
    @Provides
    fun providesIfwFileSystem(
        @Dispatcher(IO) dispatcher: CoroutineDispatcher,
    ): IfwFileSystem = SuIfwFileSystem(dispatcher)

    @Singleton
    @Provides
    fun providesIntentFirewall(
        xmlParser: XML,
        rootChecker: RootAvailabilityChecker,
        componentTypeResolver: ComponentTypeResolver,
        fileSystem: IfwFileSystem,
    ): IIntentFirewall = IntentFirewall(xmlParser, rootChecker, componentTypeResolver, fileSystem)
}
