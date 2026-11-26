package scout.core;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The leagues we have data for, and the names people use for them. Resolution is exact: no fuzzy matching. */
public final class Catalog {
    private final Map<Integer, Data.LeagueRow> byId = new HashMap<>();
    private final Map<String, Integer> aliases = new HashMap<>();

    public Catalog(List<Data.LeagueRow> leagues, Map<String, Integer> aliasTable) {
        for (Data.LeagueRow l : leagues) {
            byId.put(l.id(), l);
            aliases.put(norm(l.name()), l.id());
        }
        aliasTable.forEach((k, v) -> aliases.put(norm(k), v));
    }

    public static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9. ]", " ").replaceAll("\\s+", " ").trim();
    }

    public Data.LeagueRow league(int id) {
        return byId.get(id);
    }

    /** The league a name refers to, or null when we do not cover it. */
    public Integer resolve(String name) {
        return aliases.get(norm(name));
    }

    public Map<String, Integer> aliases() {
        return Map.copyOf(aliases);
    }

    public List<Data.LeagueRow> leagues() {
        return byId.values().stream().sorted((a, b) -> Integer.compare(a.id(), b.id())).toList();
    }
}
