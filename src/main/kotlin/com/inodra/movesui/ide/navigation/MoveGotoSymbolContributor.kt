package com.inodra.movesui.ide.navigation

import com.inodra.movesui.ide.structure.iconFor
import com.inodra.movesui.ide.structure.presentableText
import com.inodra.movesui.lang.psi.MvEnum
import com.inodra.movesui.lang.psi.MvModule
import com.inodra.movesui.lang.psi.MvStruct
import com.inodra.movesui.lang.psi.ext.MvNamedElement
import com.inodra.movesui.lang.psi.ext.ancestorStrict
import com.inodra.movesui.lang.psi.ext.qualifiedName
import com.inodra.movesui.lang.stubs.index.MvNamedElementIndex
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import javax.swing.Icon

open class MoveGotoSymbolContributor : ChooseByNameContributorEx {
    protected open fun accept(e: PsiElement) = true

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        StubIndex.getInstance().processAllKeys(MvNamedElementIndex.KEY, processor, scope, filter)
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        StubIndex.getInstance().processElements(MvNamedElementIndex.KEY, name, parameters.project, parameters.searchScope, parameters.idFilter, MvNamedElement::class.java) { e ->
            if (!accept(e)) return@processElements true
            processor.process(MoveNavigationItem(e))
        }
    }
}

class MoveGotoClassContributor : MoveGotoSymbolContributor() {
    override fun accept(e: PsiElement) = e is MvStruct || e is MvEnum || e is MvModule
}

class MoveNavigationItem(val element: MvNamedElement) : NavigationItem {
    override fun getName() = element.name
    override fun navigate(requestFocus: Boolean) = (element as? com.intellij.psi.NavigatablePsiElement)?.navigate(requestFocus) ?: Unit
    override fun canNavigate() = true
    override fun canNavigateToSource() = true
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText() = presentableText(element)
        override fun getLocationString(): String? = element.ancestorStrict<MvModule>()?.qualifiedName ?: element.containingFile?.name
        override fun getIcon(unused: Boolean): Icon? = iconFor(element)
    }
}
