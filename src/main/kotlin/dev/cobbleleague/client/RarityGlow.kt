package dev.cobbleleague.client

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import dev.cobbleleague.Rarity
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import kotlin.math.cos
import kotlin.math.sin

/** A full-bright translucent ground ring, drawn locally without altering light blocks or shaders. */
object RarityGlow {
    fun register() {
        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            val client = Minecraft.getInstance()
            val world = client.level ?: return@register
            val player = client.player ?: return@register
            val consumers = context.consumers() ?: return@register
            val stack = context.matrixStack() ?: return@register
            val camera = context.camera().position
            val partial = context.tickCounter().getGameTimeDeltaPartialTick(false)
            world.getEntitiesOfClass(PokemonEntity::class.java, player.boundingBox.inflate(40.0)).asSequence()
                .mapNotNull { entity -> Rarity.fromLabels(entity.pokemon.species.labels + entity.pokemon.form.labels)?.let { entity to it } }
                .take(16).forEach { (entity, rarity) ->
                    val position = entity.getPosition(partial)
                    val radius = (entity.bbWidth * 0.8f).coerceIn(0.65f, 3.0f)
                    val rgb = rarity.rgb
                    val r = (rgb shr 16) and 255; val g = (rgb shr 8) and 255; val b = rgb and 255
                    val pulse = 0.85 + 0.15 * sin((world.gameTime + partial) * 0.09)
                    stack.pushPose()
                    stack.translate(position.x - camera.x, position.y - camera.y + 0.025, position.z - camera.z)
                    val matrix = stack.last().pose()
                    val vertices = consumers.getBuffer(RenderType.lightning())
                    // Concentric quads fade outward and remain bright at night.
                    repeat(3) { band ->
                        val inner = radius * (0.70f + band * 0.16f)
                        val outer = inner + radius * 0.16f
                        val alpha = ((110 - band * 28) * pulse).toInt()
                        repeat(48) { segment ->
                            val a = segment * Math.PI * 2 / 48; val c = (segment + 1) * Math.PI * 2 / 48
                            vertices.addVertex(matrix, (cos(a)*inner).toFloat(), 0f, (sin(a)*inner).toFloat()).setColor(r,g,b,alpha)
                            vertices.addVertex(matrix, (cos(c)*inner).toFloat(), 0f, (sin(c)*inner).toFloat()).setColor(r,g,b,alpha)
                            vertices.addVertex(matrix, (cos(c)*outer).toFloat(), 0f, (sin(c)*outer).toFloat()).setColor(r,g,b,alpha)
                            vertices.addVertex(matrix, (cos(a)*outer).toFloat(), 0f, (sin(a)*outer).toFloat()).setColor(r,g,b,alpha)
                        }
                    }
                    stack.popPose()
                }
        }
    }
}
