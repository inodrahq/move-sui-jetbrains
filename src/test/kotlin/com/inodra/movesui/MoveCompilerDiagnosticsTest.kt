package com.inodra.movesui

import com.inodra.movesui.cli.MoveCompilerDiagnostics
import junit.framework.TestCase

class MoveCompilerDiagnosticsTest : TestCase() {
    fun `test parses concatenated json arrays from stderr`() {
        val stderr = """
            INCLUDING DEPENDENCY Sui
            BUILDING counter
            [
              { "file": "./sources/counter.move", "line": 37, "column": 4, "level": "Warning", "category": 9, "code": 8, "msg": "unused function" }
            ]
            [
              { "file": "./sources/counter.move", "line": 37, "column": 39, "level": "BlockingError", "category": 3, "code": 9, "msg": "unbound variable" }
            ]
            Failed to build Move modules: Compilation error.
        """.trimIndent()
        val d = MoveCompilerDiagnostics.parse(stderr)
        assertEquals(2, d.size)
        assertFalse(d[0].isError)
        assertTrue(d[1].isError)
        assertEquals("unbound variable", d[1].message)
    }
}
