package ai.league.practice;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

final class PracticeExchange {
    private static final TagKey<Item> MEGA_STONES = TagKey.create(Registries.ITEM, ResourceLocation.parse("mega_showdown:mega_stone"));
    private static final TagKey<Item> Z_CRYSTALS = TagKey.create(Registries.ITEM, ResourceLocation.parse("mega_showdown:z_crystal"));
    private static final TagKey<Item> Z_RINGS = TagKey.create(Registries.ITEM, ResourceLocation.parse("mega_showdown:z_ring"));

    static List<Item> megaCatalog() {
        Class<?> stone = null;
        try { stone = Class.forName("com.github.yajatkaul.mega_showdown.item.custom.mega.MegaStone"); }
        catch (ClassNotFoundException ignored) {}
        final Class<?> stoneClass = stone;
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> (stoneClass != null && stoneClass.isInstance(item)) || item.builtInRegistryHolder().is(MEGA_STONES))
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
    }

    static List<Item> zCatalog() {
        Class<?> zCrystal = null, zRing = null;
        try { zCrystal = Class.forName("com.github.yajatkaul.mega_showdown.item.custom.z.ElementalZCrystal"); }
        catch (ClassNotFoundException ignored) {}
        try { zRing = Class.forName("com.github.yajatkaul.mega_showdown.item.custom.gimmick.ZRing"); }
        catch (ClassNotFoundException ignored) {}
        final Class<?> crystalClass = zCrystal, ringClass = zRing;
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> (crystalClass != null && crystalClass.isInstance(item))
                        || (ringClass != null && ringClass.isInstance(item))
                        || item.builtInRegistryHolder().is(Z_CRYSTALS)
                        || item.builtInRegistryHolder().is(Z_RINGS))
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
    }

    static List<Item> catalog() {
        return megaCatalog();
    }

    static boolean isExchangeBlock(BlockState state) {
        if (state.is(Blocks.BARREL)) return true;
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return key.equals("cobblemon:display_case")
                || key.equals("cobblemon:pc")
                || key.equals("mega_showdown:pedestal");
    }

    static boolean nearby(ServerPlayer player, PracticeController control, BlockSite site) {
        if (site == null || !site.equals(control.settings.exchange)) return false;
        if (!player.serverLevel().dimension().location().toString().equals(control.settings.dimension)) return false;
        if (player.distanceToSqr(site.x() + .5, site.y() + .5, site.z() + .5) > 64) return false;
        BlockState state = player.serverLevel().getBlockState(new net.minecraft.core.BlockPos(site.x(), site.y(), site.z()));
        return isExchangeBlock(state);
    }

    static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var control = LeaguePractice.controller;
            if (control == null || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer server)) return InteractionResult.PASS;
            var site = control.settings.exchange;
            var pos = hit.getBlockPos();
            if (site == null || site.x() != pos.getX() || site.y() != pos.getY() || site.z() != pos.getZ()
                    || !world.dimension().location().toString().equals(control.settings.dimension)) return InteractionResult.PASS;
            try {
                if (!control.settings.enabled || !nearby(server, control, site)) throw new IllegalArgumentException("교환소 가까이에서 이용해 주세요.");
                if (com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(server) != null) throw new IllegalArgumentException("전투를 마친 뒤 이용해 주세요.");
                if (megaCatalog().isEmpty() && zCatalog().isEmpty()) throw new IllegalStateException("판매할 교환 상품이 없습니다.");
                server.openMenu(new SimpleMenuProvider((id, inventory, user) -> new PracticeExchangeMenu(id, inventory, control, site),
                        Component.literal("리그 증표 교환소")));
            } catch (Exception error) { server.sendSystemMessage(Component.literal("[교환소] " + error.getMessage())); }
            return InteractionResult.SUCCESS;
        });
    }
}
