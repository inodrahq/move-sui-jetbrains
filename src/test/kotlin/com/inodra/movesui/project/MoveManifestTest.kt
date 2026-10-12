package com.inodra.movesui.project

import junit.framework.TestCase

class MoveManifestTest : TestCase() {
    fun `test parse inline and table dependencies`() {
        val m = MoveManifest.parse("""
            [package]
            name = "counter"
            edition = "2024" # comment

            [dependencies]
            Sui = { git = "https://github.com/MystenLabs/sui.git", subdir = "crates/sui-framework/packages/sui-framework", rev = "framework/testnet" }
            local_dep = { local = "../other" }
            vaults = { r.mvr = "@abyssonsui/abyss-vaults" }

            [dependencies.Table]
            git = "https://example.com/x.git"
            rev = "main"

            [addresses]
            counter = "0x0"
        """.trimIndent())
        assertEquals("counter", m.name)
        assertEquals("2024", m.edition)
        assertEquals(mapOf("counter" to "0x0"), m.addresses)
        assertEquals(4, m.dependencies.size)
        val sui = m.dependencies.first { it.name == "Sui" }
        assertEquals("crates/sui-framework/packages/sui-framework", sui.subdir)
        assertEquals("framework/testnet", sui.rev)
        assertEquals("../other", m.dependencies.first { it.name == "local_dep" }.local)
        assertEquals("@abyssonsui/abyss-vaults", m.dependencies.first { it.name == "vaults" }.mvr)
        val table = m.dependencies.first { it.name == "Table" }
        assertEquals("main", table.rev)
    }
}
