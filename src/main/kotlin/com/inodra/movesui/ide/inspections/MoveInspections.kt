package com.inodra.movesui.ide.inspections

import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.inodra.movesui.lsp.MoveLspIntegrationProvider
import com.inodra.movesui.resolve.MvModuleResolver
import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

abstract class MoveLocalInspection : LocalInspectionTool() {
    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        if (holder.file is com.inodra.movesui.lang.MoveFile) buildMoveVisitor(holder, isOnTheFly) else PsiElementVisitor.EMPTY_VISITOR

    abstract fun buildMoveVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
}

/** `use` items whose imported name is never referenced in the module. */
class MoveUnusedImportInspection : MoveLocalInspection() {
    override fun buildMoveVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {
        override fun visitUseStmt(use: MvUseStmt) {
            val module = use.ancestorStrict<MvModule>() ?: return
            if (use.useFun != null || use.visibility != null) return
            val speck = use.useSpeck ?: return
            val leaves = mutableListOf<MvUseSpeck>()
            collectLeaves(speck, leaves)
            val scope = LocalSearchScope(module)
            val unusedLeaves = leaves.filter { leaf -> !isUsed(leaf, module, scope) }
            if (unusedLeaves.isEmpty()) return
            if (unusedLeaves.size == leaves.size) {
                holder.registerProblem(use, "Unused import", ProblemHighlightType.LIKE_UNUSED_SYMBOL, RemoveElementFix("Remove unused import"))
            } else {
                unusedLeaves.forEach { leaf -> holder.registerProblem(leaf, "Unused import", ProblemHighlightType.LIKE_UNUSED_SYMBOL, RemoveUseLeafFix()) }
            }
        }
    }

    private fun collectLeaves(speck: MvUseSpeck, out: MutableList<MvUseSpeck>) {
        val group = speck.useGroup
        val child = speck.useSpeck
        when {
            group != null -> group.useSpeckList.forEach { collectLeaves(it, out) }
            child != null -> collectLeaves(child, out)
            else -> out += speck
        }
    }

    private fun isUsed(leaf: MvUseSpeck, module: MvModule, scope: LocalSearchScope): Boolean {
        val alias = leaf.useAlias?.nameIdentifier?.text
        if (alias == "_") return true
        val visible = alias ?: if (leaf.useSpeckName.text == "Self") (leaf.parent.let { (it as? MvUseGroup)?.parent as? MvUseSpeck } ?: leaf.parent as? MvUseSpeck)?.useSpeckName?.text ?: return true else leaf.useSpeckName.text
        // Cheap textual pre-check before resolving.
        val body = module.text
        var idx = body.indexOf(visible)
        var mentions = 0
        while (idx >= 0) { mentions++; if (mentions > 1) break; idx = body.indexOf(visible, idx + visible.length) }
        if (mentions <= 1) return false
        return PsiTreeUtil.findChildrenOfType(module, MvPathSegment::class.java).any { seg ->
            seg.referenceName == visible && seg.parentOfTypeOrNull<MvUseStmt>() == null && (seg.parent as? MvPath)?.segments?.firstOrNull() === seg
        } || PsiTreeUtil.findChildrenOfType(module, MvMethodCallExpr::class.java).any { it.identifier?.text == visible && alias == null && leaf.parent?.parent !is MvUseSpeck && false }
    }
}

private inline fun <reified T : PsiElement> PsiElement.parentOfTypeOrNull(): T? = PsiTreeUtil.getParentOfType(this, T::class.java)

class RemoveElementFix(private val text: String) : LocalQuickFix {
    override fun getFamilyName() = text
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val e = descriptor.psiElement ?: return
        val next = e.nextSibling
        e.delete()
        if (next is com.intellij.psi.PsiWhiteSpace && next.textContains('\n')) runCatching { next.delete() }
    }
}

class RemoveUseLeafFix : LocalQuickFix {
    override fun getFamilyName() = "Remove unused import"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val leaf = descriptor.psiElement as? MvUseSpeck ?: return
        val group = leaf.parent as? MvUseGroup
        if (group == null) { leaf.ancestorStrict<MvUseStmt>()?.delete(); return }
        val remaining = group.useSpeckList.filter { it !== leaf }
        val stmt = leaf.ancestorStrict<MvUseStmt>() ?: return
        if (remaining.isEmpty()) { stmt.delete(); return }
        val prefix = stmt.text.substring(0, group.textRange.startOffset - stmt.textRange.startOffset)
        val newText = if (remaining.size == 1 && remaining[0].useSpeckName.text != "Self") prefix + remaining[0].text + ";" else prefix + "{" + remaining.joinToString(", ") { it.text } + "};"
        stmt.replace(MvPsiFactory(project).createUseStmt(newText.removePrefix("use ").removeSuffix(";")))
    }
}

/** Names that neither native resolution nor (when running) the analyzer can find. Reported only without a running analyzer. */
class MoveUnresolvedReferenceInspection : MoveLocalInspection() {
    override fun buildMoveVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (MoveLspIntegrationProvider.clients(holder.project).any { it.state == LspServerState.Running || it.state == LspServerState.Initializing }) return PsiElementVisitor.EMPTY_VISITOR
        return object : MvVisitor() {
            override fun visitPathSegment(seg: MvPathSegment) {
                val path = seg.parent as? MvPath ?: return
                if (path.segments.size > 1 && path.segments.first() === seg) return // may be an address
                if (seg.referenceName in BUILTIN_TYPES || seg.referenceName == "_") return
                if (path.parent is MvAttrValue || path.ancestorStrict<MvUseStmt>() != null) return
                val ref = seg.reference as? PsiReference ?: return
                if ((ref as com.intellij.psi.PsiPolyVariantReference).multiResolve(false).isEmpty()) {
                    holder.registerProblem(ref, "Unresolved reference '${seg.referenceName}'", ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                }
            }
        }
    }
}

/** Objects (`has key`) must start with `id: UID`. */
class MoveKeyStructIdInspection : MoveLocalInspection() {
    override fun buildMoveVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : MvVisitor() {
        override fun visitStruct(struct: MvStruct) {
            if (!struct.hasKey || struct.namedFieldList == null) return
            val first = struct.fields.firstOrNull()
            val ok = first?.name == "id" && first.type?.text?.endsWith("UID") == true
            if (!ok) holder.registerProblem(struct.nameIdentifier ?: struct, "Struct with 'key' ability must have 'id: UID' as its first field")
        }
    }
}
