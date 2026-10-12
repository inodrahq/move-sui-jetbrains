package com.inodra.movesui.lang.stubs.index

import com.inodra.movesui.lang.psi.MvModule
import com.inodra.movesui.lang.psi.ext.MvNamedElement
import com.inodra.movesui.lang.stubs.MoveFileStub
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey

/** Every stubbed named item (modules, functions, structs, enums, variants, consts) by simple name. */
class MvNamedElementIndex : StringStubIndexExtension<MvNamedElement>() {
    override fun getVersion() = MoveFileStub.Type.stubVersion
    override fun getKey() = KEY

    companion object {
        val KEY: StubIndexKey<String, MvNamedElement> = StubIndexKey.createIndexKey("move.named")

        fun find(project: Project, name: String, scope: GlobalSearchScope): Collection<MvNamedElement> =
            StubIndex.getElements(KEY, name, project, scope, MvNamedElement::class.java)

        fun allKeys(project: Project): Collection<String> = StubIndex.getInstance().getAllKeys(KEY, project)
    }
}

class MvModuleIndex : StringStubIndexExtension<MvModule>() {
    override fun getVersion() = MoveFileStub.Type.stubVersion
    override fun getKey() = KEY

    companion object {
        val KEY: StubIndexKey<String, MvModule> = StubIndexKey.createIndexKey("move.module")

        fun find(project: Project, name: String, scope: GlobalSearchScope): Collection<MvModule> =
            StubIndex.getElements(KEY, name, project, scope, MvModule::class.java)
    }
}
