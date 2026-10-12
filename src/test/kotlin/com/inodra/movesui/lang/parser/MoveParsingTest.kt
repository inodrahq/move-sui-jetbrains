package com.inodra.movesui.lang.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import java.io.File

/**
 * Parses every `.move` file under `testData/parser` and fails on any syntax error.
 * Set `MOVE_FRAMEWORK_DIR` to also sweep an external tree (e.g. the Sui framework sources).
 */
class MoveParsingTest : ParsingTestCase("parser", "move", MoveParserDefinition()) {
    override fun getTestDataPath() = "src/test/testData"
    override fun includeRanges() = true

    fun testKitchenSink() = doTest(true)
    fun testLegacy() = doTest(true)
    fun testMacros() = doTest(true)

    override fun getTestName(lowercaseFirstLetter: Boolean): String = when (name) {
        "testKitchenSink" -> "syntax/kitchen_sink"
        "testLegacy" -> "syntax/legacy"
        "testMacros" -> "syntax/macros"
        else -> super.getTestName(lowercaseFirstLetter)
    }

    fun testNoErrorsInTestData() = assertNoErrorsIn(File(testDataPath, "parser"))

    fun testNoErrorsInFramework() {
        val dir = System.getenv("MOVE_FRAMEWORK_DIR")?.let(::File) ?: return
        assertNoErrorsIn(dir)
    }

    private fun assertNoErrorsIn(root: File) {
        val failures = mutableListOf<String>()
        var count = 0
        root.walkTopDown().filter { it.isFile && it.extension == "move" }.forEach { file ->
            count++
            val psi = createPsiFile(file.name, file.readText())
            ensureParsed(psi)
            PsiTreeUtil.findChildrenOfType(psi, PsiErrorElement::class.java).take(3).forEach { err ->
                val line = psi.text.substring(0, err.textOffset).count { it == '\n' } + 1
                failures += "${file.relativeTo(root)}:$line: ${err.errorDescription}"
            }
        }
        assertTrue("no .move files found under $root", count > 0)
        assertTrue("Syntax errors:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
