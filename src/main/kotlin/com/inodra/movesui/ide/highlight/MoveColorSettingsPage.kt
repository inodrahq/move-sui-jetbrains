package com.inodra.movesui.ide.highlight

import com.inodra.movesui.MoveIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

class MoveColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Move"
    override fun getIcon() = MoveIcons.MOVE
    override fun getHighlighter() = MoveSyntaxHighlighter()
    override fun getAttributeDescriptors() = MoveColor.entries.map { it.attributesDescriptor }.toTypedArray()
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> =
        MoveColor.entries.associateBy({ it.name.lowercase() }, { it.textAttributesKey })

    override fun getDemoText() = DEMO

    companion object {
        private val DEMO = """
            /// A simple counter object.
            <attribute>#[allow(unused_variable)]</attribute>
            module <module>counter</module>::<module>counter</module>;

            use sui::<module>event</module>;

            const <constant>EInvalidValue</constant>: u64 = 0;

            public struct <struct>Counter</struct> has key, store {
                <field>id</field>: <builtin_type>UID</builtin_type>,
                <field>value</field>: u64,
            }

            public enum <enum>Status</enum> has copy, drop { <enum_variant>Active</enum_variant>, <enum_variant>Paused</enum_variant> { <field>since</field>: u64 } }

            /// Create and share a counter.
            entry fun <function>create</function>(<parameter>ctx</parameter>: &mut <builtin_type>TxContext</builtin_type>) {
                let <variable>counter</variable> = <struct>Counter</struct> { id: object::<function_call>new</function_call>(ctx), value: 0 };
                transfer::<function_call>share_object</function_call>(counter);
            }

            public fun <function>increment</function>(<parameter>counter</parameter>: &mut <struct>Counter</struct>, <parameter>by</parameter>: u64) {
                <macro_call>assert!</macro_call>(by > 0, <constant>EInvalidValue</constant>);
                counter.value = counter.value + by;
                vector[1u8, 2, 3].<method_call>do!</method_call>(|<variable>x</variable>| counter.value = counter.value + (x as u64));
                <label>'outer</label>: loop { if (counter.value > 100) break 'outer };
            }

            public macro fun <macro>twice</macro>(<macro_parameter>${'$'}f</macro_parameter>: |u64| -> u64, <macro_parameter>${'$'}x</macro_parameter>: u64): u64 { ${'$'}f(${'$'}f(${'$'}x)) }

            #[test]
            fun <function>test_address</function>() { let <variable>a</variable> = @0x2; let <variable>s</variable> = b"hello"; }
        """.trimIndent()
    }
}
