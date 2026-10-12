package com.inodra.movesui

import com.inodra.movesui.lang.psi.ext.MvNamedElement
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language

abstract class MoveTestBase : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData"

    /** Configures `main.move` with [code]; `/*caret*/` marks the caret. */
    protected fun configure(@Language("Move") code: String, fileName: String = "main.move") {
        myFixture.configureByText(fileName, code.replace("/*caret*/", "<caret>"))
    }

    protected fun addFile(path: String, @Language("Move") code: String) {
        myFixture.addFileToProject(path, code)
    }

    protected fun referenceAtCaret() = myFixture.file.findReferenceAt(myFixture.caretOffset)
        ?: error("No reference at caret: '${myFixture.file.findElementAt(myFixture.caretOffset)?.text}'")

    protected fun resolveAtCaret(): PsiElement? = referenceAtCaret().resolve()

    protected fun multiResolveAtCaret(): List<PsiElement> =
        (referenceAtCaret() as com.intellij.psi.PsiPolyVariantReference).multiResolve(false).mapNotNull { it.element }

    protected inline fun <reified T : PsiElement> checkResolvesTo(expectedName: String, expectedText: String? = null) {
        val resolved = resolveAtCaret() ?: error("Unresolved: candidates=${multiResolveAtCaret()}")
        assertTrue("Expected ${T::class.simpleName}, got ${resolved.javaClass.simpleName}", resolved is T)
        assertEquals(expectedName, (resolved as MvNamedElement).name)
        if (expectedText != null) assertTrue("'${resolved.text}' should start with '$expectedText'", resolved.text.startsWith(expectedText))
    }

    protected fun checkUnresolved() {
        val r = multiResolveAtCaret()
        assertTrue("Expected unresolved, got $r", r.isEmpty())
    }
}
