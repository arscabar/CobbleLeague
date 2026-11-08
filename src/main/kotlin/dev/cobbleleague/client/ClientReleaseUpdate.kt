package dev.cobbleleague.client

import com.google.gson.JsonParser
import dev.cobbleleague.LeagueMod
import dev.cobbleleague.releaseIsNewer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** Checks at the title screen, including starts made with Modrinth's regular Play button. */
object ClientReleaseUpdate {
    private var checked=false
    private var pending:String?=null
    private var launching=false
    private var localValidation=false
    private val http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build()
    fun register(){ClientTickEvents.END_CLIENT_TICK.register{client->
        if(client.screen !is TitleScreen||client.level!=null||launching)return@register
        val dir=client.gameDirectory.toPath().toAbsolutePath().normalize().resolve(".cobbleleague-updater")
        if(!Files.isRegularFile(dir.resolve("client-updater.ps1"))||!Files.isRegularFile(dir.resolve("auto-update.json")))return@register
        if(!checked){
            checked=true
            val config=runCatching{JsonParser.parseString(Files.readString(dir.resolve("auto-update.json")).trimStart('\uFEFF')).asJsonObject}.getOrNull()?:return@register
            if(config.get("enabled")?.asBoolean!=true)return@register
            val repo=config.get("repository")?.asString?:"arscabar/CobbleLeague"
            if(!repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))return@register
            localValidation=config.get("validationLocalRelease")?.asBoolean==true
            CompletableFuture.supplyAsync{
                if(localValidation)selectUpdate(Files.readAllBytes(dir.resolve("validation/client-release.json")))else findUpdate(repo)
            }.whenComplete{update,error->
                if(error!=null)LeagueMod.LOGGER.info("Client release check unavailable: {}",error.message)
                else client.execute{pending=update}
            }
        }
        val version=pending?:return@register
        launching=true;pending=null
        // The external helper waits for this game process to save/close before touching JARs.
        try{
            val token=java.util.UUID.randomUUID().toString().replace("-", "")
            val arguments=mutableListOf("powershell.exe","-WindowStyle","Hidden","-NoProfile","-ExecutionPolicy","Bypass","-File",dir.resolve("client-updater.ps1").toString(),"-TargetPath",client.gameDirectory.absolutePath,"-WaitForExit","-Launch","-HandoffToken",token)
            if(localValidation)arguments.addAll(listOf("-ManifestPath",dir.resolve("validation/client-release.json").toString(),"-BundleDirectory",dir.resolve("validation/payload").toString()))
            val process=ProcessBuilder(arguments)
                .redirectErrorStream(true).redirectOutput(dir.resolve("auto-update.log").toFile()).start()
            LeagueMod.LOGGER.info("Client update handoff started: release={}, updaterPid={}",version,process.pid())
            client.setScreen(UpdateRestartScreen(version,process,dir.resolve("handoff-$token.ready")))
        }catch(e:Exception){LeagueMod.LOGGER.error("Could not start client updater",e);client.setScreen(TitleScreen())}
    }}
    private fun get(url:String,accept:String="application/vnd.github+json"):ByteArray {
        val request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent","CobbleLeague-client-updater").header("Accept",accept).GET().build()
        val response=http.send(request,HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode()==200){"HTTP ${response.statusCode()}"}
        check(response.body().size<=1_000_000){"Release manifest too large"}
        return response.body()
    }
    private fun findUpdate(repo:String):String? {
        val release=JsonParser.parseString(String(get("https://api.github.com/repos/$repo/releases/latest"),Charsets.UTF_8)).asJsonObject
        if(release.get("draft")?.asBoolean==true||release.get("prerelease")?.asBoolean==true)return null
        val asset=release.getAsJsonArray("assets").singleOrNull{it.asJsonObject.get("name")?.asString=="client-release.json"}?.asJsonObject?:return null
        val url=URI.create(asset.get("url").asString)
        check(url.scheme=="https"&&url.host=="api.github.com"&&url.path.lowercase().startsWith("/repos/${repo.lowercase()}/releases/assets/"))
        val body=get(url.toASCIIString(),"application/octet-stream")
        val digest=asset.get("digest")?.takeUnless{it.isJsonNull}?.asString
        if(digest!=null)check(digest=="sha256:"+MessageDigest.getInstance("SHA-256").digest(body).joinToString(""){"%02x".format(it)})
        return selectUpdate(body)
    }
    private fun selectUpdate(body:ByteArray):String? {
        val manifest=JsonParser.parseString(String(body,Charsets.UTF_8)).asJsonObject
        check(manifest.get("schema").asInt==1&&manifest.get("minecraft").asString=="1.21.1"&&manifest.get("cobblemon").asString=="1.7.3")
        val entries=manifest.getAsJsonArray("mods").map{it.asJsonObject}
        check(entries.size==2&&entries.map{it.get("id").asString}.toSet()==setOf("cobbleleague","cobblesynergy"))
        var newer=false
        for(entry in entries){
            check(entry.get("sha256").asString.matches(Regex("[0-9a-f]{64}")))
            val installed=FabricLoader.getInstance().getModContainer(entry.get("id").asString).orElse(null)?.metadata?.version?.friendlyString
            if(installed==null||releaseIsNewer(entry.get("version").asString,installed))newer=true
        }
        return if(newer)manifest.get("release").asString else null
    }
}

internal class UpdateRestartScreen(private val version:String,private val updater:Process,private val ready:java.nio.file.Path):Screen(Component.literal("애드온 업데이트")) {
    override fun shouldCloseOnEsc()=false
    private var ticks=0
    override fun tick(){
        ticks++
        if(ticks>=20&&updater.isAlive&&Files.isRegularFile(ready)){Minecraft.getInstance().stop();return}
        if(!updater.isAlive||ticks>400){
            if(updater.isAlive)updater.destroy()
            LeagueMod.LOGGER.error("Updater did not accept handoff; game remains open")
            Minecraft.getInstance().setScreen(TitleScreen())
        }
    }
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        g.fill(0,0,width,height,0xFF14202B.toInt())
        g.drawCenteredString(font,"애드온 $version 업데이트 후 다시 실행합니다",width/2,height/2-8,0xFFFFFF)
        g.drawCenteredString(font,"같은 Modrinth 프로필로 자동 실행됩니다",width/2,height/2+12,0xB5E6E9)
    }
}
