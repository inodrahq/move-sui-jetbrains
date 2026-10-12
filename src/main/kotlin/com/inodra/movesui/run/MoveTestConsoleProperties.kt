package com.inodra.movesui.run

import com.inodra.movesui.lang.psi.MvFunction
import com.inodra.movesui.lang.psi.ext.qualifiedName
import com.inodra.movesui.resolve.MvModuleResolver
import com.inodra.movesui.resolve.itemsNamed
import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

class MoveTestConsoleProperties(config: MoveRunConfiguration, executor: Executor) :
    SMTRunnerConsoleProperties(config, "Sui Move", executor), SMCustomMessagesParsing {

    init {
        setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false)
        isIdBasedTestTree = false
    }

    override fun getTestLocator(): SMTestLocator = MoveTestLocator

    override fun createTestEventsConverter(testFrameworkName: String, consoleProperties: TestConsoleProperties): OutputToGeneralTestEventsConverter =
        MoveTestEventsConverter(testFrameworkName, consoleProperties)
}

class MoveTestEventsConverter(frameworkName: String, props: TestConsoleProperties) : OutputToGeneralTestEventsConverter(frameworkName, props) {
    private val parser = MoveTestOutputParser()

    override fun processConsistentText(text: String, outputType: Key<*>) {
        val messages = parser.process(text)
        super.processConsistentText(text, outputType)
        messages.forEach { super.processConsistentText(it + "\n", com.intellij.execution.process.ProcessOutputTypes.STDOUT) }
    }

    override fun flushBufferOnProcessTermination(exitCode: Int) {
        parser.finish().forEach { super.processConsistentText(it + "\n", com.intellij.execution.process.ProcessOutputTypes.STDOUT) }
        super.flushBufferOnProcessTermination(exitCode)
    }
}

/** `move-test://addr::module::fun` → the function (or module) declaration. */
object MoveTestLocator : SMTestLocator {
    const val PROTOCOL = "move-test"

    override fun getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): List<com.intellij.execution.Location<*>> {
        if (protocol != PROTOCOL) return emptyList()
        val parts = path.split("::")
        if (parts.size < 2) return emptyList()
        val modules = MvModuleResolver.findModules(project, parts[0], parts[1])
        val targets: List<PsiElement> = if (parts.size == 2) modules else modules.flatMap { it.itemsNamed(parts[2]).filterIsInstance<MvFunction>() }
        return targets.map { com.intellij.execution.PsiLocation(project, it) }
    }
}
