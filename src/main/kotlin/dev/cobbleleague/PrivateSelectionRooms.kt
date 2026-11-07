package dev.cobbleleague

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs

/** Separate sealed cells, independent of the arena's position and capacity. */
class PrivateSelectionRooms {
    private data class Placement(val pos:BlockPos,val state:BlockState)
    private val jobs=linkedMapOf<Int,ArrayDeque<Placement>>()
    private val complete=mutableSetOf<Int>()
    fun center(index:Int)=1024+index*256 to 1024
    fun contains(index:Int,x:Double,y:Double,z:Double):Boolean {
        val c=center(index);return abs(x-c.first)<=28&&abs(z-c.second)<=22&&y in 60.0..88.0
    }
    fun ready(index:Int)=index in complete
    fun request(world:ServerLevel,index:Int) {
        if(index in complete||index in jobs)return
        val c=center(index)
        if(world.getBlockState(BlockPos(c.first,64,c.second)).`is`(Blocks.SEA_LANTERN)&&world.getBlockState(BlockPos(c.first,86,c.second)).`is`(Blocks.LAPIS_BLOCK)){complete.add(index);return}
        val queue=ArrayDeque<Placement>()
        for(dx in -27..27)for(dz in -21..21){
            queue.add(Placement(BlockPos(c.first+dx,64,c.second+dz),if(dx%8==0&&dz%8==0)Blocks.SEA_LANTERN.defaultBlockState() else Blocks.SMOOTH_QUARTZ.defaultBlockState()))
            queue.add(Placement(BlockPos(c.first+dx,86,c.second+dz),if(dx%8==0&&dz%8==0)Blocks.SEA_LANTERN.defaultBlockState() else Blocks.SMOOTH_QUARTZ.defaultBlockState()))
        }
        for(y in 65..85){
            val block=if(y==65||y==66)Blocks.LIGHT_BLUE_CONCRETE else Blocks.SMOOTH_QUARTZ
            for(dx in -27..27){queue.add(Placement(BlockPos(c.first+dx,y,c.second-21),block.defaultBlockState()));queue.add(Placement(BlockPos(c.first+dx,y,c.second+21),block.defaultBlockState()))}
            for(dz in -20..20){queue.add(Placement(BlockPos(c.first-27,y,c.second+dz),block.defaultBlockState()));queue.add(Placement(BlockPos(c.first+27,y,c.second+dz),block.defaultBlockState()))}
        }
        // Commit markers are written last; an interrupted build is safe to repeat.
        queue.add(Placement(BlockPos(c.first,86,c.second),Blocks.LAPIS_BLOCK.defaultBlockState()))
        queue.add(Placement(BlockPos(c.first,64,c.second),Blocks.SEA_LANTERN.defaultBlockState()))
        jobs[index]=queue
    }
    fun tick(world:ServerLevel,budget:Int=700) {
        var left=budget
        while(left>0&&jobs.isNotEmpty()){
            val entry=jobs.entries.first();val job=entry.value
            val next=job.removeFirst();world.setBlock(next.pos,next.state,2);left--
            if(job.isEmpty()){complete.add(entry.key);jobs.remove(entry.key)}
        }
    }
}
