package ai.league.practice;

import java.util.*;

public final class PracticeSettings {
    public boolean enabled = false;
    public String dimension = "minecraft:overworld";
    public SpawnSite spawn = null;
    public Map<String, BlockSite> buttons = new LinkedHashMap<>();
    public BlockSite exchange = null;

    public void validate() {
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("dimension은 올바른 차원 ID여야 합니다.");
        if (buttons == null) throw new IllegalArgumentException("buttons는 객체여야 합니다.");
        if (spawn != null) spawn.validate();
        if (exchange != null) exchange.validate();
        Set<BlockSite> sites = new HashSet<>();
        for (var entry : buttons.entrySet()) {
            Tier.fromId(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("버튼 좌표가 비어 있습니다.");
            entry.getValue().validate();
            if (!sites.add(entry.getValue())) throw new IllegalArgumentException("버튼 좌표는 중복될 수 없습니다.");
        }
        if (enabled && (spawn == null || buttons.size() != Tier.values().length))
            throw new IllegalArgumentException("활성화하려면 NPC 위치와 4개 버튼 좌표를 설정하세요.");
    }

    public boolean ready() {
        try { validate(); return enabled; }
        catch (IllegalArgumentException error) { return false; }
    }
}
