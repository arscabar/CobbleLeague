package dev.cobbleleague

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.*

/** Pin the native flag that decides whether the real left sidebar is rebound. */
class VisiblePartyContractTest {
    private fun initializationFlag(path:String,method:String):Int? {
        var found:Int?=null
        javaClass.getResourceAsStream("/$path.class")!!.use { stream ->
            ClassReader(stream).accept(object:ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a:Int,n:String,d:String,s:String?,e:Array<out String>?):MethodVisitor? {
                    if(n!=method)return null
                    var flag:Int?=null
                    return object:MethodVisitor(Opcodes.ASM9) {
                        override fun visitInsn(op:Int){if(op==Opcodes.ICONST_0)flag=0;if(op==Opcodes.ICONST_1)flag=1}
                        override fun visitMethodInsn(op:Int,owner:String,n:String,d:String,i:Boolean){
                            if(owner.endsWith("/InitializePartyPacket")&&n=="<init>"&&found==null)found=flag
                        }
                    }
                }
            },ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        return found
    }
    @Test fun `upstream sendTo initializes an auxiliary party`() {
        assertEquals(0,initializationFlag("com/cobblemon/mod/common/api/storage/party/PartyStore","sendTo"))
    }
    @Test fun `league sync explicitly initializes the player's main party`() {
        assertEquals(1,initializationFlag("dev/cobbleleague/VisiblePartySync","send"))
    }
}
