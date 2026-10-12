package com.inodra.movesui.ide.editor

import com.inodra.movesui.lang.psi.MoveTypes.*
import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.psi.PsiComment

class MoveCommenter : CodeDocumentationAwareCommenter {
    override fun getLineCommentPrefix() = "//"
    override fun getBlockCommentPrefix() = "/*"
    override fun getBlockCommentSuffix() = "*/"
    override fun getCommentedBlockCommentPrefix() = null
    override fun getCommentedBlockCommentSuffix() = null

    override fun getLineCommentTokenType() = LINE_COMMENT
    override fun getBlockCommentTokenType() = BLOCK_COMMENT
    override fun getDocumentationCommentTokenType() = DOC_COMMENT
    override fun getDocumentationCommentPrefix() = "///"
    override fun getDocumentationCommentLinePrefix() = "///"
    override fun getDocumentationCommentSuffix() = ""
    override fun isDocumentationComment(element: PsiComment?) = element?.tokenType == DOC_COMMENT
}
