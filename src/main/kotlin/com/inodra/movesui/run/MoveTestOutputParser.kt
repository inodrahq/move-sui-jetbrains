package com.inodra.movesui.run

/**
 * Pure translation of `sui move test` output lines into TeamCity service messages (as strings),
 * which the SM test runner turns into a test tree.
 *
 * ```
 * [ PASS    ] counter::counter_tests::test_increment
 * [ FAIL    ] counter::counter_tests::test_x
 * Failures in counter::counter_tests:
 * ┌── test_x ──────
 * │ error[E11001]: test failure
 * └──────────────────
 * Test result: FAILED. Total tests: 2; passed: 1; failed: 1
 * ```
 */
class MoveTestOutputParser {
    private val pendingFailures = linkedMapOf<String, String>() // fqn → module
    private var currentFailureName: String? = null
    private var currentFailureModule: String? = null
    private val failureLines = StringBuilder()
    private var currentSuite: String? = null

    /** Service messages to emit for [rawLine] (without trailing newline); empty when the line is plain output. */
    fun process(rawLine: String): List<String> {
        val out = mutableListOf<String>()
        val line = STRIP_ANSI.replace(rawLine, "").trimEnd('\n', '\r')
        RESULT_LINE.matchEntire(line.trim())?.let { m ->
            val (status, fqn) = m.destructured
            val module = fqn.substringBeforeLast("::")
            val name = fqn.substringAfterLast("::")
            ensureSuite(module, out)
            when (status) {
                "PASS" -> {
                    out += tc("testStarted", "name" to name, "locationHint" to "${MoveTestLocator.PROTOCOL}://$fqn")
                    out += tc("testFinished", "name" to name)
                }
                else -> pendingFailures[fqn] = module
            }
            return out
        }
        FAILURES_IN.matchEntire(line.trim())?.let { currentFailureModule = it.groupValues[1]; return out }
        FAILURE_START.find(line)?.let { currentFailureName = it.groupValues[1]; failureLines.clear(); return out }
        if (currentFailureName != null) {
            if (line.trimStart().startsWith("└")) flushFailure(out) else failureLines.append(line.trimStart().trimStart('│', ' ')).append('\n')
            return out
        }
        if (line.startsWith("Test result:")) finish(out)
        return out
    }

    /** Emits whatever is still pending (process ended). */
    fun finish(out: MutableList<String> = mutableListOf()): List<String> {
        if (currentFailureName != null) flushFailure(out)
        pendingFailures.keys.toList().forEach { emitFailure(it, "Test failed (see console output)", out) }
        closeSuite(out)
        return out
    }

    private fun flushFailure(out: MutableList<String>) {
        val name = currentFailureName ?: return
        val module = currentFailureModule
        val fqn = pendingFailures.keys.firstOrNull { it.endsWith("::$name") && (module == null || it.startsWith(module)) } ?: "$module::$name"
        emitFailure(fqn, failureLines.toString().trim(), out)
        currentFailureName = null
    }

    private fun emitFailure(fqn: String, details: String, out: MutableList<String>) {
        pendingFailures.remove(fqn)
        val module = fqn.substringBeforeLast("::")
        val name = fqn.substringAfterLast("::")
        ensureSuite(module, out)
        out += tc("testStarted", "name" to name, "locationHint" to "${MoveTestLocator.PROTOCOL}://$fqn")
        out += tc("testFailed", "name" to name, "message" to (details.lineSequence().firstOrNull() ?: "Test failed"), "details" to details)
        out += tc("testFinished", "name" to name)
    }

    private fun ensureSuite(module: String, out: MutableList<String>) {
        if (currentSuite == module) return
        closeSuite(out)
        currentSuite = module
        out += tc("testSuiteStarted", "name" to module, "locationHint" to "${MoveTestLocator.PROTOCOL}://$module")
    }

    private fun closeSuite(out: MutableList<String>) {
        currentSuite?.let { out += tc("testSuiteFinished", "name" to it) }
        currentSuite = null
    }

    companion object {
        private val STRIP_ANSI = Regex("\u001B\\[[;\\d]*[A-Za-z]")
        private val RESULT_LINE = Regex("""\[\s*(PASS|FAIL|TIMEOUT)\s*]\s+(\S+)""")
        private val FAILURES_IN = Regex("""Failures in (\S+):""")
        private val FAILURE_START = Regex("""┌──\s*(\S+)\s*──""")

        fun tc(name: String, vararg attrs: Pair<String, String>): String =
            "##teamcity[$name " + attrs.joinToString(" ") { (k, v) -> "$k='${escape(v)}'" } + "]"

        private fun escape(s: String) = s.replace("|", "||").replace("'", "|'").replace("\n", "|n").replace("\r", "|r").replace("[", "|[").replace("]", "|]")
    }
}
