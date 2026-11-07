package ai.league.practice;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;

/** Vanilla chest screen; all displayed stacks are previews, never withdrawable. */
final class PracticeExchangeMenu extends ChestMenu {
    private final SimpleContainer display;
    private final PracticeController control;
    private final BlockSite site;
    private final ServerPlayer owner;
    private final List<Item> megaCatalog;
    private final List<Item> zCatalog;
    private List<Item> activeCatalog;
    private int page;
    private boolean browsing;
    private Item selected;
    private String categoryName = "메가스톤";

    PracticeExchangeMenu(int id, Inventory inventory, PracticeController control, BlockSite site) {
        this(id, inventory, control, site, new SimpleContainer(54));
    }
    private PracticeExchangeMenu(int id, Inventory inventory, PracticeController control, BlockSite site, SimpleContainer display) {
        super(MenuType.GENERIC_9x6, id, inventory, display, 6);
        this.display = display; this.control = control; this.site = site;
        this.owner = (ServerPlayer) inventory.player;
        this.megaCatalog = PracticeExchange.megaCatalog();
        this.zCatalog = PracticeExchange.zCatalog();
        this.activeCatalog = this.megaCatalog;
        render();
    }
    @Override public boolean stillValid(Player player) {
        return player == owner && control.settings.enabled && PracticeExchange.nearby(owner, control, site);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return false; }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || slot < 0 || slot >= 54 || type != ClickType.PICKUP || button != 0) return;
        try {
            if (selected != null) {
                if (slot == 31) {
                    String item = BuiltInRegistries.ITEM.getKey(selected).toString();
                    control.rewards.exchange(owner, Tier.MASTER, 2, Tier.MASTER, item);
                    selected = null; browsing = false;
                } else if (slot == 49) selected = null;
            } else if (browsing) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < activeCatalog.size()) selected = activeCatalog.get(index);
                } else if (slot == 45 && page > 0) page--;
                else if (slot == 53 && (page + 1) * 45 < activeCatalog.size()) page++;
                else if (slot == 49) browsing = false;
            } else {
                if (slot == 20) control.rewards.exchange(owner, Tier.INTERMEDIATE, 3, Tier.ADVANCED, null);
                else if (slot == 24) control.rewards.exchange(owner, Tier.ADVANCED, 3, Tier.MASTER, null);
                else if (slot == 30) {
                    activeCatalog = megaCatalog;
                    categoryName = "메가스톤";
                    browsing = true; page = 0;
                } else if (slot == 32) {
                    activeCatalog = zCatalog;
                    categoryName = "Z크리스탈 / Z링";
                    browsing = true; page = 0;
                } else if (slot == 49) { owner.closeContainer(); return; }
            }
        } catch (Exception error) {
            owner.sendSystemMessage(Component.literal("[교환소] " + error.getMessage()));
            LeaguePractice.LOG.debug("증표 교환 거부", error);
        }
        render();
        broadcastFullState();
    }

    private void render() {
        for (int slot = 0; slot < 54; slot++) display.setItem(slot, icon(Items.GRAY_STAINED_GLASS_PANE, " "));
        if (selected != null) {
            ItemStack preview = new ItemStack(selected);
            preview.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("가격: 최고급 증표 2개"))));
            display.setItem(22, preview);
            display.setItem(31, icon(Items.EMERALD, "최고급 증표 2개로 구매 확정", "선택한 상품 1개를 받습니다."));
            display.setItem(49, icon(Items.ARROW, "취소 · 목록으로"));
        } else if (browsing) {
            for (int slot = 0; slot < 45; slot++) {
                int index = page * 45 + slot;
                if (index >= activeCatalog.size()) { display.setItem(slot, ItemStack.EMPTY); continue; }
                ItemStack preview = new ItemStack(activeCatalog.get(index));
                preview.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("최고급 증표 2개 · 클릭하여 선택"))));
                display.setItem(slot, preview);
            }
            if (page > 0) display.setItem(45, icon(Items.ARROW, "이전 페이지"));
            int totalPages = Math.max(1, (activeCatalog.size() + 44) / 45);
            display.setItem(49, icon(Items.BOOK, categoryName + " 목록", (page + 1) + " / " + totalPages + " 페이지", "클릭 시 교환소 처음으로"));
            if ((page + 1) * 45 < activeCatalog.size()) display.setItem(53, icon(Items.ARROW, "다음 페이지"));
        } else {
            display.setItem(4, icon(Items.BOOK, "보유 증표 현황",
                    "중급: " + count(Tier.INTERMEDIATE) + "개",
                    "고급: " + count(Tier.ADVANCED) + "개",
                    "최고급: " + count(Tier.MASTER) + "개"));
            display.setItem(20, icon(Items.GOLD_INGOT, "중급 3개 → 고급 증표 1개", "중급 클리어 증표를 인벤토리에 넣어 주세요."));
            display.setItem(24, icon(Items.DIAMOND, "고급 3개 → 최고급 증표 1개", "고급 클리어 증표를 인벤토리에 넣어 주세요."));
            display.setItem(30, icon(Items.NETHER_STAR, "메가스톤 선택 구매", "최고급 증표 2개 → 원하는 메가스톤 1개", "상품 " + megaCatalog.size() + "종 · 선택 후 구매 확정"));
            display.setItem(32, icon(Items.AMETHYST_SHARD, "Z기술 (크리스탈 & 링) 선택 구매", "최고급 증표 2개 → 원하는 Z크리스탈/Z링 1개", "상품 " + zCatalog.size() + "종 · 선택 후 구매 확정"));
            display.setItem(49, icon(Items.BARRIER, "닫기"));
        }
    }
    private int count(Tier tier) { return control.rewards.tickets(owner.getInventory(), tier).size(); }
    private static ItemStack icon(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore).map(Component::literal).map(c -> (Component)c).toList()));
        return stack;
    }
}
