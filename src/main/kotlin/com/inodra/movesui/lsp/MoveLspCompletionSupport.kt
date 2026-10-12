package com.inodra.movesui.lsp

import com.inodra.movesui.ide.completion.MoveScopeNames
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Ranks the analyzer's completion items: what is already in scope beats what would need an import,
 * so a parameter named `ctx` wins over `test_scenario::ctx()`.
 */
class MoveLspCompletionSupport : LspCompletionSupport() {
    override fun createLookupElement(parameters: CompletionParameters, item: CompletionItem): LookupElement? {
        val inScope = MoveScopeNames.namesInScope(parameters.position)
        // Locals are contributed natively; drop the server's copy to avoid duplicates.
        if (item.label in MoveScopeNames.localNamesInScope(parameters.position) && item.additionalTextEdits.isNullOrEmpty()) return null
        val element = super.createLookupElement(parameters, item) ?: return null
        return PrioritizedLookupElement.withPriority(element, MoveCompletionRanking.priority(item, item.label in inScope))
    }
}

object MoveCompletionRanking {
    private val LOCAL_KINDS = setOf(CompletionItemKind.Variable, CompletionItemKind.Field, CompletionItemKind.Property, CompletionItemKind.TypeParameter, CompletionItemKind.EnumMember)

    fun priority(item: CompletionItem, isInScope: Boolean): Double {
        val needsImport = !item.additionalTextEdits.isNullOrEmpty()
        return when {
            isInScope && !needsImport -> 100.0
            item.kind in LOCAL_KINDS && !needsImport -> 50.0
            !needsImport -> 10.0
            else -> -10.0
        }
    }
}
