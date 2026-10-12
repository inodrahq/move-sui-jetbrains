import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.grammarKit)
    alias(libs.plugins.changelog)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Stay on the API level of the Kotlin stdlib bundled in the target IDE.
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugins("org.toml.lang")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.junit)
}

tasks.generateLexer {
    sourceFile.set(file("src/main/grammars/MoveLexer.flex"))
    purgeOldFiles.set(true)
}

tasks.generateParser {
    sourceFile.set(file("src/main/grammars/MoveParser.bnf"))
    pathToParser.set("com/inodra/movesui/lang/parser/MoveParser.java")
    pathToPsiRoot.set("com/inodra/movesui/lang/psi")
    purgeOldFiles.set(true)
}

// Generated Java must be visible to the Kotlin compiler (and vice versa).
sourceSets {
    main {
        java.srcDirs(
            files(tasks.generateLexer.flatMap { it.targetRootOutputDir }).builtBy(tasks.generateLexer),
            files(tasks.generateParser.flatMap { it.targetRootOutputDir }).builtBy(tasks.generateParser),
        )
    }
}

tasks {
    wrapper { gradleVersion = "9.8.1" }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        // Rendered eagerly: the changelog extension cannot be captured by a configuration-cached provider.
        val notes = with(changelog) {
            renderItem((getOrNull(providers.gradleProperty("pluginVersion").get()) ?: getUnreleased()).withHeader(false).withEmptySections(false), org.jetbrains.changelog.Changelog.OutputType.HTML)
        }
        changeNotes = notes

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }

        vendor {
            name = "Inodra"
            url = "https://inodra.com"
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Pre-release versions (e.g. 0.1.0-beta.1) go to a non-default channel.
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.runIde {
    // Sandbox conveniences: no trust dialog, no tips.
    systemProperty("idea.trust.all.projects", "true")
    systemProperty("ide.show.tips.on.startup.default.value", "false")
    systemProperty("idea.log.debug.categories", "#com.intellij.platform.lsp,#com.inodra.movesui")
}

intellijPlatformTesting {
    runIde {
        // ./gradlew runRustRover — runs the plugin inside the locally installed RustRover.
        register("runRustRover") {
            localPath = file("/Applications/RustRover.app")
        }
        register("runWebStorm") {
            localPath = file("/Applications/WebStorm.app")
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}
