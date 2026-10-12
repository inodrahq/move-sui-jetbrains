package com.inodra.movesui.lang.stubs

import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.MoveLanguage
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.inodra.movesui.lang.psi.impl.*
import com.inodra.movesui.lang.stubs.index.MvModuleIndex
import com.inodra.movesui.lang.stubs.index.MvNamedElementIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IStubFileElementType

class MoveFileStub(file: MoveFile?) : PsiFileStubImpl<MoveFile>(file) {
    override fun getType() = Type

    object Type : IStubFileElementType<MoveFileStub>(MoveLanguage) {
        // Bump when stub shape changes.
        override fun getStubVersion() = 3
        override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
            override fun createStubForFile(file: PsiFile) = MoveFileStub(file as MoveFile)
        }
        override fun getExternalId() = "move.file"
        override fun serialize(stub: MoveFileStub, dataStream: StubOutputStream) {}
        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?) = MoveFileStub(null)
    }
}

abstract class MvStubElementType<S : StubElement<*>, P : PsiElement>(debugName: String) : IStubElementType<S, P>(debugName, MoveLanguage) {
    override fun getExternalId() = "move.${super.toString()}"
    override fun indexStub(stub: S, sink: IndexSink) {
        val name = (stub as? NamedStub<*>)?.name ?: return
        sink.occurrence(MvNamedElementIndex.KEY, name)
    }
}

// ---------- Module ----------

class MvModuleStub(parent: StubElement<*>?, type: IStubElementType<*, *>, private val name: String?, val address: String?) :
    StubBase<MvModule>(parent, type), NamedStub<MvModule> {
    override fun getName() = name

    object Type : MvStubElementType<MvModuleStub, MvModule>("MODULE") {
        override fun createPsi(stub: MvModuleStub) = MvModuleImpl(stub, this)
        override fun createStub(psi: MvModule, parent: StubElement<*>?) = MvModuleStub(parent, this, psi.name, psi.addressName)
        override fun serialize(stub: MvModuleStub, out: StubOutputStream) { out.writeName(stub.name); out.writeName(stub.address) }
        override fun deserialize(input: StubInputStream, parent: StubElement<*>?) = MvModuleStub(parent, this, input.readNameString(), input.readNameString())
        override fun indexStub(stub: MvModuleStub, sink: IndexSink) {
            super.indexStub(stub, sink)
            stub.name?.let { sink.occurrence(MvModuleIndex.KEY, it) }
        }
    }
}

// ---------- Function ----------

class MvFunctionStub(parent: StubElement<*>?, type: IStubElementType<*, *>, private val name: String?, val flags: Int) :
    StubBase<MvFunction>(parent, type), NamedStub<MvFunction> {
    override fun getName() = name
    val isTest get() = flags and TEST != 0
    val isEntry get() = flags and ENTRY != 0
    val isMacro get() = flags and MACRO != 0
    val isPublic get() = flags and PUBLIC != 0
    val isPackage get() = flags and PACKAGE != 0

    companion object {
        const val TEST = 1; const val ENTRY = 2; const val MACRO = 4; const val PUBLIC = 8; const val PACKAGE = 16; const val NATIVE = 32; const val TEST_ONLY = 64
        fun flagsOf(f: MvFunction): Int = (if (f.isTest) TEST else 0) or (if (f.isEntry) ENTRY else 0) or (if (f.isMacro) MACRO else 0) or
            (if (f.isPublic) PUBLIC else 0) or (if (f.isPackageVisible) PACKAGE else 0) or (if (f.isNative) NATIVE else 0) or (if (f.isTestOnly) TEST_ONLY else 0)
    }

    object Type : MvStubElementType<MvFunctionStub, MvFunction>("FUNCTION") {
        override fun createPsi(stub: MvFunctionStub) = MvFunctionImpl(stub, this)
        override fun createStub(psi: MvFunction, parent: StubElement<*>?) = MvFunctionStub(parent, this, psi.name, flagsOf(psi))
        override fun serialize(stub: MvFunctionStub, out: StubOutputStream) { out.writeName(stub.name); out.writeVarInt(stub.flags) }
        override fun deserialize(input: StubInputStream, parent: StubElement<*>?) = MvFunctionStub(parent, this, input.readNameString(), input.readVarInt())
    }
}

