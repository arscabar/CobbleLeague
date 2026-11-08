package dev.cobbleleague
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class GymTrainersTest {
    @Test fun `trainer pools are separate and randomness covers every candidate`() {
        for(kind in DungeonRoundKind.entries.filter{it!=DungeonRoundKind.PVP}){
            val pool=GymTrainers.pool(kind);assertTrue(pool.size>=3);assertEquals(pool.size,pool.map{it.name}.distinct().size)
            val random=Random(418);assertEquals(pool.toSet(),(1..100).map{GymTrainers.draw(kind,random)}.toSet())
            pool.forEach{assertNotNull(javaClass.getResource(it.resource))}
        }
        assertTrue(GymTrainers.pool(DungeonRoundKind.PVP).isEmpty())
    }
    @Test fun `ranks share ties and follow remaining points not prior wins`() {
        val ids=List(3){UUID.randomUUID()};val h=RankHistory();h.record(0,ids.associateWith{10})
        assertEquals(listOf(1,1,1),h.rounds[0].rows.map{it.rank})
        h.record(1,mapOf(ids[0] to 8,ids[1] to 10,ids[2] to 8))
        assertEquals(listOf(2,1,2),h.rounds[1].rows.map{it.rank})
        h.record(2,mapOf(ids[0] to 8,ids[1] to 6,ids[2] to 0))
        assertEquals(listOf(1,2,3),h.rounds[2].rows.map{it.rank})
    }
    @Test fun `old round snapshots remain unchanged as live points change`() {
        val id=UUID.randomUUID();val live=mutableMapOf(id to 10);val h=RankHistory();h.record(0,live);live[id]=4;h.record(1,live)
        assertEquals(10,h.rounds[0].rows.single().health);assertEquals(4,h.rounds[1].rows.single().health)
        assertThrows(IllegalArgumentException::class.java){h.record(1,live)}
    }
}
