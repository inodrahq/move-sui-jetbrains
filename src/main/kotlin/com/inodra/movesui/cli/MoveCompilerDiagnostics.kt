package com.inodra.movesui.cli

import com.google.gson.JsonStreamParser
import com.inodra.movesui.toolchain.SuiToolchain
import com.intellij.execution.process.CapturingProcessHandler
import java.nio.file.Path

/** One entry of `sui move build --json-errors` (printed to stderr, possibly as several JSON arrays). */
data class MoveCompilerDiagnostic(val file: String, val line: Int, val column: Int, val level: String, val message: String, val category: Int, val code: Int) {
    val isError get() = level.contains("Error", ignoreCase = true) || level == "Bug"
}

object MoveCompilerDiagnostics {
    fun parse(stderr: String): List<MoveCompilerDiagnostic> {
        val start = stderr.indexOf('[')
        if (start < 0) return emptyList()
        val result = mutableListOf<MoveCompilerDiagnostic>()
        runCatching {
            val parser = JsonStreamParser(stderr.substring(start))
            while (parser.hasNext()) {
                val el = parser.next()
                if (!el.isJsonArray) continue
                for (item in el.asJsonArray) {
                    val o = item.asJsonObject
                    result += MoveCompilerDiagnostic(
                        file = o["file"]?.asString ?: continue,
                        line = o["line"]?.asInt ?: 1,
                        column = o["column"]?.asInt ?: 1,
                        level = o["level"]?.asString ?: "Error",
                        message = o["msg"]?.asString ?: "",
                        category = o["category"]?.asInt ?: 0,
                        code = o["code"]?.asInt ?: 0,
                    )
                }
            }
        }
        return result
    }

    /** Runs `sui move build --json-errors --test` in [packageDir]; returns null when the CLI could not run. */
    fun build(toolchain: SuiToolchain, packageDir: Path, extra: List<String> = emptyList()): List<MoveCompilerDiagnostic>? {
        val cmd = toolchain.commandLine("move", "build", "--json-errors", "--test", *extra.toTypedArray(), workDir = packageDir)
        val output = runCatching { CapturingProcessHandler(cmd).runProcess(180_000) }.getOrNull() ?: return null
        if (output.isTimeout) return null
        return parse(output.stderr)
    }
}