// ---------- Struct / Enum / Variant / Const ----------

class MvStructStub(parent: StubElement<*>?, type: IStubElementType<*, *>, private val name: String?, val abilities: String?) :
    StubBase<MvStruct>(parent, type), NamedStub<MvStruct> {
    override fun getName() = name
    object Type : MvStubElementType<MvStructStub, MvStruct>("STRUCT") {
        override fun createPsi(stub: MvStructStub) = MvStructImpl(stub, this)
        override fun createStub(psi: MvStruct, parent: StubElement<*>?) = MvStructStub(parent, this, psi.name, psi.abilities.joinToString(",") { it.text })
        override fun serialize(stub: MvStructStub, out: StubOutputStream) { out.writeName(stub.name); out.writeName(stub.abilities) }
        override fun deserialize(input: StubInputStream, parent: StubElement<*>?) = MvStructStub(parent, this, input.readNameString(), input.readNameString())
    }
}

class MvEnumStub(parent: StubElement<*>?, type: IStubElementType<*, *>, private val name: String?) :
    StubBase<MvEnum>(parent, type), NamedStub<MvEnum> {
    override fun getName() = name
    object Type : MvStubElementType<MvEnumStub, MvEnum>("ENUM") {
        override fun createPsi(stub: MvEnumStub) = MvEnumImpl(stub, this)
        override fun createStub(psi: MvEnum, parent: StubElement<*>?) = MvEnumStub(parent, this, psi.name)
        override fun serialize(stub: MvEnumStub, out: StubOutputStream) { out.writeName(stub.name) }
        override fun deserialize(input: StubInputStream, parent: StubElement<*>?) = MvEnumStub(parent, this, input.readNameString())
    }
}

class MvEnumVariantStub(parent: StubElement<*>?, type: IStubElementType<*, *>, private val name: String?) :
    StubBase<MvEnumVariant>(parent, type), NamedStub<MvEnumVariant> {
    override fun getName() = name
    object Type : MvStubElementType<MvEnumVariantStub, MvEnumVariant>("ENUM_VARIANT") {
        override fun createPsi(stub: MvEnumVariantStub) = MvEnumVariantImpl(stub, this)
        override fun createStub(psi: MvEnumVariant, parent: StubElement<*>?) = MvEnumVariantStub(parent, this, psi.name)
        override fun serialize(stub: MvEnumVariantStub, out: StubOutputStream) { out.writeName(stub.name) }
        override fun deserialize(input: StubInputStream, parent: StubElement<*>?) = MvEnumVariantStub(parent, this, input.readNameString())
    }
}

class MvConstStub(parent: StubElement<*>?, type: IStubElementType<*, *>, private val name: String?) :
    StubBase<MvConst>(parent, type), NamedStub<MvConst> {
    override fun getName() = name
    object Type : MvStubElementType<MvConstStub, MvConst>("CONST") {
        override fun createPsi(stub: MvConstStub) = MvConstImpl(stub, this)
        override fun createStub(psi: MvConst, parent: StubElement<*>?) = MvConstStub(parent, this, psi.name)
        override fun serialize(stub: MvConstStub, out: StubOutputStream) { out.writeName(stub.name) }
        override fun deserialize(input: StubInputStream, parent: StubElement<*>?) = MvConstStub(parent, this, input.readNameString())
    }
}

/** Grammar-Kit `elementTypeFactory` entry point. */
object MvElementTypeFactory {
    @JvmStatic
    fun stubFactory(name: String): IStubElementType<*, *> = when (name) {
        "MODULE" -> MvModuleStub.Type
        "FUNCTION" -> MvFunctionStub.Type
        "STRUCT" -> MvStructStub.Type
        "ENUM" -> MvEnumStub.Type
        "ENUM_VARIANT" -> MvEnumVariantStub.Type
        "CONST" -> MvConstStub.Type
        else -> error("Unknown stub element type: $name")
    }
}
