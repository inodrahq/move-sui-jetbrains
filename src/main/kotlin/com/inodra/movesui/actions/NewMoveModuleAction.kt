package com.inodra.movesui.actions

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.project.MovePackage
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

/** New | Move Module: creates `name.move` with a `module <package>::<name>;` header. */
class NewMoveModuleAction : CreateFileFromTemplateAction("Move Module", "Create a new Move module", MoveIcons.MOVE_FILE) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle("New Move Module")
            .addKind("Module", MoveIcons.MOVE_FILE, "Move Module")
            .addKind("Test module", MoveIcons.MOVE_FILE, "Move Test Module")
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?) = "Create Move module $newName"

    override fun createFile(name: String, templateName: String, dir: PsiDirectory): PsiFile? {
        val template: FileTemplate = FileTemplateManager.getInstance(dir.project).getInternalTemplate(templateName)
        val pkgName = MovePackage.forFile(dir.virtualFile)?.name?.lowercase()?.replace('-', '_') ?: "package"
        val moduleName = name.removeSuffix(".move")
        val props = FileTemplateManager.getInstance(dir.project).defaultProperties.apply {
            setProperty("PACKAGE_NAME", pkgName)
            setProperty("NAME", moduleName)
        }
        return com.intellij.ide.fileTemplates.FileTemplateUtil.createFromTemplate(template, "$moduleName.move", props, dir).containingFile
    }
}
