package com.inodra.movesui.lang

import com.inodra.movesui.MoveIcons
import com.intellij.openapi.fileTypes.LanguageFileType

object MoveFileType : LanguageFileType(MoveLanguage) {
    override fun getName() = "Move"
    override fun getDescription() = "Move source file"
    override fun getDefaultExtension() = "move"
    override fun getIcon() = MoveIcons.MOVE_FILE
}
