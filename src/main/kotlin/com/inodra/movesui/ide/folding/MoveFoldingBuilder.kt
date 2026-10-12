package com.inodra.movesui.ide.folding

import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.descendantsOfType
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

class MoveFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (root !is MoveFile) return emptyArray()
        val descriptors = mutableListOf<FoldingDescriptor>()

        for (e in PsiTreeUtil.findChildrenOfAnyType(
            root, MvModuleBody::class.java, MvCodeBlock::class.java, MvNamedFieldList::class.java, MvEnumBody::class.java,
            MvMatchBody::class.java, MvUseGroup::class.java, MvStructLitFieldList::class.java,
        )) {
            if (document.getLineNumber(e.textRange.startOffset) != document.getLineNumber(e.textRange.endOffset)) {
                descriptors += FoldingDescriptor(e.node, e.textRange)
            }
        }

        // Consecutive `use` statements fold into one region.
        for (module in root.descendantsOfType<MvModule>()) {
            val items = (module.moduleBody?.moduleItemList ?: module.moduleItemList)
            var run = mutableListOf<MvUseStmt>()
            fun flush() {
                if (run.size > 1) {
                    val range = TextRange(run.first().textRange.startOffset, run.last().textRange.endOffset)
                    descriptors += FoldingDescriptor(run.first().node, range, null, setOf(), false, "use ...", null)
                }
                run = mutableListOf()
            }
            for (item in items) {
                val use = item.useStmt
                if (use != null) run += use else flush()
            }
            flush()
        }

        // Doc comment runs and multi-line block comments.
        for (comment in root.descendantsOfType<PsiComment>()) {
            when (comment.elementType) {
                MoveTypes.BLOCK_COMMENT, MoveTypes.DOC_COMMENT -> {
                    if (comment.text.startsWith("/*") && comment.textContains('\n')) {
                        descriptors += FoldingDescriptor(comment.node, comment.textRange, null, setOf(), false, "/*...*/", null)
                    }
                }
            }
        }
        var docRun = mutableListOf<PsiComment>()
        fun flushDocs() {
            if (docRun.size > 1) {
                val range = TextRange(docRun.first().textRange.startOffset, docRun.last().textRange.endOffset)
                descriptors += FoldingDescriptor(docRun.first().node, range, null, setOf(), false, "/// ...", null)
            }
            docRun = mutableListOf()
        }
        var prev: PsiElement? = null
        root.descendantsOfType<PsiComment>().forEach { c ->
            val isLineDoc = c.elementType == MoveTypes.DOC_COMMENT && c.text.startsWith("///")
            val adjacent = prev != null && c.prevSibling?.let { it.textContains('\n') && it.text.count { ch -> ch == '\n' } == 1 } == true
            if (isLineDoc && (docRun.isEmpty() || (adjacent && docRun.last() === prev))) docRun += c else { flushDocs(); if (isLineDoc) docRun += c }
            prev = c
        }
        flushDocs()

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = when (node.elementType) {
        MoveTypes.USE_STMT -> "use ..."
        MoveTypes.BLOCK_COMMENT, MoveTypes.DOC_COMMENT -> "/*...*/"
        else -> "{...}"
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
