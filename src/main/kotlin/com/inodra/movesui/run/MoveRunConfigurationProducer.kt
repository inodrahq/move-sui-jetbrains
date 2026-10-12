package com.inodra.movesui.run

import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.psi.MvFunction
import com.inodra.movesui.lang.psi.MvModule
import com.inodra.movesui.lang.psi.ext.*
import com.inodra.movesui.project.MovePackage
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType

/** Test targets: a `#[test]` function, a test module, or a whole package. */
class MoveTestRunConfigurationProducer : LazyRunConfigurationProducer<MoveRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = MoveRunConfigurationType.getInstance()

    override fun setupConfigurationFromContext(configuration: MoveRunConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val target = findTarget(context) ?: return false
        configuration.command = MoveCommand.TEST
        configuration.packagePath = target.pkg.root.path
        configuration.testFilter = target.filter
        configuration.name = configuration.suggestedName() ?: "Test"
        sourceElement.set(target.element)
        return true
    }

    override fun isConfigurationFromContext(configuration: MoveRunConfiguration, context: ConfigurationContext): Boolean {
        val target = findTarget(context) ?: return false
        return configuration.command == MoveCommand.TEST && configuration.packagePath == target.pkg.root.path && configuration.testFilter == target.filter
    }

    private class Target(val element: PsiElement, val pkg: MovePackage, val filter: String)

    private fun findTarget(context: ConfigurationContext): Target? {
        val element = context.psiLocation ?: return null
        val file = element.containingFile as? MoveFile ?: return null
        val pkg = MovePackage.forFile(file.virtualFile ?: return null) ?: return null
        val fn = element.parentOfType<MvFunction>(withSelf = true)
        if (fn != null && fn.isTest) {
            val module = fn.ancestorStrict<MvModule>() ?: return null
            return Target(fn, pkg, "${module.name}::${fn.name}")
        }
        val module = element.parentOfType<MvModule>(withSelf = true)
        if (module != null && module.functions.any { it.isTest }) return Target(module, pkg, "${module.name}::")
        if (file.modules.any { m -> m.functions.any { it.isTest } }) return Target(file, pkg, file.modules.first().name?.let { "$it::" } ?: "")
        return null
    }
}

class MoveBuildRunConfigurationProducer : LazyRunConfigurationProducer<MoveRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = MoveRunConfigurationType.getInstance()

    override fun setupConfigurationFromContext(configuration: MoveRunConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val pkg = packageFromManifest(context) ?: return false
        configuration.command = MoveCommand.BUILD
        configuration.packagePath = pkg.root.path
        configuration.name = configuration.suggestedName() ?: "Build"
        return true
    }

    override fun isConfigurationFromContext(configuration: MoveRunConfiguration, context: ConfigurationContext): Boolean {
        val pkg = packageFromManifest(context) ?: return false
        return configuration.command == MoveCommand.BUILD && configuration.packagePath == pkg.root.path
    }

    private fun packageFromManifest(context: ConfigurationContext): MovePackage? {
        val file = context.psiLocation?.containingFile?.virtualFile ?: context.location?.virtualFile ?: return null
        if (file.name != MovePackage.MANIFEST) return null
        return MovePackage.forFile(file)
    }
}
