package dev.enthusia.itemshops.websync;

import dev.enthusia.itemshops.util.Pos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CanonicalMarketLayout {
    public static final int STALL_COUNT = 71;
    private final List<StallDefinition> stalls;

    public CanonicalMarketLayout(List<StallDefinition> stalls) {
        this.stalls = List.copyOf(stalls);
        validate();
    }

    public static CanonicalMarketLayout load() {
        InputStream stream = CanonicalMarketLayout.class.getClassLoader().getResourceAsStream("canonical-market-stalls.txt");
        if (stream == null) throw new IllegalStateException("Canonical Market stall mapping is missing");
        List<StallDefinition> definitions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length != 12) throw new IllegalStateException("Invalid canonical stall mapping row");
                definitions.add(new StallDefinition(p[0], p[1], integer(p[2]), integer(p[3]), integer(p[4]),
                        integer(p[5]), integer(p[6]), integer(p[7]), integer(p[8]), integer(p[9]), integer(p[10]), integer(p[11])));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read canonical Market stall mapping", e);
        }
        definitions.sort(Comparator.comparingInt(s -> Integer.parseInt(s.id().substring(5))));
        return new CanonicalMarketLayout(definitions);
    }

    private static int integer(String value) { return Integer.parseInt(value); }

    private void validate() {
        if (stalls.size() != STALL_COUNT) throw new IllegalStateException("Canonical Market mapping must contain 71 stalls");
        Set<String> ids = new HashSet<>();
        for (int i = 1; i <= STALL_COUNT; i++) ids.add("stall" + i);
        Set<String> actual = new HashSet<>();
        for (StallDefinition stall : stalls) {
            if (!actual.add(stall.id())) throw new IllegalStateException("Duplicate canonical stall ID: " + stall.id());
        }
        if (!actual.equals(ids)) throw new IllegalStateException("Canonical Market mapping IDs are incomplete");
    }

    public List<StallDefinition> stalls() { return stalls; }

    public Optional<StallDefinition> byId(String id) {
        return stalls.stream().filter(stall -> stall.id().equals(id)).findFirst();
    }

    public Assignment assign(Pos position) {
        if (position == null || !"world".equals(position.world)) return Assignment.unmapped();
        List<StallDefinition> matches = stalls.stream().filter(stall -> stall.contains(position.x, position.y, position.z)).toList();
        if (matches.size() == 1) return Assignment.matched(matches.getFirst());
        if (matches.isEmpty()) return Assignment.unmapped();
        return Assignment.ambiguous(matches.stream().map(StallDefinition::id).toList());
    }

    public record StallDefinition(String id, String buildingId, int floor, int publicX, int publicY, int publicZ,
                                  int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
        MarketDtos.Location publicLocation() { return new MarketDtos.Location("world", publicX, publicY, publicZ); }
    }

    public record Assignment(Status status, StallDefinition stall, List<String> candidates) {
        public enum Status { MATCHED, UNMAPPED, AMBIGUOUS }
        static Assignment matched(StallDefinition stall) { return new Assignment(Status.MATCHED, stall, List.of(stall.id())); }
        static Assignment unmapped() { return new Assignment(Status.UNMAPPED, null, List.of()); }
        static Assignment ambiguous(List<String> ids) { return new Assignment(Status.AMBIGUOUS, null, List.copyOf(ids)); }
    }
}
