package com.inodra.movesui.debug

import com.inodra.movesui.MoveNotifications
import com.inodra.movesui.project.MoveManifest
import com.inodra.movesui.run.MoveCommand
import com.inodra.movesui.run.MoveRunConfiguration
import com.inodra.movesui.toolchain.MoveSettings
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.io.File

/**
 * Debug on a single-test run configuration: runs `sui move test --trace --disassemble <module::fun>`,
 * then replays the produced trace in the debugger.
 */
class MoveDebugRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId() = "MoveSuiTraceDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is MoveRunConfiguration && profile.command == MoveCommand.TEST && profile.testFilter.contains("::")

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        val config = environment.runProfile as MoveRunConfiguration
        val promise = AsyncPromise<RunContentDescriptor?>()
        val project = environment.project
        val packageDir = config.workingDirectory?.toFile() ?: run { promise.setError("No package directory"); return promise }
        val toolchain = MoveSettings.getInstance().toolchain ?: run { promise.setError("Sui CLI not found"); return promise }
        val filter = config.testFilter.trimEnd(':')
        val pkgName = MoveManifest.parse(File(packageDir, "Move.toml").readText()).name ?: packageDir.name
        val traceInfo = "$pkgName::$filter"

        object : Task.Backgroundable(project, "Tracing Move test $filter", true) {
            override fun run(indicator: ProgressIndicator) {
                val cmd = toolchain.commandLine("move", "test", "--trace", "--disassemble", filter, workDir = packageDir.toPath())
                val out = CapturingProcessHandler(cmd).runProcessWithProgressIndicator(indicator)
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(packageDir)?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
                val traceFile = File(packageDir, "traces/${traceInfo.replace("::", "__")}.json.zst")
                if (!traceFile.isFile) {
                    val msg = "No trace was produced at ${traceFile.path}.<br>" + (out.stderr.ifBlank { out.stdout }).takeLast(800).replace("\n", "<br>")
                    MoveNotifications.error(project, "Trace generation failed", msg)
                    promise.setError("trace not produced")
                    return
                }
                val sourceFile = findSourceFile(packageDir, filter.substringBefore("::")) ?: File(packageDir, "sources")
                ApplicationManager.getApplication().invokeLater {
                    try {
                        val session: XDebugSession = XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
                            override fun start(session: XDebugSession): XDebugProcess = MoveDebugProcess(session, sourceFile, traceInfo, packageDir)
                        })
                        promise.setResult(session.runContentDescriptor)
                    } catch (e: Exception) {
                        promise.setError(e)
                    }
                }
            }
        }.queue()
        return promise
    }

    /** The `.move` file declaring [moduleName] under sources/ or tests/ (the adapter wants a source file to anchor the package). */
    private fun findSourceFile(packageDir: File, moduleName: String): File? {
        val regex = Regex("""module\s+\w+::$moduleName\b""")
        return listOf("tests", "sources").map { File(packageDir, it) }.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "move" }.toList() }
            .firstOrNull { f -> regex.containsMatchIn(f.readText()) }
    }
}
