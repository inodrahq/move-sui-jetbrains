package com.inodra.movesui.ide.formatting

import com.inodra.movesui.MoveNotifications
import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.toolchain.SuiToolchainLocator
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.notification.NotificationAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/** Reformat Code → `prettier` with `@mysten/prettier-plugin-move` (the same formatter `sui move format` uses). */
class MoveFormattingService : AsyncDocumentFormattingService() {
    override fun getFeatures(): Set<FormattingService.Feature> = emptySet()
    override fun canFormat(file: PsiFile) = file is MoveFile
    override fun getNotificationGroupId() = MoveNotifications.GROUP
    override fun getName() = "prettier-move"

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val file = request.context.virtualFile ?: return null
        val prettier = SuiToolchainLocator.findExecutable("prettier")
        if (prettier == null) {
            MoveNotifications.warn(
                request.context.project, "Move formatter not installed",
                "Install it with <code>npm i -g prettier @mysten/prettier-plugin-move</code>.",
            )
            return null
        }
        val cmd = GeneralCommandLine(prettier.toString(), "--plugin", "@mysten/prettier-plugin-move", "--stdin-filepath", file.path)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withWorkDirectory(packageDirOf(file)?.toString())
            .withCharset(StandardCharsets.UTF_8)
        return object : FormattingTask {
            private var handler: OSProcessHandler? = null
            override fun run() {
                try {
                    val h = OSProcessHandler(cmd)
                    handler = h
                    h.addProcessListener(object : CapturingProcessAdapter() {
                        override fun processTerminated(event: ProcessEvent) {
                            val out = output
                            if (event.exitCode == 0) request.onTextReady(out.stdout) else request.onError("prettier-move failed", out.stderr.take(2000).ifBlank { out.stdout })
                        }
                    })
                    h.startNotify()
                    h.processInput?.use { it.write(request.documentText.toByteArray(StandardCharsets.UTF_8)) }
                } catch (e: Exception) {
                    request.onError("Cannot run prettier", e.message ?: e.toString())
                }
            }
            override fun cancel(): Boolean { handler?.destroyProcess(); return true }
            override fun isRunUnderProgress() = true
        }
    }

    private fun packageDirOf(file: com.intellij.openapi.vfs.VirtualFile): Path? =
        com.inodra.movesui.project.MovePackage.forFile(file)?.root?.toNioPath() ?: file.parent?.toNioPath()
}
