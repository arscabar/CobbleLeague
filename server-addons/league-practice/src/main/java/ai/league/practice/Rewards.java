package ai.league.practice;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class Rewards {
    private static final String TICKET = "league_practice_battle";
    private final RewardStore store;
    private final Set<UUID> warned = new HashSet<>();

    Rewards(Path directory) throws IOException { store = new RewardStore(directory.resolve("rewards.json")); }

    void award(UUID battleId, UUID playerId, Tier tier) throws IOException {
        store.award(battleId, playerId, tier.id());
    }

    void claim(ServerPlayer player) throws IOException {
        var pending = store.pending(player.getUUID());
        if (pending.isEmpty()) { warned.remove(player.getUUID()); return; }
        for (var grant : pending) {
            Tier tier = Arrays.stream(Tier.values()).filter(t -> t.id().equals(grant.tier()))
                    .findFirst().orElseThrow(() -> new IOException("알 수 없는 보상 난이도: " + grant.tier()));
            Inventory inventory = player.getInventory();
            if (!contains(inventory, grant.battle())) {
                int slot = inventory.getFreeSlot();
                if (slot < 0) {
                    if (warned.add(player.getUUID())) player.sendSystemMessage(Component.literal(
                            "클리어 증표가 보관되었습니다. 인벤토리 한 칸을 비우면 자동 지급됩니다."));
                    return;
                }
                ItemStack item;
                if (grant.itemId() == null) {
                    item = new ItemStack(Items.PAPER);
                    item.set(DataComponents.CUSTOM_NAME, Component.literal(tier.label() + " 클리어 증표"));
                } else {
                    var id = ResourceLocation.parse(grant.itemId());
                    if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR)
                        throw new IOException("지급할 교환 상품이 없습니다: " + id);
                    item = new ItemStack(BuiltInRegistries.ITEM.get(id));
                }
                CompoundTag tag = new CompoundTag();
                tag.putString(TICKET, grant.battle().toString());
                item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                inventory.setItem(slot, item);
                inventory.setChanged();
                player.containerMenu.broadcastChanges();
            }
            // shortcut: player.dat와 보상 JSON은 별도 저장이라 강제종료 직전 지급의 원자성은 보장하지 않는다.
            store.delivered(grant.battle());
            warned.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(grant.itemId() == null
                    ? tier.label() + " 증표 1개를 받았습니다." : "선택한 교환 상품 1개를 받았습니다."));
        }
    }

    List<UUID> tickets(Inventory inventory, Tier tier) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(Items.PAPER)) continue;
            UUID id = ticketId(stack);
            if (id != null && store.currency(id, tier.id())) ids.add(id);
        }
        return List.copyOf(ids);
    }

    void exchange(ServerPlayer player, Tier input, int cost, Tier output, String itemId) throws IOException {
        Inventory inventory = player.getInventory();
        var ids = tickets(inventory, input);
        if (ids.size() < cost) throw new IllegalArgumentException(input.label() + " 증표 " + cost + "개가 필요합니다. 현재 " + ids.size() + "개입니다.");
        var selected = ids.subList(0, cost);
        // Spending and creating the pending output share one atomic ledger write.
        store.exchange(player.getUUID(), selected, input.id(), cost, output.id(), itemId);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            UUID id = ticketId(stack);
            if (stack.is(Items.PAPER) && id != null && selected.contains(id)) inventory.setItem(slot, ItemStack.EMPTY);
        }
        inventory.setChanged();
        claim(player);
        player.containerMenu.broadcastChanges();
    }

    private static UUID ticketId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        try { return UUID.fromString(data.copyTag().getString(TICKET)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean contains(Inventory inventory, UUID battle) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            CustomData data = inventory.getItem(i).get(DataComponents.CUSTOM_DATA);
            if (data != null && battle.toString().equals(data.copyTag().getString(TICKET))) return true;
        }
        return false;
    }
}
