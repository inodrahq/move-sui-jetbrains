package com.inodra.movesui.run

import junit.framework.TestCase

class MoveTestOutputParserTest : TestCase() {
    fun `test parses pass and fail with details`() {
        val parser = MoveTestOutputParser()
        val output = """
            BUILDING counter
            Running Move unit tests
            [ PASS    ] counter::counter_tests::test_increment
            [ FAIL    ] counter::counter_tests::test_bad
            Test failures:

            Failures in counter::counter_tests:

            ┌── test_bad ──────
            │ error[E11001]: test failure
            │    ┌─ ./tests/counter_tests.move:12:5
            │
            └──────────────────

            Test result: FAILED. Total tests: 2; passed: 1; failed: 1
        """.trimIndent()
        val messages = output.lines().flatMap { parser.process(it) } + parser.finish()
        val kinds = messages.map { it.substringAfter("##teamcity[").substringBefore(' ') }
        assertEquals(listOf("testSuiteStarted", "testStarted", "testFinished", "testStarted", "testFailed", "testFinished", "testSuiteFinished"), kinds)
        assertTrue(messages[4].contains("message='error|[E11001|]: test failure'"))
        assertTrue(messages[4].contains("details='error|[E11001|]: test failure|n"))
        assertTrue(messages[1].contains("locationHint='move-test://counter::counter_tests::test_increment'"))
    }

    fun `test failure without details is still reported`() {
        val parser = MoveTestOutputParser()
        val messages = listOf("[ FAIL    ] a::m::t", "Test result: FAILED. Total tests: 1; passed: 0; failed: 1").flatMap { parser.process(it) } + parser.finish()
        assertTrue(messages.any { it.startsWith("##teamcity[testFailed name='t'") })
    }
}
