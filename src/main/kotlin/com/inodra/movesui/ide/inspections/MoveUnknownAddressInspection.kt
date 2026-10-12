package com.inodra.movesui.ide.inspections

import com.inodra.movesui.lang.psi.MvUseGroup
import com.inodra.movesui.lang.psi.MvUseSpeck
import com.inodra.movesui.lang.psi.MvVisitor
import com.inodra.movesui.mvr.MvrDependencies
import com.inodra.movesui.mvr.MvrSearchDialog
import com.inodra.movesui.project.MoveNamedAddresses
import com.inodra.movesui.project.MovePackage
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/** `use foo::bar;` where `foo` is neither a declared address, a dependency, nor the address of any known module. */
class MoveUnknownAddressInspection : MoveLocalInspection() {
    override fun buildMoveVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {
        override fun visitUseSpeck(speck: MvUseSpeck) {
            if (speck.parent is MvUseSpeck || speck.parent is MvUseGroup) return // only the root segment is an address
            if (speck.useSpeck == null && speck.useGroup == null) return       // `use foo;` alone is not an address path
            val name = speck.useSpeckName.text
            val file = holder.file.virtualFile ?: return
            if (name == "Self" || MoveNamedAddresses.isKnown(holder.project, file, name)) return
            holder.registerProblem(speck.useSpeckName, "Unknown named address '$name': not declared in Move.toml and no dependency provides it",
                ProblemHighlightType.WEAK_WARNING, AddMvrDependencyFix(name))
        }
    }
}

class AddMvrDependencyFix(private val addressName: String) : LocalQuickFix {
    override fun getFamilyName() = "Add dependency from the Move Registry"
    override fun getName() = "Add '$addressName' as a Move Registry dependency…"
    override fun startInWriteAction() = false
    override fun availableInBatchMode() = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile?.virtualFile ?: return
        val pkg = MovePackage.forFile(file) ?: return
        ApplicationManager.getApplication().invokeLater {
            val dialog = MvrSearchDialog(project, addressName.replace('_', '-'))
            if (dialog.showAndGet()) dialog.selected?.let { MvrDependencies.add(project, pkg, it, depName = addressName) }
        }
    }
}
