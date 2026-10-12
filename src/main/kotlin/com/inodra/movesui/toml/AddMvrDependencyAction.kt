package com.inodra.movesui.toml

import com.inodra.movesui.mvr.MvrDependencies
import com.inodra.movesui.mvr.MvrSearchDialog
import com.inodra.movesui.project.MovePackage
import com.inodra.movesui.project.MovePackageService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/** Tools | Sui Move | Add MVR Dependency…: search the Move Registry and append `name = { r.mvr = "@org/pkg" }`. */
class AddMvrDependencyAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) { e.presentation.isEnabledAndVisible = e.project != null }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = MovePackageService.getInstance(project)
        val pkg = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { service.packageFor(it) } ?: service.packages().firstOrNull()
            ?: run { Messages.showErrorDialog(project, "No Move package (Move.toml) found in this project.", "Add MVR Dependency"); return }
        val dialog = MvrSearchDialog(project)
        if (!dialog.showAndGet()) return
        val chosen = dialog.selected ?: return
        MvrDependencies.add(project, pkg, chosen)
    }
}
