package ai.league.recorder;

public final class PokemonState {
    public final String side, player, uuid, species, nickname, moves;
    public final int slot, level, startHp, startMaxHp;
    public int hp, maxHp;
    public String status = "";

    public PokemonState(String side, String player, int slot, String uuid, String species,
                        String nickname, int level, String moves, int hp, int maxHp) {
        this.side = side;
        this.player = player;
        this.slot = slot;
        this.uuid = uuid;
        this.species = species;
        this.nickname = nickname;
        this.level = level;
        this.moves = moves;
        this.hp = this.startHp = hp;
        this.maxHp = this.startMaxHp = maxHp;
    }

    public String label() { return nickname.isBlank() ? species : species + " (" + nickname + ")"; }
}
