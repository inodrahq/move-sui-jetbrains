package com.inodra.movesui.resolve

import com.inodra.movesui.MoveTestBase
import com.inodra.movesui.lang.psi.*

class MoveResolveTest : MoveTestBase() {

    fun `test local let binding`() {
        configure("""
            module a::m;
            fun f() { let x = 1; let y = /*caret*/x + 1; }
        """)
        checkResolvesTo<MvBindingPat>("x")
    }

    fun `test shadowing picks nearest`() {
        configure("""
            module a::m;
            fun f() { let x = 1; let x = 2u8; /*caret*/x; }
        """)
        checkResolvesTo<MvBindingPat>("x")
        val resolved = resolveAtCaret()!!
        assertEquals("2u8", (resolved.parent as MvLetStmt).letInitializer?.expr?.text)
    }

    fun `test let does not see itself`() {
        configure("""
            module a::m;
            fun f(x: u64) { let x = /*caret*/x + 1; }
        """)
        checkResolvesTo<MvFunctionParam>("x")
    }

    fun `test function param and tuple destructuring`() {
        configure("""
            module a::m;
            fun f(v: u64) { let (a, b) = (v, 1); /*caret*/b; }
        """)
        checkResolvesTo<MvBindingPat>("b")
    }

    fun `test module function call`() {
        configure("""
            module a::m;
            fun helper(): u64 { 1 }
            fun f() { /*caret*/helper(); }
        """)
        checkResolvesTo<MvFunction>("helper")
    }

    fun `test Self path`() {
        configure("""
            module a::m;
            const E: u64 = 1;
            fun f() { abort Self::/*caret*/E }
        """)
        checkResolvesTo<MvConst>("E")
    }

    fun `test struct type and literal field`() {
        configure("""
            module a::m;
            public struct S has drop { value: u64 }
            fun f(): S { S { /*caret*/value: 1 } }
        """)
        checkResolvesTo<MvNamedField>("value")
    }

    fun `test struct in type position`() {
        configure("""
            module a::m;
            public struct S has drop { value: u64 }
            fun f(s: /*caret*/S) {}
        """)
        checkResolvesTo<MvStruct>("S")
    }

    fun `test enum variant in pattern`() {
        configure("""
            module a::m;
            public enum E { A, B(u64) }
            fun f(e: E): u64 { match (e) { E::/*caret*/B(x) => x, E::A => 0 } }
        """)
        checkResolvesTo<MvEnumVariant>("B")
    }

    fun `test match arm binding`() {
        configure("""
            module a::m;
            public enum E { A, B(u64) }
            fun f(e: E): u64 { match (e) { E::B(x) => /*caret*/x, E::A => 0 } }
        """)
        checkResolvesTo<MvBindingPat>("x")
    }

    fun `test type param`() {
        configure("""
            module a::m;
            fun f<T: drop>(x: /*caret*/T) {}
        """)
        checkResolvesTo<MvTypeParam>("T")
    }

    fun `test macro params`() {
        configure("""
            module a::m;
            macro fun twice(${'$'}f: |u64| -> u64, ${'$'}x: u64): u64 { ${'$'}f(/*caret*/${'$'}x) }
        """)
        checkResolvesTo<MvFunctionParam>("\$x")
    }

    fun `test label`() {
        configure("""
            module a::m;
            fun f() { 'outer: loop { loop { break /*caret*/'outer } } }
        """)
        checkResolvesTo<MvLabel>("outer")
    }

    fun `test lambda param`() {
        configure("""
            module a::m;
            fun f(v: vector<u64>) { v.do!(|e| /*caret*/e + 1); }
        """)
        checkResolvesTo<MvBindingPat>("e")
    }

    fun `test cross module via use`() {
        addFile("sources/other.move", """
            module a::other;
            public struct Thing has drop { v: u64 }
            public fun make(): Thing { Thing { v: 1 } }
        """.trimIndent())
        configure("""
            module a::m;
            use a::other::{Self, Thing};
            fun f(): Thing { other::/*caret*/make() }
        """)
        checkResolvesTo<MvFunction>("make")
    }

    fun `test imported type alias`() {
        addFile("sources/other.move", """
            module a::other;
            public struct Thing has drop { v: u64 }
        """.trimIndent())
        configure("""
            module a::m;
            use a::other::Thing as T;
            fun f(t: /*caret*/T) {}
        """)
        checkResolvesTo<MvStruct>("Thing")
    }

    fun `test fully qualified path`() {
        addFile("sources/coin.move", """
            module sui::coin;
            public struct Coin<phantom T> has key, store { id: u64 }
        """.trimIndent())
        configure("""
            module a::m;
            fun f(c: sui::coin::/*caret*/Coin<u64>) {}
        """)
        checkResolvesTo<MvStruct>("Coin")
    }

    fun `test numeric address matches named`() {
        addFile("sources/coin.move", """
            module sui::coin;
            public fun value(): u64 { 0 }
        """.trimIndent())
        configure("""
            module a::m;
            fun f(): u64 { 0x2::coin::/*caret*/value() }
        """)
        checkResolvesTo<MvFunction>("value")
    }

    fun `test implicit sui imports`() {
        addFile("sources/object.move", """
            module sui::object;
            public struct UID has store { id: u64 }
            public fun new(): UID { UID { id: 0 } }
        """.trimIndent())
        configure("""
            module a::m;
            public struct S has key { id: /*caret*/UID }
        """)
        checkResolvesTo<MvStruct>("UID")
    }

    fun `test implicit module alias`() {
        addFile("sources/object.move", """
            module sui::object;
            public struct UID has store { id: u64 }
            public fun new(): UID { UID { id: 0 } }
        """.trimIndent())
        configure("""
            module a::m;
            fun f(): u64 { object::/*caret*/new().id }
        """)
        checkResolvesTo<MvFunction>("new")
    }

    fun `test use speck navigation`() {
        addFile("sources/other.move", """
            module a::other;
            public fun make() {}
        """.trimIndent())
        configure("""
            module a::m;
            use a::/*caret*/other;
        """)
        checkResolvesTo<MvModule>("other")
    }

    fun `test method call resolves by name`() {
        configure("""
            module a::m;
            public struct S has drop { v: u64 }
            public fun bump(s: &mut S) { s.v = s.v + 1 }
            fun f(s: &mut S) { s./*caret*/bump() }
        """)
        checkResolvesTo<MvFunction>("bump")
    }

    fun `test struct pattern shorthand field`() {
        configure("""
            module a::m;
            public struct S has drop { v: u64 }
            fun f(s: S) { let S { /*caret*/v } = s; }
        """)
        checkResolvesTo<MvNamedField>("v")
    }

    fun `test unresolved`() {
        configure("""
            module a::m;
            fun f() { /*caret*/nope(); }
        """)
        checkUnresolved()
    }
}
