package com.nickwongdev.life;

import java.util.*;

/**
 * The most straightforward implementation of the game I could manage using lots of HashMap lookups
 *
 * What I don't like about this implementation:
 * - Lots of individual lookups against the Set that are misses
 * - Duplicates all the data into another Set instance for the next Tick
 * - Doesn't gracefully handle long overflow
 * - Uses a lot of collections rather than arrays to store temp data (Eden GC should be OK since in same frame?)
 * - Doesn't have a generic API for spatial query, very specific to just this use case on the 2d plane
 * - Doesn't solve the problem that every new life generated is detected 3 times (once for every parent)
 *
 * Things I like
 * - Significantly less convoluted, easier to read
 *
 */
public class HashMapWorld {
    private Set<Pos> world = new HashSet<>();

    public void add(long x, long y) {
        world.add(new Pos(x, y));
    }

    public void tick() {
        final Set<Pos> newWorld = new HashSet<>();
        for (Pos curPos : world) {
            int closeNeighborCount = 0;
            var newLifeCounterMap = new HashMap<Pos, Integer>(8);
            for (long x = curPos.x - 1; x <= curPos.x + 1; x++) { // Initialize the newLifeCounterMap with 1 for curPos
                for (long y = curPos.y + 1; y >= curPos.y - 1; y--) {
                    if (x == curPos.x && y == curPos.y) continue;
                    final var aPos = new Pos(x, y);
                    newLifeCounterMap.put(aPos, 1);
                }
            }
            for (long x = curPos.x - 2; x <= curPos.x + 2; x++) { // Scan around current location
                for (long y = curPos.y + 2; y >= curPos.y - 2; y--) {
                    final var neighborPos = new Pos(x, y);
                    if (curPos.equals(neighborPos)) continue; // Skip Self
                    if (world.contains(neighborPos)) {
                        if (curPos.distance(neighborPos) == 1) closeNeighborCount++; // Close neighbor
                        for (long ax = x - 1; ax <= x + 1; ax++) { // For every near space around the neighbor
                            for (long ay = y + 1; ay >= y - 1; ay--) {
                                newLifeCounterMap.computeIfPresent(new Pos(ax, ay), (k, v) -> ++v); // If it's also a space around cur, +1 counter
                            }
                        }
                    }
                }
            }
            if (closeNeighborCount == 2 || closeNeighborCount == 3) newWorld.add(curPos);
            for (Map.Entry<Pos, Integer> entry : newLifeCounterMap.entrySet()) {
                if (entry.getValue() == 3) {
                    newWorld.add(entry.getKey());
                }
            }
        }
        world = newWorld;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("#Life 1.06\n");
        for (Pos pos : world) {
            sb.append(pos.x).append(" ").append(pos.y).append("\n");
        }
        return sb.toString();
    }

    private record Pos(long x, long y) {
        public long distance(final Pos aPos) {
            return Math.max(Math.abs(x - aPos.x), Math.abs(y - aPos.y));
        }
    }
}
