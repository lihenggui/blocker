package com.merxury.ifw.util

import com.elvishew.xlog.XLog
import com.merxury.ifw.entity.Rules
import org.simpleframework.xml.core.Persister
import java.io.File

object RuleSerializer {
    private val serializer by lazy { Persister() }
    private val logger = XLog.tag("RuleSerializer").build()
    fun deserialize(file: File): Rules? {
        if (!file.exists()) {
            return null
        }
        return try {
            serializer.read(Rules::class.java, file)
        } catch (e: Exception) {
            logger.e("${file.absolutePath} is not a valid ifw rule, skipping", e)
            null
        }
    }
}