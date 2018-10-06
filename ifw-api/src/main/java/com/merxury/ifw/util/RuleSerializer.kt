package com.merxury.ifw.util

import android.util.Log
import com.merxury.ifw.entity.Rules
import org.simpleframework.xml.core.Persister
import java.io.File

object RuleSerializer {
    private val serializer by lazy { Persister() }
    private val TAG = "RuleSerializer"
    fun deserialize(file: File): Rules? {
        if (!file.exists()) {
            return null
        }
        return try {
            serializer.read(Rules::class.java, file)
        } catch (e: Exception) {
            Log.e(TAG, "${file.absolutePath} is not a valid ifw rule, skipping", e)
            null
        }
    }
}