package com.inodra.movesui.lang.psi.ext

import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.psi.*
import com.intellij.psi.PsiElement

/** Any element that can carry `#[...]` attributes. */
val PsiElement.attrs: List<MvAttr>
    get() = when (this) {
        is MvFunction -> attrList
        is MvStruct -> attrList
        is MvEnum -> attrList
        is MvEnumVariant -> attrList
        is MvConst -> attrList
        is MvUseStmt -> attrList
        is MvModule -> attrList
        is MvNamedField -> attrList
        is MvFunctionParam -> attrList
        else -> emptyList()
    }

fun PsiElement.hasAttr(name: String): Boolean =
    attrs.any { attr -> attr.attrItemList.any { it.attrName.text == name } }

val MvFunction.isTest: Boolean get() = hasAttr("test") || hasAttr("random_test")
val MvFunction.isTestOnly: Boolean get() = hasAttr("test_only")
val MvFunction.isEntry: Boolean get() = functionModifierList.any { it.entryKw != null }
val MvFunction.isNative: Boolean get() = functionModifierList.any { it.nativeKw != null }
val MvFunction.isMacro: Boolean get() = functionModifierList.any { it.macroKw != null }
val MvFunction.visibility: MvVisibility? get() = functionModifierList.firstNotNullOfOrNull { it.visibility }
val MvFunction.isPublic: Boolean get() = visibility?.let { it.lparen == null } == true
val MvFunction.isPackageVisible: Boolean get() = visibility?.packageKw != null || visibility?.friendKw != null
val MvFunction.params: List<MvFunctionParam> get() = functionParamList?.functionParamList ?: emptyList()

val MvModule.items: List<PsiElement>
    get() = (moduleBody?.moduleItemList ?: moduleItemList).mapNotNull { it.firstChild }

val MvModule.functions: List<MvFunction> get() = items.filterIsInstance<MvFunction>()
val MvModule.structs: List<MvStruct> get() = items.filterIsInstance<MvStruct>()
val MvModule.enums: List<MvEnum> get() = items.filterIsInstance<MvEnum>()
val MvModule.consts: List<MvConst> get() = items.filterIsInstance<MvConst>()
val MvModule.useStmts: List<MvUseStmt> get() = items.filterIsInstance<MvUseStmt>()
val MvModule.isTestOnly: Boolean get() = hasAttr("test_only")
val MvModule.addressName: String? get() = modulePath?.addressRef?.text
val MvModule.qualifiedName: String get() = listOfNotNull(addressName, name).joinToString("::")
/** `module a::b;` without a brace body. */
val MvModule.isLabelForm: Boolean get() = moduleBody == null

val MoveFile.modules: List<MvModule>
    get() = childrenOfType<MvModule>() + childrenOfType<MvAddressBlock>().flatMap { it.moduleList }

val MvStruct.fields: List<MvNamedField> get() = namedFieldList?.namedFieldList ?: emptyList()
val MvStruct.abilities: List<MvAbility> get() = abilityListList.flatMap { it.abilityList }
val MvStruct.hasKey: Boolean get() = abilities.any { it.text == "key" }
val MvEnum.variants: List<MvEnumVariant> get() = enumBody?.enumVariantList ?: emptyList()
val MvEnum.abilities: List<MvAbility> get() = abilityListList.flatMap { it.abilityList }

val MvPath.segments: List<MvPathSegment> get() = pathSegmentList
val MvPath.lastSegment: MvPathSegment? get() = pathSegmentList.lastOrNull()
val MvPathSegment.nameElement: PsiElement? get() = node.getChildren(MvNamedElementImpl.NAME_TOKENS).firstOrNull()?.psi ?: integerLiteral
val MvPathSegment.referenceName: String? get() = nameElement?.text

val MvCodeBlock.tailExpr: MvExpr?
    get() = stmtList.lastOrNull()?.exprStmt?.takeIf { it.semicolon == null }?.expr

val BUILTIN_TYPES = setOf("u8", "u16", "u32", "u64", "u128", "u256", "bool", "address", "signer", "vector")
val ABILITIES = setOf("copy", "drop", "store", "key")
