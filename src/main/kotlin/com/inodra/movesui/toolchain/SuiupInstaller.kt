package com.inodra.movesui.toolchain

import com.inodra.movesui.MoveNotifications
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/** Installs or updates `sui` through `suiup`. */
object SuiupInstaller {
    fun installSui(project: Project?, component: String = "sui@testnet", onDone: () -> Unit = {}) {
        val suiup = SuiToolchainLocator.findSuiup()
        if (suiup == null) {
            MoveNotifications.warn(project, "suiup not found", "Install suiup first: https://github.com/MystenLabs/suiup")
            return
        }
        object : Task.Backgroundable(project, "Installing $component via suiup", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val cmd = GeneralCommandLine(suiup.toString(), "install", component, "-y", "--disable-update-warnings")
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                val output = CapturingProcessHandler(cmd).runProcessWithProgressIndicator(indicator)
                SuiToolchain.invalidateCaches()
                if (output.exitCode == 0) {
                    MoveNotifications.info(project, "Sui installed", output.stdout.lines().lastOrNull { it.isNotBlank() } ?: "Done")
                } else {
                    MoveNotifications.error(project, "suiup failed", (output.stderr.ifBlank { output.stdout }).takeLast(800))
                }
                onDone()
            }
        }.queue()
    }
}
