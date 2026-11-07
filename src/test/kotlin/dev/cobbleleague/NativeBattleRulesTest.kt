package dev.cobbleleague

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.*

class NativeBattleRulesTest {
    @Test fun `native rule order remains first when adding modes and rebuilding repeatedly`() {
        val rules = mutableListOf(-1, 50, 100, 5)
        repeat(8) { updateNativeRuleChoices(rules, true) }
        assertEquals(listOf(-1, 50, 100, 5), rules.take(4))
        assertEquals(listOf("borrow", "pick", "draft"), rules.drop(4).map { NativeLeagueMode.fromSelector(it)?.wireName })
        assertEquals(7, rules.size)
    }
    @Test fun `unsupported format or server removes only our modes`() {
        val rules = mutableListOf(-1, 50, 100, 5, 25)
        updateNativeRuleChoices(rules, true)
        updateNativeRuleChoices(rules, false)
        assertEquals(listOf(-1, 50, 100, 5, 25), rules)
    }
    @Test fun `ordinary level rules never become custom challenges`() {
        listOf(null, -1, 5, 25, 50, 100).forEach { assertNull(NativeLeagueMode.fromSelector(it)) }
        assertEquals(3, NativeLeagueMode.entries.map { it.selectorId }.distinct().size)
        assertTrue(NativeLeagueMode.entries.all { it.selectorId < -1 })
    }
    private fun read(path: String) = requireNotNull(javaClass.getResourceAsStream("/$path.class")).use(::ClassReader)

    @Test fun `pinned Cobblemon has native arrow rules and the exact challenge callback we intercept`() {
        var callback = false
        var captionCount = 0
        var init = false
        var pageSetter = false
        val owner = "com/cobblemon/mod/common/client/gui/interact/battleRequest/BattleConfigureGUI"
        read(owner).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (name == "init\$lambda\$4" && access and Opcodes.ACC_STATIC != 0 &&
                    descriptor == "(L$owner;Lnet/minecraft/client/gui/components/Button;)V") callback = true
                if (name == "init" && descriptor == "()V") init = true
                if (name == "setCurrentPage" && descriptor == "(I)V") pageSetter = true
                if (name != "render") return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitLdcInsn(value: Any?) { if (value == "challenge.rule.anything_goes") captionCount++ }
                }
            }
        }, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)
        assertTrue(callback, "Original challenge button callback changed")
        assertEquals(1, captionCount, "Native unlimited rule caption must have one replacement point")
        assertTrue(init && pageSetter)
    }

    @Test fun `actual R interaction wheel opens the native battle configuration screen`() {
        var opensNativeScreen = false
        read("com/cobblemon/mod/common/client/gui/interact/wheel/InteractWheelGuiFactoryKt").accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor =
                object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitTypeInsn(opcode: Int, type: String) {
                        if (opcode == Opcodes.NEW && type == "com/cobblemon/mod/common/client/gui/interact/battleRequest/BattleConfigureGUI") opensNativeScreen = true
                    }
                }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        assertTrue(opensNativeScreen)
    }
}
