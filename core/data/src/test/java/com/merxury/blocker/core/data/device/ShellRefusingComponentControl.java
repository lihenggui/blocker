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

package com.merxury.blocker.core.data.device;

import com.merxury.blocker.core.control.ComponentControl;
import com.merxury.blocker.core.control.PackageLocks;
import com.merxury.blocker.core.control.PrivilegedComponentControl;
import com.merxury.blocker.core.model.privileged.CallFailure;
import com.merxury.blocker.core.model.privileged.CallResult;
import com.merxury.blocker.core.model.privileged.ChannelState;
import com.merxury.blocker.core.privileged.PrivilegedCall;
import com.merxury.blocker.core.privileged.PrivilegedChannel;
import com.merxury.blocker.core.privileged.PrivilegedExecutor;
import com.merxury.blocker.core.system.SystemPackageReader;
import com.merxury.core.ifw.IfwStore;
import kotlin.coroutines.Continuation;

/** Builds the real controller without exposing its internal executor to other modules. */
final class ShellRefusingComponentControl {
    private ShellRefusingComponentControl() {}

    static ComponentControl create(
            PrivilegedChannel channel, SystemPackageReader reader, IfwStore store, PackageLocks locks) {
        PrivilegedExecutor executor = new PrivilegedExecutor() {
            @Override
            public <R> Object execute(PrivilegedCall<R> call, Continuation<? super CallResult<? extends R>> continuation) {
                throw new AssertionError("Expected the controller to use the ready channel");
            }

            @Override
            public <R> Object executeIfReady(PrivilegedCall<R> call, Continuation<? super CallResult<? extends R>> continuation) {
                return new CallResult.Failure(new CallFailure.NotPermitted("Shell cannot change components", null));
            }

            @Override
            public Object checkedState(Continuation<? super ChannelState> continuation) {
                return channel.getState().getValue();
            }
        };
        return new PrivilegedComponentControl(channel, executor, reader, store, locks);
    }
}
