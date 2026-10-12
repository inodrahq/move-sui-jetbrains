package com.inodra.movesui.lsp

import com.inodra.movesui.toolchain.MoveSettings
import com.inodra.movesui.toolchain.SuiToolchain
import com.inodra.movesui.toolchain.SuiToolchainLocator
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCompletionCustomizer
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolCustomizer
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolDisabled
import com.intellij.platform.lsp.api.customization.LspFormattingCustomizer
import com.intellij.platform.lsp.api.customization.LspFormattingDisabled
import com.intellij.platform.lsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.lsp.api.customization.LspSemanticTokensDisabled
import java.nio.file.Path

class MoveLspClientDescriptor private constructor(
    project: Project,
    private val commandLine: GeneralCommandLine,
) : ProjectWideLspClientDescriptor(project, "Sui Move Analyzer") {

    override fun isSupportedFile(file: VirtualFile) = file.extension == "move"

    override fun getLanguageId(file: VirtualFile) = "move"

    override fun createCommandLine(): GeneralCommandLine = commandLine

    override fun createInitializationOptions(): Any = MoveSettings.getInstance().analyzerInitializationOptions()

    override val lspCustomization: LspCustomization = object : LspCustomization() {
        // Native PSI provides structure view, breadcrumbs and (via prettier-move) formatting.
        override val documentSymbolCustomizer: LspDocumentSymbolCustomizer get() = LspDocumentSymbolDisabled
        override val formattingCustomizer: LspFormattingCustomizer get() = LspFormattingDisabled
        override val semanticTokensCustomizer: LspSemanticTokensCustomizer get() = LspSemanticTokensDisabled
        override val completionCustomizer: LspCompletionCustomizer get() = MoveLspCompletionSupport()
    }

    companion object {
        /** `null` when no analyzer can be started (no `sui`, no standalone `move-analyzer`). */
        fun create(project: Project): MoveLspClientDescriptor? {
            val settings = MoveSettings.getInstance()
            val workDir = project.basePath?.let(Path::of)
            val explicitAnalyzer = settings.analyzerExecutable
            val commandLine = when {
                explicitAnalyzer != null -> GeneralCommandLine(explicitAnalyzer.toString())
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                    .also { if (workDir != null) it.withWorkingDirectory(workDir) }
                else -> settings.toolchain?.analyzerCommandLine(workDir)
                    ?: SuiToolchainLocator.findMoveAnalyzer()?.let { GeneralCommandLine(it.toString()).withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE) }
                    ?: return null
            }
            return MoveLspClientDescriptor(project, commandLine)
        }
    }
}
