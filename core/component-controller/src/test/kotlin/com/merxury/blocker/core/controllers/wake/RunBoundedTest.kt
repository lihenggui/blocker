// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A command that ignores the request to stop: only a kill ends it, or nothing does when
 * [unkillable]. With [keepsStdoutOpen] its stdout stays open until it ends or the stream is closed;
 * otherwise it closes stdout at once and exits unless [hang] was called.
 */
private class StubbornProcess(
    private val keepsStdoutOpen: Boolean,
    private val status: Int = 0,
    private val stdout: ByteArray = ByteArray(0),
    private val unkillable: Boolean = false,
) : Process() {
    private val ended = CountDownLatch(1)
    private val stdoutClosed = CountDownLatch(1)
    private var hangs = false
    var stopRequests = 0
    var kills = 0

    private val input = object : InputStream() {
        private var next = 0

        override fun read(): Int {
            if (next < stdout.size) return stdout[next++].toInt() and 0xff
            // A pipe read ignores interrupts; only the end of the command or a close ends it.
            while (keepsStdoutOpen && ended.count > 0 && stdoutClosed.count > 0) {
                Thread.onSpinWait()
            }
            return -1
        }

        override fun close() {
            stdoutClosed.countDown()
        }
    }

    init {
        if (!keepsStdoutOpen) ended.countDown()
    }

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getErrorStream(): InputStream = InputStream.nullInputStream()

    override fun getInputStream(): InputStream = input

    override fun waitFor(): Int {
        if (hangs || keepsStdoutOpen) killedOrEnded.await()
        return exitValue()
    }

    private val killedOrEnded = CountDownLatch(1)

    override fun exitValue(): Int = if (kills > 0 && !unkillable) 137 else status

    override fun destroy() {
        stopRequests++
    }

    override fun destroyForcibly(): Process {
        kills++
        if (!unkillable) {
            ended.countDown()
            killedOrEnded.countDown()
        }
        return this
    }

    fun hang() {
        hangs = true
    }
}

class RunBoundedTest {
    @Test
    fun givenCommandThatExits_whenRun_thenItsOutputArrives() {
        val output = ByteArrayOutputStream()

        runBounded(StubbornProcess(keepsStdoutOpen = false, stdout = "dump".toByteArray()), "SERVICES", null, output, 200, 100, Process::destroyForcibly)

        assertThat(output.toString()).isEqualTo("dump")
    }

    @Test
    fun givenCommandThatFails_whenRun_thenItsStatusIsTheMessage() {
        val failure = assertThrows(IOException::class.java) {
            runBounded(StubbornProcess(keepsStdoutOpen = false, status = 1), "EVENTS", null, ByteArrayOutputStream(), 200, 100, Process::destroyForcibly)
        }

        assertThat(failure).hasMessageThat().isEqualTo("EVENTS exited with status 1")
    }

    @Test
    fun givenCommandThatKeepsStdoutOpenAndIgnoresTheStop_whenRun_thenKilledAfterTheGrace() {
        val process = StubbornProcess(keepsStdoutOpen = true)
        val started = System.nanoTime()

        val failure = assertThrows(IOException::class.java) { runBounded(process, "JOBS", null, ByteArrayOutputStream(), 200, 100, Process::destroyForcibly) }

        assertThat(failure).hasMessageThat().isEqualTo("JOBS did not finish within 0.2 s")
        assertThat(process.stopRequests).isAtLeast(1)
        assertThat(process.kills).isAtLeast(1)
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000)
    }

    @Test
    fun givenCommandThatEvenTheKillCannotEnd_whenRun_thenTheCallIsAbandonedWithItsReason() {
        val process = StubbornProcess(keepsStdoutOpen = true, unkillable = true)
        val started = System.nanoTime()

        val failure = assertThrows(IOException::class.java) { runBounded(process, "BATTERY", null, ByteArrayOutputStream(), 200, 100, Process::destroyForcibly) }

        assertThat(failure).hasMessageThat().isEqualTo("BATTERY did not exit after it was killed")
        assertThat(Thread.currentThread().isInterrupted).isFalse()
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000)
    }

    @Test
    fun givenCommandThatClosesStdoutButNeverExits_whenRun_thenTheWaitIsBoundedAndItIsKilled() {
        val process = StubbornProcess(keepsStdoutOpen = false).apply { hang() }
        val started = System.nanoTime()

        val failure = assertThrows(IOException::class.java) { runBounded(process, "ALARMS", null, ByteArrayOutputStream(), 200, 100, Process::destroyForcibly) }

        assertThat(failure).hasMessageThat().isEqualTo("ALARMS did not finish within 0.2 s")
        assertThat(process.kills).isAtLeast(1)
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000)
    }
}
