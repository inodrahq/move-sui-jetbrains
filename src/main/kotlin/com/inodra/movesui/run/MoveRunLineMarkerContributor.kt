package com.inodra.movesui.run

import com.inodra.movesui.lang.psi.MvFunction
import com.inodra.movesui.lang.psi.MvModule
import com.inodra.movesui.lang.psi.ext.NAME_TOKENS
import com.inodra.movesui.lang.psi.ext.functions
import com.inodra.movesui.lang.psi.ext.isTest
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType

/** Gutter run icons on `#[test]` functions and on modules that contain tests. */
class MoveRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.elementType !in NAME_TOKENS) return null
        val parent = element.parent
        val isTarget = when (parent) {
            is MvFunction -> parent.nameIdentifier === element && parent.isTest
            is com.inodra.movesui.lang.psi.MvModulePath -> (parent.parent as? MvModule)?.let { it.nameIdentifier === element && it.functions.any { f -> f.isTest } } == true
            else -> false
        }
        if (!isTarget) return null
        val actions = ExecutorAction.getActions(0)
        return Info(AllIcons.RunConfigurations.TestState.Run, actions) { "Run Move test" }
    }
}
