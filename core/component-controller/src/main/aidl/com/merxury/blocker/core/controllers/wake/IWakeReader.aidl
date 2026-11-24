// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake;
import android.os.ParcelFileDescriptor;
interface IWakeReader {
    ParcelFileDescriptor read(String operation, int profile, int boot, long argument) = 5;
    void destroy() = 16777114;
}
