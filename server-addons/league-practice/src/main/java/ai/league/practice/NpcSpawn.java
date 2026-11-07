package ai.league.practice;

import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.npc.configuration.interaction.NoneNPCInteractionConfiguration;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

final class NpcSpawn {
    static NPCEntity create(ServerLevel world, SpawnSite site, Tier tier, TeamSpec team) {
        return create(world, site, tier, team, true);
    }
    static NPCEntity validateDetached(ServerLevel world, SpawnSite site, Tier tier, TeamSpec team) {
        return create(world, site, tier, team, false);
    }
    private static NPCEntity create(ServerLevel world, SpawnSite site, Tier tier, TeamSpec team, boolean tracked) {
        site.validate();
        NPCEntity npc = new NPCEntity(world);
        try {
            var kind = NPCClasses.INSTANCE.getByName("standard");
            if (kind == null) throw new IllegalStateException("standard NPC 클래스가 없습니다.");
            npc.setNpc(kind);
            npc.initialize(50);
            npc.setParty(NpcPartyFactory.create(npc, team));
            npc.setSkill(tier.skill());
            npc.setMovable(false);
            npc.setInvulnerable(true);
            npc.setPersistenceRequired();
            npc.setInteraction(new NoneNPCInteractionConfiguration());
            npc.setCustomName(Component.literal(tier.label() + " 훈련 트레이너"));
            npc.moveTo(site.x(), site.y(), site.z(), site.yaw(), 0);
            var box = npc.getBoundingBox();
            if (box.minY < world.getMinBuildHeight() || box.maxY > world.getMaxBuildHeight())
                throw new IllegalStateException("NPC 생성 위치가 월드 높이 범위 밖입니다.");
            if (!world.getWorldBorder().isWithinBounds(box))
                throw new IllegalStateException("NPC 생성 위치가 월드 경계 밖입니다.");
            for (int x = (int) Math.floor(box.minX) >> 4; x <= (int) Math.floor(box.maxX) >> 4; x++)
                for (int z = (int) Math.floor(box.minZ) >> 4; z <= (int) Math.floor(box.maxZ) >> 4; z++)
                    world.getChunk(x, z);
            if (!world.noCollision(npc)) throw new IllegalStateException("NPC 생성 위치에 벽이나 장애물이 있습니다.");
            PracticeNpcTags.mark(npc);
            if (tracked && !world.addFreshEntity(npc)) throw new IllegalStateException("임시 NPC 생성에 실패했습니다.");
            return npc;
        } catch (RuntimeException error) { npc.discard(); throw error; }
    }
}
