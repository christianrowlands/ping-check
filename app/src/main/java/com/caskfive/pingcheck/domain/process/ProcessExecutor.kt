package com.caskfive.pingcheck.domain.process

import java.io.BufferedReader

interface ProcessExecutor {
    fun execute(command: List<String>): ManagedProcess
}

interface ManagedProcess {
    val stdout: BufferedReader
    val stderr: BufferedReader
    val exitCode: Int
    fun isAlive(): Boolean
    fun destroy()
    fun waitFor(): Int
}

class SystemProcessExecutor : ProcessExecutor {
    override fun execute(command: List<String>): ManagedProcess {
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()
        return SystemManagedProcess(process)
    }
}

private class SystemManagedProcess(private val process: Process) : ManagedProcess {
    override val stdout: BufferedReader = process.inputStream.bufferedReader()
    override val stderr: BufferedReader = process.errorStream.bufferedReader()
    override val exitCode: Int get() = process.exitValue()
    override fun isAlive(): Boolean = try {
        process.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }
    override fun destroy() {
        process.destroy()
        stdout.close()
        stderr.close()
    }
    override fun waitFor(): Int = process.waitFor()
}
