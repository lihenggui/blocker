package com.merxury.blocker.data

import android.util.SparseArray

object AndroidCodeName {
    private val array: SparseArray<String> = SparseArray(64)

    init {
        array.put(1, "Android 1.0")
        array.put(2, "Android 1.1")
        array.put(3, "Android 1.5")
        array.put(4, "Android 1.6")
        array.put(5, "Android 2.0")
        array.put(6, "Android 2.0.1")
        array.put(7, "Android 2.1")
        array.put(8, "Android 2.2")
        array.put(9, "Android 2.3")
        array.put(10, "Android 2.3.3")
        array.put(11, "Android 3.0")
        array.put(12, "Android 3.1")
        array.put(13, "Android 3.2")
        array.put(14, "Android 4.0.1")
        array.put(15, "Android 4.0.3")
        array.put(16, "Android 4.1")
        array.put(17, "Android 4.2")
        array.put(18, "Android 4.3")
        array.put(19, "Android 4.4")
        array.put(21, "Android 5.0")
        array.put(22, "Android 5.1")
        array.put(23, "Android 6.0")
        array.put(24, "Android 7.0")
        array.put(25, "Android 7.1")
        array.put(26, "Android 8.0")
        array.put(27, "Android 8.1")
        array.put(28, "Android P")
        array.put(29, "Android 10")
        array.put(30, "Android 11")
        array.put(31, "Android 12")
        array.put(32, "Android 12L")
        array.put(33, "Android 13")
        // Reference : https://source.android.com/setup/start/build-numbers
    }

    fun getCodeName(code: Int): String {
        return array.get(code) ?: "Android API $code"
    }
}
