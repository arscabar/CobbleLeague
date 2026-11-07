package ai.league.practice;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import java.util.*;

final class PracticeNpcCleanup {
    private record Pending(int entityId, String dimension, int tick) {}
    private static final List<Pending> pending = new ArrayList<>();
    static void remove(NPCEntity npc) {
        PracticeNpcTags.unmark(npc);
        if (!(npc.level() instanceof ServerLevel world)) { npc.discard(); return; }
        int id = npc.getId();
        npc.discard();
        for (ServerPlayer player : world.players()) player.connection.send(new ClientboundRemoveEntitiesPacket(id));
        pending.add(new Pending(id, world.dimension().location().toString(), world.getServer().getTickCount()+10));
    }
    static void tick(MinecraftServer server) {
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            Pending item = iterator.next();
            if (server.getTickCount() < item.tick()) continue;
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                if (player.serverLevel().dimension().location().toString().equals(item.dimension()))
                    player.connection.send(new ClientboundRemoveEntitiesPacket(item.entityId()));
            iterator.remove();
        }
    }
}
