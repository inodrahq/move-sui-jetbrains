package com.inodra.movesui.ide

import com.inodra.movesui.MoveTestBase
import com.inodra.movesui.ide.inspections.MoveUnknownAddressInspection

class MoveUseCompletionTest : MoveTestBase() {
    private fun setupPackage() {
        myFixture.addFileToProject("Move.toml", """
            [package]
            name = "demo"
            edition = "2024"

            [dependencies]
            cetus_clmm = { r.mvr = "@cetuspackages/clmm" }

            [addresses]
            demo = "0x0"
        """.trimIndent())
        myFixture.addFileToProject("Move.lock", """
            [move]
            version = 4

            [pinned.mainnet.demo]
            source = { root = true }
            deps = { std = "MoveStdlib", sui = "Sui", cetus_clmm = "CetusClmm", deepbook = "DeepBook" }
        """.trimIndent())
        myFixture.addFileToProject("sources/other.move", "module demo::other;\npublic fun f() {}\n")
    }

    fun `test use root offers named addresses`() {
        setupPackage()
        myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("sources/main2.move", "module demo::main2;\nuse <caret>").virtualFile)
        val items = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        for (expected in listOf("std", "sui", "demo", "cetus_clmm", "deepbook")) assertTrue("missing $expected in $items", expected in items)
    }

    fun `test use address offers its modules`() {
        setupPackage()
        myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("sources/main.move", "module demo::main;\nuse demo::<caret>").virtualFile)
        val items = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue("expected module 'other' in $items", "other" in items)
    }

    fun `test use module offers items`() {
        setupPackage()
        myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("sources/main.move", "module demo::main;\nuse demo::other::<caret>").virtualFile)
        val items = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue("expected 'f' and 'Self' in $items", "f" in items && "Self" in items)
    }

    fun `test unknown address is reported with a registry quick fix`() {
        setupPackage()
        myFixture.enableInspections(MoveUnknownAddressInspection())
        myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("sources/main.move", "module demo::main;\nuse cetus_clmm::pool;\nuse unknown_pkg::thing;\n").virtualFile)
        val highlights = myFixture.doHighlighting().filter { it.description?.contains("Unknown named address") == true }
        assertEquals(listOf("unknown_pkg"), highlights.map { it.text })
        assertTrue(myFixture.getAllQuickFixes().any { it.text.contains("Move Registry") })
    }
}

class MoveStructFieldCompletionTest : com.inodra.movesui.MoveTestBase() {
    fun `test missing struct literal fields are offered`() {
        configure("""
            module a::m;
            public struct Counter has key { id: UID, value: u64, owner: address }
            fun f(): Counter { Counter { id: 1, value: 0, ow/*caret*/ } }
        """)
        val items = myFixture.completeBasic()?.map { it.lookupString }
        // A single match is inserted directly; otherwise `owner` must be listed and `id` must not.
        if (items != null) { assertTrue("owner" in items); assertFalse("id" in items) }
        assertTrue(myFixture.editor.document.text.contains("owner: "))
    }

    fun `test struct pattern fields are offered`() {
        configure("""
            module a::m;
            public struct P has drop { x: u64, y: u64 }
            fun f(p: P) { let P { x, /*caret*/ } = p; }
        """)
        val items = myFixture.completeBasic()?.map { it.lookupString }
        if (items != null) { assertEquals(listOf("y"), items); myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR) }
        assertTrue("text=${myFixture.editor.document.text}", myFixture.editor.document.text.contains("let P { x, y }"))
    }
}

class MoveLocalCompletionTest : com.inodra.movesui.MoveTestBase() {
    fun `test parameters and locals are offered in expressions`() {
        configure("""
            module a::m;
            public struct Counter has key { id: UID, owner: address }
            fun f(ctx: &mut TxContext) {
                let count = 1;
                let c = Counter { id: 1, owner: c/*caret*/ };
            }
        """)
        val items = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue("ctx missing in $items", "ctx" in items)
        assertTrue("count missing in $items", "count" in items)
    }
}
