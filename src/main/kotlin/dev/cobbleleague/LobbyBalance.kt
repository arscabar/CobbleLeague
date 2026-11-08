package dev.cobbleleague
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

data class LobbyBalance(val startingHealth:Int=15,val lossDamage:Int=1,val selectionSeconds:Int=60, val escalateFromGym:Int=4, val maxLossDamage:Int=15,
    val roundIncome:Int=1, val lossBonus:Int=1, val gymMoneyMin:Int=2, val gymMoneyMax:Int=4) {
    fun validate(){require(startingHealth in 1..100&&lossDamage in 1..startingHealth&&selectionSeconds in 15..180 && escalateFromGym in 1..100 && maxLossDamage in 1..100 && roundIncome in 0..20 && lossBonus in 0..20 && gymMoneyMin in 0..gymMoneyMax && gymMoneyMax in 0..100)}
    companion object {
        fun load():LobbyBalance {
            val file=FabricLoader.getInstance().configDir.resolve("cobbleleague-lobby.json")
            if(!Files.exists(file))return LobbyBalance().also{Files.writeString(file,LeagueMod.GSON.toJson(it))}
            return runCatching{LeagueMod.GSON.fromJson(Files.readString(file),LobbyBalance::class.java).also{it.validate()}}
                .getOrElse{LeagueMod.LOGGER.warn("Invalid lobby balance; using defaults without overwriting the file",it);LobbyBalance()}
        }
    }
}
