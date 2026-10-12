package com.inodra.movesui.toml

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlVisitor

/** Sanity checks for Move.toml: unknown keys, missing or legacy edition, malformed MVR names. */
class MoveTomlInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!MoveToml.isMoveToml(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        return object : TomlVisitor() {
            override fun visitKeyValue(kv: TomlKeyValue) {
                val section = MoveToml.sectionOf(kv)
                val key = MoveToml.keyName(kv)
                val value = kv.value
                when {
                    section == "package" && key !in MoveToml.PACKAGE_KEYS ->
                        holder.registerProblem(kv.key, "Unknown [package] key '$key'", ProblemHighlightType.WEAK_WARNING)
                    section == "package" && key == "edition" && value is TomlLiteral && MoveToml.literalText(value) !in MoveToml.EDITIONS ->
                        holder.registerProblem(value, "Unknown edition; expected one of ${MoveToml.EDITIONS.joinToString()}")
                    section == "package" && key == "edition" && value is TomlLiteral && MoveToml.literalText(value) == "legacy" ->
                        holder.registerProblem(value, "Legacy edition; migrate with `sui move migrate`", ProblemHighlightType.WEAK_WARNING)
                    (key == "r.mvr" || key == "mvr") && value is TomlLiteral && !com.inodra.movesui.mvr.MvrClient.NAME_REGEX.matches(MoveToml.literalText(value)) ->
                        holder.registerProblem(value, "MVR names look like @org/package")
                }
            }
        }
    }
}
