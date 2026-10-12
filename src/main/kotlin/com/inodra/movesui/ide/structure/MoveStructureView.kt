package com.inodra.movesui.ide.structure

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

class MoveStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is MoveFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel = MoveStructureViewModel(psiFile, editor)
        }
    }
}

class MoveStructureViewModel(file: MoveFile, editor: Editor?) :
    StructureViewModelBase(file, editor, MoveStructureViewElement(file)), StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(MvModule::class.java, MvFunction::class.java, MvStruct::class.java, MvEnum::class.java, MvConst::class.java)
    }

    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)
    override fun isAlwaysShowsPlus(element: StructureViewTreeElement) = element.value is MvModule
    override fun isAlwaysLeaf(element: StructureViewTreeElement) = element.value is MvFunction || element.value is MvConst
}

class MoveStructureViewElement(private val element: PsiElement) : StructureViewTreeElement, SortableTreeElement {
    override fun getValue() = element
    override fun navigate(requestFocus: Boolean) = (element as? NavigatablePsiElement)?.navigate(requestFocus) ?: Unit
    override fun canNavigate() = (element as? NavigatablePsiElement)?.canNavigate() ?: false
    override fun canNavigateToSource() = canNavigate()
    override fun getAlphaSortKey(): String = (element as? MvNamedElement)?.name ?: ""

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): @NlsSafe String = presentableText(element)
        override fun getLocationString(): String? = locationString(element)
        override fun getIcon(unused: Boolean): Icon? = iconFor(element)
    }

    override fun getChildren(): Array<StructureViewTreeElement> {
        val children: List<PsiElement> = when (element) {
            is MoveFile -> element.modules
            is MvModule -> element.items.filter { it is MvFunction || it is MvStruct || it is MvEnum || it is MvConst }
            is MvStruct -> element.fields
            is MvEnum -> element.variants
            else -> emptyList()
        }
        return children.map { MoveStructureViewElement(it) }.toTypedArray()
    }
}

fun presentableText(e: PsiElement): String = when (e) {
    is MoveFile -> e.name
    is MvModule -> e.qualifiedName
    is MvFunction -> buildString {
        append(e.name ?: "?")
        append(e.typeParamList?.text ?: "")
        append("(")
        append(e.params.joinToString(", ") { it.type?.text ?: "" })
        append(")")
        e.returnType?.type?.let { append(": ").append(it.text) }
    }
    is MvStruct -> (e.name ?: "?") + (e.typeParamList?.text ?: "")
    is MvEnum -> (e.name ?: "?") + (e.typeParamList?.text ?: "")
    is MvEnumVariant -> e.name ?: "?"
    is MvConst -> (e.name ?: "?") + ": " + (e.type?.text ?: "")
    is MvNamedField -> (e.name ?: "?") + ": " + (e.type?.text ?: "")
    is MvNamedElement -> e.name ?: "?"
    else -> e.text
}

fun locationString(e: PsiElement): String? = when (e) {
    is MvFunction -> listOfNotNull(
        e.visibility?.text, "entry".takeIf { e.isEntry }, "macro".takeIf { e.isMacro }, "native".takeIf { e.isNative }, "#[test]".takeIf { e.isTest },
    ).joinToString(" ").ifEmpty { null }
    is MvStruct -> e.abilities.joinToString(", ") { it.text }.let { if (it.isEmpty()) null else "has $it" }
    is MvEnum -> e.abilities.joinToString(", ") { it.text }.let { if (it.isEmpty()) null else "has $it" }
    else -> null
}

fun iconFor(e: PsiElement): Icon? = when (e) {
    is MoveFile -> MoveIcons.MOVE_FILE
    is MvModule -> AllIcons.Nodes.Module
    is MvFunction -> when {
        e.isTest -> AllIcons.Nodes.Test
        e.isMacro -> AllIcons.Nodes.Template
        else -> AllIcons.Nodes.Function
    }
    is MvStruct -> AllIcons.Nodes.Class
    is MvEnum -> AllIcons.Nodes.Enum
    is MvEnumVariant -> AllIcons.Nodes.Constant
    is MvConst -> AllIcons.Nodes.Constant
    is MvNamedField -> AllIcons.Nodes.Field
    else -> null
}
