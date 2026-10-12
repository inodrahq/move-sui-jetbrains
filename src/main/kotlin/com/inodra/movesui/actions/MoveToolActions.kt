package com.inodra.movesui.actions

import com.inodra.movesui.MoveNotifications
import com.inodra.movesui.project.MovePackage
import com.inodra.movesui.project.MovePackageService
import com.inodra.movesui.run.MoveCommand
import com.inodra.movesui.run.MoveRunConfiguration
import com.inodra.movesui.run.MoveRunConfigurationType
import com.inodra.movesui.toolchain.MoveSettings
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil

abstract class MovePackageAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && currentPackage(e) != null
    }

    protected fun currentPackage(e: AnActionEvent): MovePackage? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        return file?.let { MovePackageService.getInstance(project).packageFor(it) } ?: MovePackageService.getInstance(project).packages().singleOrNull()
    }

    protected fun runConfiguration(project: Project, pkg: MovePackage, command: MoveCommand) {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("${command.display} ${pkg.name}", MoveRunConfigurationType.getInstance())
        (settings.configuration as MoveRunConfiguration).apply { this.command = command; packagePath = pkg.root.path }
        settings.isTemporary = true
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), settings)?.let { ExecutionManager.getInstance(project).restartRunProfile(it.build()) }
    }

    /** Runs a `sui move <subcommand>` in the background and shows the result as a notification. */
    protected fun runBackground(project: Project, pkg: MovePackage, title: String, vararg args: String, onSuccess: (String) -> Unit = {}) {
        val toolchain = MoveSettings.getInstance().toolchain ?: run { MoveNotifications.error(project, "Sui CLI not found", "Configure it in Settings | Languages & Frameworks | Move for Sui."); return }
        object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                val out = CapturingProcessHandler(toolchain.commandLine("move", *args, workDir = pkg.root.toNioPath())).runProcessWithProgressIndicator(indicator)
                VfsUtil.markDirtyAndRefresh(true, true, true, pkg.root)
                if (out.exitCode == 0) { MoveNotifications.info(project, title, out.stdout.lines().filter { it.isNotBlank() }.takeLast(5).joinToString("<br>")); onSuccess(out.stdout) }
                else MoveNotifications.error(project, "$title failed", (out.stderr.ifBlank { out.stdout }).takeLast(1500).replace("\n", "<br>"))
            }
        }.queue()
    }
}

class MoveBuildPackageAction : MovePackageAction() {
    override fun actionPerformed(e: AnActionEvent) { runConfiguration(e.project ?: return, currentPackage(e) ?: return, MoveCommand.BUILD) }
}

class MoveTestPackageAction : MovePackageAction() {
    override fun actionPerformed(e: AnActionEvent) { runConfiguration(e.project ?: return, currentPackage(e) ?: return, MoveCommand.TEST) }
}

class MoveLintPackageAction : MovePackageAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val pkg = currentPackage(e) ?: return
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("Lint ${pkg.name}", MoveRunConfigurationType.getInstance())
        (settings.configuration as MoveRunConfiguration).apply { command = MoveCommand.BUILD; packagePath = pkg.root.path; options.lint = true }
        settings.isTemporary = true
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), settings)?.let { ExecutionManager.getInstance(project).restartRunProfile(it.build()) }
    }
}

class MoveUpdateDepsAction : MovePackageAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val pkg = currentPackage(e) ?: return
        runBackground(project, pkg, "Updating Move dependencies", "update-deps") { MovePackageService.getInstance(project).invalidate() }
    }
}
