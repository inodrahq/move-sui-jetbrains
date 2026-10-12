package com.inodra.movesui.lang.psi.ext

import com.inodra.movesui.lang.psi.MoveTokenSets
import com.inodra.movesui.lang.psi.MoveTypes
import com.inodra.movesui.lang.psi.MvModule
import com.inodra.movesui.lang.psi.MvPsiFactory
import com.inodra.movesui.lang.stubs.*
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.NamedStub
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.TokenSet

interface MvNamedElement : MvElement, PsiNameIdentifierOwner

internal val NAME_TOKENS: TokenSet = TokenSet.orSet(MoveTokenSets.IDENTIFIERS, TokenSet.create(MoveTypes.UNDERSCORE))

internal fun PsiElement.setNameIdentifier(name: String) {
    (this as PsiNameIdentifierOwner).nameIdentifier?.replace(MvPsiFactory(project).createIdentifier(name))
}

abstract class MvNamedElementImpl(node: ASTNode) : MvElementImpl(node), MvNamedElement {
    override fun getNameIdentifier(): PsiElement? = findChildByType(NAME_TOKENS)
    override fun getName(): String? = nameIdentifier?.text
    override fun setName(name: String): PsiElement { setNameIdentifier(name); return this }
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    companion object {
        val NAME_TOKENS get() = com.inodra.movesui.lang.psi.ext.NAME_TOKENS
    }
}

abstract class MvStubbedNamedElementImpl<S : StubElement<*>> : StubBasedPsiElementBase<S>, MvNamedElement {
    constructor(node: ASTNode) : super(node)
    constructor(stub: S, type: IStubElementType<*, *>) : super(stub, type)

    override fun getNameIdentifier(): PsiElement? = findChildByType(NAME_TOKENS)
    override fun getName(): String? = (greenStub as? NamedStub<*>)?.name ?: nameIdentifier?.text
    override fun setName(name: String): PsiElement { setNameIdentifier(name); return this }
    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
    override fun toString(): String = "${javaClass.simpleName.removeSuffix("Impl")}(${node.elementType})"
}

abstract class MvModuleMixin : MvStubbedNamedElementImpl<MvModuleStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: MvModuleStub, type: IStubElementType<*, *>) : super(stub, type)

    override fun getNameIdentifier(): PsiElement? =
        (this as MvModule).modulePath?.let { path -> path.node.getChildren(NAME_TOKENS).lastOrNull()?.psi }
}

abstract class MvFunctionMixin : MvStubbedNamedElementImpl<MvFunctionStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: MvFunctionStub, type: IStubElementType<*, *>) : super(stub, type)
}

abstract class MvStructMixin : MvStubbedNamedElementImpl<MvStructStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: MvStructStub, type: IStubElementType<*, *>) : super(stub, type)
}

abstract class MvEnumMixin : MvStubbedNamedElementImpl<MvEnumStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: MvEnumStub, type: IStubElementType<*, *>) : super(stub, type)
}

abstract class MvEnumVariantMixin : MvStubbedNamedElementImpl<MvEnumVariantStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: MvEnumVariantStub, type: IStubElementType<*, *>) : super(stub, type)
}

abstract class MvConstMixin : MvStubbedNamedElementImpl<MvConstStub> {
    constructor(node: ASTNode) : super(node)
    constructor(stub: MvConstStub, type: IStubElementType<*, *>) : super(stub, type)
}
