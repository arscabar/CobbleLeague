package dev.cobbleleague

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.core.registries.BuiltInRegistries
import java.util.concurrent.atomic.AtomicLong

/** Only Cobblemon's natural spawn action is cancelled; send-outs do not emit this event. */
object LeagueSpawnGuard {
    private const val DIMENSION = "cobbleleague:league"
    private val blocked = AtomicLong()
    private val removed = AtomicLong()
    private val trainerBlocked = AtomicLong()

    fun register() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            if (event.entity.level().dimension().location().toString() == DIMENSION) {
                event.cancel()
                blocked.incrementAndGet()
            }
        }
    }

    fun rejectTrainerSpawn(world: ServerLevel, entity: Entity): Boolean {
        if (world.dimension().location().toString() == DIMENSION && BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).namespace == "rctmod") {
            trainerBlocked.incrementAndGet(); return true
        }
        return false
    }

    fun tick(server: MinecraftServer) {
        if (server.tickCount % 100 != 0) return
        val world = server.allLevels.firstOrNull { it.dimension().location().toString() == DIMENSION } ?: return
        // Snapshot before discarding entities. Grace and protection checks preserve entities
        // still being attached to a battle, a party or a pasture during their spawn animation.
        world.allEntities.toList().filter { BuiltInRegistries.ENTITY_TYPE.getKey(it.type).namespace == "rctmod" }.forEach { it.discard() }
        world.allEntities.filterIsInstance<PokemonEntity>().toList().forEach { entity ->
            val safe = SpawnProtection(
                owned = entity.pokemon.getOwnerUUID() != null,
                stored = entity.pokemon.storeCoordinates.get() != null,
                battle = entity.battleId != null,
                tethered = entity.tethering != null,
                display = "cobbleleague_display" in entity.tags,
                busy = entity.isBusy,
                age = entity.tickCount
            )
            if (shouldRemoveLeagueWild(safe)) { entity.discard(); removed.incrementAndGet() }
        }
    }

    fun status() = "리그 자연스폰 차단 ON · 차단 ${blocked.get()}회 · 기존 야생 정리 ${removed.get()}마리 · 트레이너 차단 ${trainerBlocked.get()}회"
}
