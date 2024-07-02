/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.vpn.model

import com.merxury.blocker.core.vpn.extension.toUnsignedShort
import java.nio.ByteBuffer

data class UdpHeader(
    var sourcePort: Int = 0,
    var destinationPort: Int = 0,
    var length: Int = 0,
    var checksum: Int = 0,
) {
    constructor(buffer: ByteBuffer) : this(
        sourcePort = buffer.short.toUnsignedShort(),
        destinationPort = buffer.short.toUnsignedShort(),
        length = buffer.short.toUnsignedShort(),
        checksum = buffer.short.toUnsignedShort(),
    )

    fun fillHeader(buffer: ByteBuffer) {
        buffer.putShort(sourcePort.toShort())
        buffer.putShort(destinationPort.toShort())
        buffer.putShort(length.toShort())
        buffer.putShort(checksum.toShort())
    }
}
