package dev.cobbleleague

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB

/** A room-local, visible button, usable without opening any menu. */
object DungeonRerollControl {
    fun position(center:Pair<Int,Int>)=BlockPos(center.first-6,66,center.second)
    fun ensure(world:ServerLevel,center:Pair<Int,Int>,room:Int,remaining:Int,active:Boolean=true) {
        val pos=position(center)
        if(!world.getBlockState(pos.below()).`is`(Blocks.SEA_LANTERN)) world.setBlockAndUpdate(pos.below(),Blocks.SEA_LANTERN.defaultBlockState())
        if(!world.getBlockState(pos).`is`(Blocks.STONE_BUTTON)) world.setBlockAndUpdate(pos,Blocks.STONE_BUTTON.defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR).setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH))
        val tag="cobbleleague_reroll_$room"
        val nearby=world.getEntitiesOfClass(ArmorStand::class.java,AABB(pos).inflate(2.0)).filter{marker->marker.tags.any{it.startsWith("cobbleleague_reroll_")}}
        val stand=nearby.firstOrNull{!it.isRemoved} ?: ArmorStand(world,pos.x+0.5,pos.y+0.55,pos.z+0.5).also{
            it.setInvisible(true);it.setNoGravity(true);it.setInvulnerable(true);(it as dev.cobbleleague.mixin.RerollMarkerAccessor).`cobbleleague$makeMarker`(true);it.setSilent(true)
            it.addTag(tag);world.addFreshEntity(it)
        }
        nearby.filter{it!==stand}.forEach{it.discard()}
        stand.moveTo(pos.x+0.5,pos.y+0.9,pos.z+0.5,0f,0f)
        stand.customName=Component.literal(if(active)"↻ 리롤 ${remaining}회" else "↻ 리롤")
        stand.setCustomNameVisible(active)
    }
}
