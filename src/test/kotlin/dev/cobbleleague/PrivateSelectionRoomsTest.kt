package dev.cobbleleague
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PrivateSelectionRoomsTest {
    @Test fun `one separated cell can be assigned to every participant`() {
        val rooms=PrivateSelectionRooms();val centers=(0..7).map{rooms.center(it)}
        assertEquals(8,centers.distinct().size)
        centers.forEachIndexed {i,c->
            assertTrue(abs(c.first)>500&&abs(c.second)>500)
            assertTrue(rooms.contains(i,c.first+0.5,65.0,c.second+0.5))
            centers.forEachIndexed{j,other->if(i!=j)assertFalse(rooms.contains(i,other.first+0.5,65.0,other.second+0.5))}
        }
    }
    @Test fun `room containment rejects escape above roof or outside walls`() {
        val r=PrivateSelectionRooms();val c=r.center(0)
        assertFalse(r.contains(0,c.first.toDouble(),100.0,c.second.toDouble()))
        assertFalse(r.contains(0,c.first+40.0,65.0,c.second.toDouble()))
    }
}
