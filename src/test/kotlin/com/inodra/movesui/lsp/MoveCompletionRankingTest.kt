package com.inodra.movesui.lsp

import junit.framework.TestCase
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.TextEdit

class MoveCompletionRankingTest : TestCase() {
    fun `test in-scope names beat auto-imported functions`() {
        val local = CompletionItem("ctx").apply { kind = CompletionItemKind.Variable }
        val imported = CompletionItem("ctx").apply { kind = CompletionItemKind.Function; additionalTextEdits = listOf(TextEdit()) }
        val plainFn = CompletionItem("ctx").apply { kind = CompletionItemKind.Function }
        assertTrue(MoveCompletionRanking.priority(local, true) > MoveCompletionRanking.priority(plainFn, false))
        assertTrue(MoveCompletionRanking.priority(plainFn, false) > MoveCompletionRanking.priority(imported, false))
        assertTrue(MoveCompletionRanking.priority(local, false) > MoveCompletionRanking.priority(plainFn, false))
    }
}
