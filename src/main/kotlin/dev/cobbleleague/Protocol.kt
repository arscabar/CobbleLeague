package dev.cobbleleague

import com.google.gson.JsonObject
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class LeaguePayload(val json: String) : CustomPacketPayload {
    override fun type() = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<LeaguePayload>(ResourceLocation.fromNamespaceAndPath("cobbleleague", "message"))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, LeaguePayload> = StreamCodec.of(
            // Up to 24 six-member deck previews include portrait/form/move metadata.
            { buf, value -> buf.writeUtf(value.json, 128000) },
            { buf -> LeaguePayload(buf.readUtf(128000)) }
        )
        fun register() {
            PayloadTypeRegistry.playC2S().register(TYPE, CODEC)
            PayloadTypeRegistry.playS2C().register(TYPE, CODEC)
        }
    }
}

fun JsonObject.text(key: String, default: String = ""): String =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: default
fun JsonObject.number(key: String, default: Int = 0): Int =
    runCatching { get(key)?.asInt ?: default }.getOrDefault(default)
