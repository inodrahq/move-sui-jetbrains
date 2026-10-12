package com.inodra.movesui.ide.annotator

import com.inodra.movesui.cli.MoveCompilerDiagnostic
import com.inodra.movesui.cli.MoveCompilerDiagnostics
import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lsp.MoveLspIntegrationProvider
import com.inodra.movesui.project.MovePackage
import com.inodra.movesui.toolchain.MoveSettings
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiFile
import java.nio.file.Path

/**
 * Opt-in: runs the real compiler on save and shows its diagnostics. Off by default because the
 * analyzer already reports errors as you type; useful when the analyzer and compiler disagree.
 */
class MoveCompilerAnnotator : ExternalAnnotator<MoveCompilerAnnotator.Input, MoveCompilerAnnotator.Result>() {
    class Input(val pkg: MovePackage, val document: Document, val relPath: String)
    class Result(val diagnostics: List<MoveCompilerDiagnostic>)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? {
        if (file !is MoveFile || !MoveSettings.getInstance().state.compileOnSave) return null
        val vf = file.virtualFile ?: return null
        if (FileDocumentManager.getInstance().isDocumentUnsaved(editor.document)) return null
        val pkg = MovePackage.forFile(vf) ?: return null
        val rel = VfsUtilCore.getRelativePath(vf, pkg.root) ?: return null
        return Input(pkg, editor.document, rel)
    }

    override fun doAnnotate(input: Input): Result? {
        val toolchain = MoveSettings.getInstance().toolchain ?: return null
        val diags = MoveCompilerDiagnostics.build(toolchain, Path.of(input.pkg.root.path)) ?: return null
        return Result(diags.filter { it.file.removePrefix("./") == input.relPath })
    }

    override fun apply(file: PsiFile, result: Result?, holder: AnnotationHolder) {
        if (result == null) return
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile ?: return) ?: return
        val analyzerRunning = MoveLspIntegrationProvider.clients(file.project).any { it.state == LspServerState.Running }
        for (d in result.diagnostics) {
            if (d.line < 1 || d.line > document.lineCount) continue
            val lineStart = document.getLineStartOffset(d.line - 1)
            val lineEnd = document.getLineEndOffset(d.line - 1)
            val start = (lineStart + d.column - 1).coerceIn(lineStart, lineEnd)
            // Highlight the identifier/token at the column.
            var end = start
            val text = document.charsSequence
            while (end < lineEnd && (text[end].isLetterOrDigit() || text[end] == '_')) end++
            if (end == start) end = lineEnd.coerceAtLeast(start + 1)
            val severity = when {
                d.isError -> HighlightSeverity.ERROR
                analyzerRunning -> HighlightSeverity.WEAK_WARNING
                else -> HighlightSeverity.WARNING
            }
            holder.newAnnotation(severity, "[compiler] ${d.message}").range(TextRange(start, end)).create()
        }
    }
}
