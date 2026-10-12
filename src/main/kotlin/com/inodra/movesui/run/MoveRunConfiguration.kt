package com.inodra.movesui.run

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.toolchain.MoveSettings
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.nio.file.Path

enum class MoveCommand(val cliName: String, val display: String) {
    BUILD("build", "Build"), TEST("test", "Test");
}

class MoveRunConfigurationType : SimpleConfigurationType(
    "MoveSuiRunConfiguration", "Sui Move", "Build or test a Sui Move package", com.intellij.openapi.util.NotNullLazyValue.createValue { MoveIcons.SUI },
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration = MoveRunConfiguration(project, this, "Sui Move")
    override fun getOptionsClass() = MoveRunConfigurationOptions::class.java

    companion object {
        fun getInstance(): MoveRunConfigurationType = ConfigurationTypeUtil.findConfigurationType(MoveRunConfigurationType::class.java)
    }
}

class MoveRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var command by string(MoveCommand.TEST.name)
    var packagePath by string("")
    var testFilter by string("")
    var extraArgs by string("")
    var coverage by property(false)
    var lint by property(false)
    var warningsAsErrors by property(false)
    var trace by property(false)
    var gasLimit by string("")
    var seed by string("")
}

class MoveRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    LocatableConfigurationBase<MoveRunConfigurationOptions>(project, factory, name) {

    public override fun getOptions(): MoveRunConfigurationOptions = super.getOptions() as MoveRunConfigurationOptions

    var command: MoveCommand
        get() = runCatching { MoveCommand.valueOf(options.command ?: "TEST") }.getOrDefault(MoveCommand.TEST)
        set(value) { options.command = value.name }
    var packagePath: String
        get() = options.packagePath ?: ""
        set(value) { options.packagePath = value }
    var testFilter: String
        get() = options.testFilter ?: ""
        set(value) { options.testFilter = value }
    var extraArgs: String
        get() = options.extraArgs ?: ""
        set(value) { options.extraArgs = value }

    val workingDirectory: Path?
        get() = packagePath.ifBlank { project.basePath ?: return null }.let(Path::of)

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = MoveRunConfigurationEditor(project)

    override fun checkConfiguration() {
        if (MoveSettings.getInstance().toolchain == null) throw RuntimeConfigurationError("Sui CLI not found. Configure it in Settings | Languages & Frameworks | Move for Sui.")
        val dir = workingDirectory?.toFile()
        if (dir == null || !dir.resolve("Move.toml").isFile) throw RuntimeConfigurationError("Package directory must contain Move.toml")
    }

    override fun suggestedName(): @NlsActions.ActionText String? = when (command) {
        MoveCommand.BUILD -> "Build ${packageName()}"
        MoveCommand.TEST -> if (testFilter.isBlank()) "Test ${packageName()}" else "Test $testFilter"
    }

    private fun packageName() = workingDirectory?.fileName?.toString() ?: "package"

    fun buildCommandLine(): GeneralCommandLine {
        val toolchain = MoveSettings.getInstance().toolchain ?: throw com.intellij.execution.ExecutionException("Sui CLI not found")
        val args = mutableListOf("move", command.cliName)
        val o = options
        if (command == MoveCommand.TEST) {
            if (testFilter.isNotBlank()) args += testFilter
            if (o.coverage) args += "--coverage"
            if (o.trace) args += "--trace"
            o.gasLimit?.takeIf { it.isNotBlank() }?.let { args += listOf("--gas-limit", it) }
            o.seed?.takeIf { it.isNotBlank() }?.let { args += listOf("--seed", it) }
        }
        if (o.lint) args += "--lint"
        if (o.warningsAsErrors) args += "--warnings-are-errors"
        args += ParametersListUtil.parse(extraArgs)
        return toolchain.commandLine(*args.toTypedArray(), workDir = workingDirectory)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        MoveCommandLineState(this, environment)

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(options, element)
    }
    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(options, element)
    }
}

private object ParametersListUtil {
    fun parse(s: String): List<String> = com.intellij.util.execution.ParametersListUtil.parse(s)
}
