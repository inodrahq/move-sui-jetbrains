package com.inodra.movesui.toml

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.toml.lang.psi.*

object MoveToml {
    const val FILE_NAME = "Move.toml"
    val PACKAGE_KEYS = listOf("name", "edition", "version", "license", "authors", "published-at", "flavor")
    val DEPENDENCY_KEYS = listOf("git", "rev", "subdir", "local", "override", "r.mvr", "r", "address")
    val SECTIONS = listOf("package", "dependencies", "dev-dependencies", "addresses", "dev-addresses", "environments", "r.mvr")
    val EDITIONS = listOf("2024", "2024.beta", "legacy")

    fun isMoveToml(file: PsiFile?): Boolean = file is TomlFile && file.name == FILE_NAME

    /** Header of the table enclosing [element], e.g. `dependencies` or `package`. */
    fun sectionOf(element: PsiElement): String? = element.parentOfType<TomlTable>(withSelf = true)?.header?.key?.segments?.joinToString(".") { it.name ?: "" }

    fun keyValueOf(element: PsiElement): TomlKeyValue? = element.parentOfType<TomlKeyValue>(withSelf = true)

    fun keyName(kv: TomlKeyValue): String = kv.key.segments.joinToString(".") { it.name ?: "" }

    fun isDependencySection(section: String?) = section == "dependencies" || section == "dev-dependencies" || section?.startsWith("dependencies.") == true || section?.startsWith("dev-dependencies.") == true

    fun literalText(literal: TomlLiteral): String = literal.text.trim().removeSurrounding("\"").removeSurrounding("'")
}
