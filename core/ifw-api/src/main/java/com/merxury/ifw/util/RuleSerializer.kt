package com.merxury.ifw.util

import com.elvishew.xlog.XLog
import com.merxury.ifw.entity.Rules
import java.io.InputStream
import org.simpleframework.xml.core.Persister

object RuleSerializer {
    private val serializer by lazy { Persister() }
    private val logger = XLog.tag("RuleSerializer").build()

    fun deserialize(inStream: InputStream): Rules? {
        return try {
            serializer.read(Rules::class.java, inStream)
        } catch (e: Exception) {
            logger.e("Not a valid ifw rule, skipping", e)
            null
        }
    }
}
