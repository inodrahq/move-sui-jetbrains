package com.inodra.movesui.resolve

import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** What kind of name a path position asks for; used to rank and filter candidates. */
enum class Namespace { VALUE, TYPE, MODULE, ANY }

object MvScopes {
    /** Bindings introduced by a pattern (all `BindingPat`s inside it, including `x @ pat`). */
    fun bindingsOf(pat: MvPat?): List<MvBindingPat> {
        if (pat == null) return emptyList()
        val result = mutableListOf<MvBindingPat>()
        if (pat is MvBindingPat) result += pat
        result += PsiTreeUtil.findChildrenOfType(pat, MvBindingPat::class.java)
        return result
    }

    /** Shorthand struct pattern fields `S { x }` also bind `x`. */
    fun shorthandFieldBindings(pat: MvPat?): List<MvStructPatField> =
        if (pat == null) emptyList() else PsiTreeUtil.findChildrenOfType(pat, MvStructPatField::class.java).filter { it.pat == null }

    /**
     * Walks lexical scopes outward from [from] and returns the first declarations named [name]
     * (locals shadow outer names; module-level names may be ambiguous and return several).
     */
    fun lookup(from: PsiElement, name: String, ns: Namespace): List<PsiElement> {
        var child: PsiElement = from
        var parent = from.parent
        while (parent != null) {
            val found = entriesAt(parent, child, name, ns)
            if (found.isNotEmpty()) return found
            child = parent
            parent = parent.parent
        }
        return emptyList()
    }

    private fun entriesAt(scope: PsiElement, child: PsiElement, name: String, ns: Namespace): List<PsiElement> = when (scope) {
        is MvCodeBlock -> if (ns == Namespace.TYPE) emptyList() else localsBefore(scope, child, name)
        is MvLambdaExpr -> if (ns == Namespace.TYPE) emptyList() else
            scope.lambdaParamList.lambdaParamList.flatMap { bindingsOf(it.pat) }.filter { it.name == name }
        is MvMatchArm -> if (ns == Namespace.TYPE || child === scope.pat) emptyList() else
            (bindingsOf(scope.pat).filter { it.name == name } + shorthandFieldBindings(scope.pat).filter { it.name == name })
        is MvFunction -> functionScope(scope, child, name, ns)
        is MvStruct -> scope.typeParamList?.typeParamList?.filter { it.name == name } ?: emptyList()
        is MvEnum -> scope.typeParamList?.typeParamList?.filter { it.name == name } ?: emptyList()
        is MvModule -> moduleScope(scope, name, ns)
        is MvScript -> scope.moduleItemList.mapNotNull { it.firstChild }.filter { it is MvNamedElement && it.name == name }
        else -> emptyList()
    }

    private fun functionScope(fn: MvFunction, child: PsiElement, name: String, ns: Namespace): List<PsiElement> {
        val typeParams = fn.typeParamList?.typeParamList?.filter { it.name == name } ?: emptyList()
        if (ns == Namespace.TYPE) return typeParams
        val params = if (child === fn.codeBlock || child === fn.returnType || child === fn.functionParamList)
            fn.params.filter { it.name == name } else emptyList()
        return params + typeParams
    }

    private fun localsBefore(block: MvCodeBlock, child: PsiElement, name: String): List<PsiElement> {
        val stmts = block.stmtList
        val idx = stmts.indexOfFirst { it === child }
        val visible = if (idx >= 0) stmts.subList(0, idx) else stmts
        for (stmt in visible.asReversed()) {
            val let = stmt.letStmt
            if (let != null) {
                val hits = bindingsOf(let.pat).filter { it.name == name } + shorthandFieldBindings(let.pat).filter { it.name == name }
                if (hits.isNotEmpty()) return hits
            }
            val use = stmt.useStmt
            if (use != null) {
                val hit = MvModuleResolver.importsOf(use).filter { it.name == name }.flatMap { it.target() }
                if (hit.isNotEmpty()) return hit
            }
        }
        return emptyList()
    }

    fun moduleScope(module: MvModule, name: String, ns: Namespace): List<PsiElement> {
        val items = module.itemsNamed(name).filter { matches(it, ns) }
        if (items.isNotEmpty()) return items
        return MvModuleResolver.imports(module).filter { it.name == name }.flatMap { it.target() }.filter { matches(it, ns) }
    }

    fun matches(e: PsiElement, ns: Namespace): Boolean = when (ns) {
        Namespace.ANY -> true
        Namespace.VALUE -> e is MvFunction || e is MvConst || e is MvBindingPat || e is MvFunctionParam || e is MvStructPatField || e is MvModule || e is MvStruct || e is MvEnum || e is MvEnumVariant
        Namespace.TYPE -> e is MvStruct || e is MvEnum || e is MvTypeParam || e is MvModule
        Namespace.MODULE -> e is MvModule
    }
}
