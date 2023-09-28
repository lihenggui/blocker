/*
 * Copyright 2023 Blocker
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
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.IntentFirewall
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import nl.adaptivity.xmlutil.serialization.XML

@Module
@InstallIn(SingletonComponent::class)
object IfwModule {

    @Provides
    fun providesXmlParser(): XML {
        return XML {
            indentString = "   "
        }
    }

    @Provides
    fun providesIntentFirewall(
        pm: PackageManager,
        xmlParser: XML,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
        @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    ): IIntentFirewall {
        return IntentFirewall(pm, xmlParser, ioDispatcher, cpuDispatcher)
    }
}
