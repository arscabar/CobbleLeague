package ai.league.practice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class RewardStore {
    record Grant(UUID battle, UUID player, String tier, boolean delivered, boolean spent, String itemId) {
        Grant(UUID battle, UUID player, String tier, boolean delivered) {
            this(battle, player, tier, delivered, false, null);
        }
        Grant {
            Objects.requireNonNull(battle); Objects.requireNonNull(player);
            if (tier == null || !tier.matches("[a-z0-9_-]{1,40}"))
                throw new IllegalArgumentException("보상 난이도 오류");
            if (itemId != null && !itemId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                throw new IllegalArgumentException("교환 상품 ID 오류");
        }
    }
    private final Path file;
    private final Map<UUID, Grant> grants = new LinkedHashMap<>();

    RewardStore(Path file) throws IOException {
        this.file = file;
        try {
            for (Grant grant : RewardDisk.read(file)) {
                Objects.requireNonNull(grant);
                if (grants.putIfAbsent(grant.battle, grant) != null)
                    throw new IllegalArgumentException("중복 전투 보상");
            }
        } catch (RuntimeException error) { throw new IOException("보상 목록이 손상되어 지급을 중단합니다", error); }
    }

    boolean award(UUID battle, UUID player, String tier) throws IOException {
        Grant grant = new Grant(battle, player, tier, false);
        Grant prior = grants.get(battle);
        if (prior != null) {
            if (!prior.player.equals(player) || !prior.tier.equals(tier))
                throw new IllegalArgumentException("동일 전투의 보상 대상 변경");
            return false;
        }
        grants.put(battle, grant);
        try { RewardDisk.write(file, grants.values()); }
        catch (IOException error) { grants.remove(battle); throw error; }
        return true;
    }

    List<Grant> pending(UUID player) {
        return grants.values().stream().filter(g -> g.player.equals(player) && !g.delivered && !g.spent).toList();
    }

    void delivered(UUID battle) throws IOException {
        Grant prior = Objects.requireNonNull(grants.get(battle), "보상 전투 없음");
        if (prior.delivered) return;
        grants.put(battle, new Grant(prior.battle, prior.player, prior.tier, true, prior.spent, prior.itemId));
        try { RewardDisk.write(file, grants.values()); }
        catch (IOException error) { grants.put(battle, prior); throw error; }
    }

    boolean currency(UUID id, String tier) {
        Grant grant = grants.get(id);
        return grant != null && grant.delivered && !grant.spent && grant.itemId == null && grant.tier.equals(tier);
    }

    UUID exchange(UUID player, List<UUID> inputs, String tier, int cost, String outputTier, String itemId) throws IOException {
        if (inputs.size() != cost || new HashSet<>(inputs).size() != cost || cost < 1)
            throw new IllegalArgumentException("서로 다른 유효한 증표가 " + cost + "개 필요합니다.");
        for (UUID input : inputs) if (!currency(input, tier))
            throw new IllegalArgumentException("이미 사용했거나 해당 난이도가 아닌 증표입니다.");
        var prior = new LinkedHashMap<>(grants);
        UUID output = UUID.randomUUID();
        Grant reward = new Grant(output, player, outputTier, false, false, itemId);
        for (UUID input : inputs) {
            Grant g = grants.get(input);
            grants.put(input, new Grant(g.battle, g.player, g.tier, true, true, null));
        }
        grants.put(output, reward);
        try { RewardDisk.write(file, grants.values()); }
        catch (IOException | RuntimeException error) { grants.clear(); grants.putAll(prior); throw error; }
        return output;
    }
}
