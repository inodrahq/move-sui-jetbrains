package com.inodra.movesui.ide.editor

import com.inodra.movesui.lang.psi.MoveTokenSets
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler

class MoveQuoteHandler : SimpleTokenSetQuoteHandler(MoveTokenSets.STRINGS)
