package com.inodra.movesui.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.filters.RegexpFilter
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleView

class MoveCommandLineState(private val config: MoveRunConfiguration, env: ExecutionEnvironment) : CommandLineState(env) {
    private val filters: List<com.intellij.execution.filters.Filter>

    init {
        // `./sources/x.move:12:5` → clickable, relative to the package directory.
        val dir = config.workingDirectory?.toString()?.replace("\\", "/") ?: ""
        filters = listOf(
            RegexpFilter(env.project, "$dir/${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}:${RegexpFilter.COLUMN_MACROS}"),
            MoveRelativePathFilter(env.project, dir),
        )
        filters.forEach { addConsoleFilters(it) }
    }

    override fun startProcess(): ProcessHandler {
        val handler = ColoredProcessHandler(config.buildCommandLine())
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        if (config.command != MoveCommand.TEST) return super.execute(executor, runner)
        val handler = startProcess()
        val properties = MoveTestConsoleProperties(config, executor)
        val console: ConsoleView = SMTestRunnerConnectionUtil.createAndAttachConsole("Sui Move", handler, properties)
        filters.forEach(console::addMessageFilter)
        return DefaultExecutionResult(console, handler, *createActions(console, handler, executor))
    }
}
