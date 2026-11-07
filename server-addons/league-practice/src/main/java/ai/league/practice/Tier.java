package ai.league.practice;

public enum Tier {
    BEGINNER("beginner", "초급", 2, 1),
    INTERMEDIATE("intermediate", "중급", 5, 3),
    ADVANCED("advanced", "고급", 10, 4),
    MASTER("master", "최고급", 10, 5);

    private final String id, label;
    private final int slots, skill;
    Tier(String id, String label, int slots, int skill) {
        this.id = id; this.label = label; this.slots = slots; this.skill = skill;
    }
    public String id() { return id; }
    public String label() { return label; }
    public int slots() { return slots; }
    public int skill() { return skill; }
    public static Tier fromId(String id) {
        for (Tier tier : values()) if (tier.id.equals(id)) return tier;
        throw new IllegalArgumentException("알 수 없는 난이도: " + id);
    }
}
