// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake;
import android.os.ParcelFileDescriptor;
interface IWakeReader {
    ParcelFileDescriptor read(String operation, int profile, int boot) = 1;
    void writeManagedFirewall(String xml, boolean probe) = 2;
    String componentJournal(String replacement) = 3;
    void removeManagedPackage(String packageName) = 4;
    void destroy() = 16777114;
}
