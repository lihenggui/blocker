// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.shizuku;

import com.merxury.blocker.core.privileged.CallReply;

// Blocker's Shizuku user service: runs privileged calls that must run in a privileged process.
interface IPrivilegedService {
    // Runs the call that marshallCall wrote and returns its outcome from encodeOutcome.
    CallReply run(in byte[] call) = 1;

    // Shizuku sends this transaction to stop the service.
    void destroy() = 16777114;
}
