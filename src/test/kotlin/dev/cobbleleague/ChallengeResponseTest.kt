package dev.cobbleleague

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.*

class ChallengeResponseTest {
    @Test fun `native accept shares registered teams in both deck modes`() {
        listOf(NativeLeagueMode.PICK, NativeLeagueMode.BORROW).forEach { mode ->
        var reads = 0
        val teams = JsonArray().apply { add(JsonObject().apply { addProperty("id", "2") }) }
        val result = leagueChallengeResponse("request-1", mode, true) { reads++; teams }
        assertEquals("accept", result.text("action"))
        assertEquals("request-1", result.text("id"))
        assertSame(teams, result.getAsJsonArray("teams"))
        assertEquals(1, reads)
        }
    }
    @Test fun `dungeon accept does not read saved teams`() {
        listOf(NativeLeagueMode.DRAFT).forEach { mode ->
            val result = leagueChallengeResponse("request-1", mode, true) { error("Must not read Quick Teams") }
            assertEquals("accept", result.text("action"))
            assertFalse(result.has("teams"))
        }
    }
    @Test fun `declining never reads or shares a team list`() {
        NativeLeagueMode.entries.forEach { mode ->
            val result = leagueChallengeResponse("request-1", mode, false) { error("Must not share on decline") }
            assertEquals("decline", result.text("action"))
            assertFalse(result.has("teams"))
        }
    }
    @Test fun `empty receiver team list rejects accept before any request is sent`() {
        assertThrows(IllegalArgumentException::class.java) {
            leagueChallengeResponse("request-1", NativeLeagueMode.PICK, true) { JsonArray() }
        }
    }
    @Test fun `real native receive screen has both response callbacks and both original icon buttons`() {
        val owner = "com/cobblemon/mod/common/client/gui/interact/battleRequest/BattleConfigureGUI"
        val callbacks = mutableSetOf<String>()
        var icons = 0
        val stream = requireNotNull(javaClass.getResourceAsStream("/$owner.class"))
        stream.use(::ClassReader).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                if (name in listOf("init\$lambda\$2", "init\$lambda\$3") && access and Opcodes.ACC_STATIC != 0 &&
                    descriptor == "(L$owner;Lcom/cobblemon/mod/common/client/requests/ClientPlayerActionRequest;Lnet/minecraft/client/gui/components/Button;)V") callbacks.add(name)
                if (name != "init") return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitTypeInsn(opcode: Int, type: String) {
                        if (opcode == Opcodes.NEW && type == "com/cobblemon/mod/common/client/gui/interact/battleRequest/BattleResponseButton") icons++
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        assertEquals(2, callbacks.size)
        assertEquals(2, icons)
    }
}
