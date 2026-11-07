package dev.cobbleleague

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/** Verify the real pinned Cobblemon bytecode, rather than a mock storage implementation. */
class StorageGuardContractTest {
    @Test fun `actual PVP builder consumes both supplied parties and original store supports full resync`() {
        var reads = 0
        reader("com/cobblemon/mod/common/battles/BattleBuilder").accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (name != "pvp1v1" || !descriptor.contains("Lkotlin/jvm/functions/Function1;")) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                        if (owner == "kotlin/jvm/functions/Function1" && name == "invoke") reads++
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        assertEquals(2, reads)
        val methods = mutableSetOf<String>()
        reader("com/cobblemon/mod/common/api/storage/party/PartyStore").accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                methods.add(name + descriptor); return null
            }
        }, ClassReader.SKIP_CODE)
        assertTrue("sendTo(Lnet/minecraft/server/level/ServerPlayer;)V" in methods)
        assertTrue("swap(II)V" in methods)
        assertTrue("toGappyList()Ljava/util/List;" in methods)
    }
    @Test fun `Mega preservation hook targets the real engine team packing entry point`() {
        var found = false
        reader("com/cobblemon/mod/common/battles/BattleRegistry").accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (name=="startShowdown" && descriptor=="(Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;)V") found=true
                return null
            }
        },ClassReader.SKIP_CODE)
        assertTrue(found)
    }
    private fun reader(path: String): ClassReader {
        val stream = requireNotNull(javaClass.getResourceAsStream("/$path.class")) { "Missing dependency class $path" }
        return stream.use(::ClassReader)
    }
    @Test fun `PC access gate exposes the signatures used by the storage lock`() {
        val methods = mutableSetOf<String>()
        reader("com/cobblemon/mod/common/api/storage/pc/link/PCLinkManager").accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                methods.add(name + descriptor); return null
            }
        }, ClassReader.SKIP_CODE)
        assertTrue(methods.any { it.startsWith("getPC(") && it.endsWith("/PCStore;") })
        assertTrue("getLink(Ljava/util/UUID;)Lcom/cobblemon/mod/common/api/storage/pc/link/PCLink;" in methods)
        assertTrue("addLink(Lcom/cobblemon/mod/common/api/storage/pc/link/PCLink;)V" in methods)
        assertTrue("addLink(Ljava/util/UUID;Lcom/cobblemon/mod/common/api/storage/pc/PCStore;Lkotlin/jvm/functions/Function1;)V" in methods)
    }
    @Test fun `every standard PC movement mutation passes through the server access gate`() {
        val prefix = "com/cobblemon/mod/common/net/serverhandling/storage/"
        val handlers = listOf("pc/MovePartyPokemonToPCHandler", "pc/MovePCPokemonToPartyHandler", "SwapPCPartyPokemonHandler",
            "pc/MovePCPokemonHandler", "pc/SwapPCPokemonHandler", "pc/SortPCBoxHandler", "party/ReleasePCPokemonHandler")
        for (handler in handlers) {
            var gated = false
            reader(prefix + handler).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (name != "handle") return null
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                            if (owner == "com/cobblemon/mod/common/api/storage/pc/link/PCLinkManager" && name == "getPC") gated = true
                        }
                    }
                }
            }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            assertTrue(gated, "$handler must pass through the protected PC link")
        }
    }
}
