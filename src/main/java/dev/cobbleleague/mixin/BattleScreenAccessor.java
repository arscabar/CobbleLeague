package dev.cobbleleague.mixin;

import com.cobblemon.mod.common.client.gui.interact.battleRequest.BattleConfigureGUI;
import com.cobblemon.mod.common.client.battle.ClientBattleChallenge;
import com.cobblemon.mod.common.client.battle.ClientTeamRequest;
import net.minecraft.network.chat.MutableComponent;
import com.cobblemon.mod.common.net.messages.client.PlayerInteractOptionsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(value = BattleConfigureGUI.class, remap = false)
public interface BattleScreenAccessor {
    @Accessor("packet") PlayerInteractOptionsPacket cobbleleague$getPacket();
    @Accessor("options") static List<PlayerInteractOptionsPacket.Options> cobbleleague$getOptions() { throw new AssertionError(); }
    @Accessor("currentPage") int cobbleleague$getPage();
    @Accessor("levelRulesetOptionIndex") int cobbleleague$getLevelIndex();
    @Accessor("levelRulesetOptionIndex") void cobbleleague$setLevelIndex(int value);
    @Accessor("activeRequest") ClientBattleChallenge cobbleleague$getActiveRequest();
    @Accessor("activeTeamRequest") ClientTeamRequest cobbleleague$getActiveTeamRequest();
    @Accessor("targetName") void cobbleleague$setTargetName(MutableComponent value);
}
