package ai.league.practice;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class PracticeFiles {
    private final Path root;
    public PracticeFiles(Path root) { this.root = Objects.requireNonNull(root).toAbsolutePath().normalize(); }

    public void bootstrap() throws IOException {
        PracticeJson.create(root.resolve("settings.json"), new PracticeSettings());
        for (Tier tier : Tier.values()) {
            List<TeamSpec> teams = new ArrayList<>();
            for (int i = 1; i <= tier.slots(); i++)
                teams.add(new TeamSpec(tier.id() + "-" + String.format(Locale.ROOT, "%02d", i), List.of()));
            PracticeJson.create(teamFile(tier), teams);
        }
    }

    public PracticeSettings loadSettings() throws IOException {
        PracticeSettings settings = PracticeJson.read(root.resolve("settings.json"), PracticeSettings.class);
        settings.validate();
        return settings;
    }

    public void saveSettings(PracticeSettings settings) throws IOException {
        Objects.requireNonNull(settings).validate();
        PracticeJson.save(root.resolve("settings.json"), settings);
    }

    public List<TeamSpec> loadTeams(Tier tier) throws IOException {
        List<TeamSpec> teams = PracticeJson.read(teamFile(tier), new TypeToken<List<TeamSpec>>() {}.getType());
        Set<String> ids = new HashSet<>();
        for (TeamSpec team : teams) {
            if (team == null) throw new IllegalArgumentException("팀 항목은 null일 수 없습니다.");
            team.validateShape();
            if (!ids.add(team.id())) throw new IllegalArgumentException("중복 팀 ID: " + team.id());
        }
        return List.copyOf(teams);
    }

    public List<TeamSpec> readyTeams(Tier tier) throws IOException {
        List<TeamSpec> teams = loadTeams(tier);
        if (teams.size() != tier.slots())
            throw new IllegalArgumentException(tier.label() + ": 후보 팀 " + tier.slots() + "개가 필요합니다.");
        teams.forEach(TeamSpec::validate);
        return teams;
    }

    private Path teamFile(Tier tier) { return root.resolve("teams").resolve(tier.id() + ".json"); }
}
