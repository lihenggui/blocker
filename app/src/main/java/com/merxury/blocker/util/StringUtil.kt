package com.merxury.blocker.util

import java.io.PrintWriter
import java.io.StringWriter

object StringUtil {
    @JvmStatic
    fun getStackTrace(error: Throwable): String {
        val errors = StringWriter()
        error.printStackTrace(PrintWriter(errors))
        return errors.toString()
    }
}