package dev.cobbleleague

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer

/** Protect only players in the managed dimension; Pokemon battle damage remains native. */
internal object LeagueProtection {
    fun register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            entity !is ServerPlayer || LeagueMod.service?.leagueArea(entity)!=true
        }
        ServerLivingEntityEvents.ALLOW_DEATH.register { entity, _, _ ->
            if(entity is ServerPlayer && LeagueMod.service?.leagueArea(entity)==true){
                entity.health=entity.maxHealth; LeagueMod.service?.rescueLeaguePlayer(entity); false
            }else true
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            server.playerList.players.filter{LeagueMod.service?.leagueArea(it)==true}.forEach{p->
                p.clearFire();p.fallDistance=0f
                if(p.y<40)LeagueMod.service?.rescueLeaguePlayer(p)
            }
        }
    }
}
