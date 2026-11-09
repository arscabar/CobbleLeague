package dev.cobbleleague

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import com.cobblemon.mod.common.api.events.CobblemonEvents
import net.minecraft.world.InteractionResult
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

class LeagueMod : ModInitializer {
    override fun onInitialize() {
        LeaguePayload.register()
        LeagueSpawnGuard.register()
        LeagueProtection.register()
        ServerPlayNetworking.registerGlobalReceiver(LeaguePayload.TYPE) { payload, context ->
            context.server().execute {
                try {
                    val message = JsonParser.parseString(payload.json).asJsonObject
                    service?.handle(context.player(), message)
                } catch (e: Exception) {
                    context.player().sendSystemMessage(Component.literal("[리그] ${e.message ?: "요청을 처리하지 못했습니다."}"))
                    send(context.player(), JsonObject().apply {
                        addProperty("kind", "error"); addProperty("message", e.message ?: "요청을 처리하지 못했습니다.")
                    })
                    LOGGER.debug("League request rejected", e)
                }
            }
        }
        ServerLifecycleEvents.SERVER_STARTED.register { service = LeagueService(it) }
        ServerLifecycleEvents.SERVER_STOPPING.register { service?.shutdown() }
        ServerLifecycleEvents.SERVER_STOPPED.register { service = null }
        ServerTickEvents.END_SERVER_TICK.register { server -> LeagueSpawnGuard.tick(server); service?.tick() }
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (LeagueSpawnGuard.rejectTrainerSpawn(world, entity)) entity.discard()
            else if ("cobbleleague_display" in entity.tags && service?.ownsDisplay(entity.uuid) != true) entity.discard()
        }
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            if (player is ServerPlayer && service?.isWatching(player.uuid)==true || "cobbleleague_display" in entity.tags ||
                entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity && service?.isDraftPokemon(entity.pokemon) == true && !player.getItemInHand(hand).isEmpty)
                InteractionResult.FAIL else InteractionResult.PASS
        }
        AttackEntityCallback.EVENT.register { player, _, _, entity, _ ->
            if (player is ServerPlayer && service?.leagueArea(player)==true || "cobbleleague_display" in entity.tags) InteractionResult.FAIL else InteractionResult.PASS
        }
        UseBlockCallback.EVENT.register { player, world, hand, hit ->
            if(world.isClientSide || player !is ServerPlayer) InteractionResult.PASS
            else {
                if(service?.isWatching(player.uuid)==true)return@register InteractionResult.FAIL
                val control=if(hand==net.minecraft.world.InteractionHand.MAIN_HAND)service?.useRerollControl(player,hit.blockPos)else null
                control ?: if(service?.leagueArea(player)==true&&player.getItemInHand(hand).item is net.minecraft.world.item.BlockItem)InteractionResult.FAIL else InteractionResult.PASS
            }
        }
        PlayerBlockBreakEvents.BEFORE.register { _,player,_,_,_ -> player !is ServerPlayer || (service?.leagueArea(player)!=true&&service?.isWatching(player.uuid)!=true) }
        UseItemCallback.EVENT.register { player,world,hand ->
            val stack=player.getItemInHand(hand)
            if(!world.isClientSide&&player is ServerPlayer&&service?.leagueArea(player)==true&&
                (stack.item is net.minecraft.world.item.BucketItem||stack.item is net.minecraft.world.item.FlintAndSteelItem||stack.`is`(net.minecraft.world.item.Items.ENDER_PEARL)||stack.`is`(net.minecraft.world.item.Items.CHORUS_FRUIT)))
                net.minecraft.world.InteractionResultHolder.fail(stack)
            else net.minecraft.world.InteractionResultHolder.pass(stack)
        }
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe { event ->
            if (event.battle.actors.any { actor ->
                "cobbleleague_display" in ((actor as? com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor<*>)?.entity?.tags ?: emptySet()) ||
                    actor.pokemonList.any { "cobbleleague_display" in (it.entity?.tags ?: emptySet()) }
            }) {
                event.reason = Component.literal("선택방 포켓몬은 몸에 닿아서 영입하세요."); event.cancel()
            }
        }
        CobblemonEvents.HELD_ITEM_PRE.subscribe { event ->
            if (service?.isDraftPokemon(event.pokemon) == true) {
                event.cancel()
                event.pokemon.getOwnerPlayer()?.sendSystemMessage(Component.literal("[리그] 던전 도구는 상점에서 장착·해제하세요. 구매품은 던전 안에서만 유지됩니다."))
            }
        }
        CobblemonEvents.POKEMON_RELEASED_EVENT_PRE.subscribe { event ->
            if (service?.isDraftPokemon(event.pokemon) == true) event.cancel()
        }
        CobblemonEvents.TRADE_EVENT_PRE.subscribe { event ->
            if (service?.isDraftPokemon(event.tradeParticipant1Pokemon) == true || service?.isDraftPokemon(event.tradeParticipant2Pokemon) == true) event.cancel()
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> service?.recover(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> service?.disconnect(handler.player.uuid) }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, player, _ -> service?.recover(player) }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(literal("league")
                .then(literal("spawnstatus").requires { it.hasPermission(2) }.executes { it.source.sendSuccess({ Component.literal(LeagueSpawnGuard.status()) }, false); 1 })
                .then(literal("menu").executes { service?.menu(it.source.playerOrException); 1 })
                .then(literal("lobby").executes { service?.lobby(it.source.playerOrException); 1 })
                .then(literal("leave").executes { service?.leave(it.source.playerOrException); 1 })
                .then(literal("restart").executes { service?.restartBattle(it.source.playerOrException); 1 })
                .then(literal("surrender").executes { service?.surrender(it.source.playerOrException); 1 })
                .then(literal("surrendergame").executes { service?.surrenderGame(it.source.playerOrException); 1 })
                .executes { service?.menu(it.source.playerOrException); 1 })
        }
    }
    companion object {
        val LOGGER = LoggerFactory.getLogger("cobbleleague")
        val GSON = GsonBuilder().setPrettyPrinting().create()
        var service: LeagueService? = null
        fun send(player: ServerPlayer, message: JsonObject) {
            try {
                if (ServerPlayNetworking.canSend(player, LeaguePayload.TYPE))
                    ServerPlayNetworking.send(player, LeaguePayload(message.toString()))
            } catch (e: Exception) { LOGGER.warn("League packet could not be delivered to {}", player.uuid, e) }
        }
    }
}
