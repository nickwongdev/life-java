package life;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * World has Life in it.
 * <p>
 * Provides methods for
 * - Creating Life in the World
 * - Destroying Life in the World
 * - Spatial Querying for Life in the World
 * - Ticking the World
 *
 * Conway's game of life has three key areas of complexity.
 *
 * First, we have to determine if a Life can keep living. This means it must consider its direct neighbors. If there are
 * exactly two or three direct neighbors, then it can keep living. An inefficient way to accomplish this is to scan
 * all life and consider its position relative to the Life that wants to determine if it is still alive. This will
 * result in a complexity of O(n^2).
 *
 * Second, we must determine where to spawn new Life. This is very tricky as given the bounds of this application
 * if we just created a binary value to represent the entire board, the energy required to initialize the
 * 340 undecillion bits would be enough to boil the oceans of the earth.
 *
 * Third, while it may seem simple that everything happens simultaneously, it makes dealing with "happens after" cases
 * require multiple passes on the data. For example, if a Life dies before it is considered to spawn new Life then
 * this is wrong. It is also wrong that new life spawns more new life on the same tick. Additionally, overflow is a
 * concern as the system can attempt to spatially scan or create life outside the bounds of MAX_LONG / MIN_LONG
 * coordinates.
 *
 * This solution was built around the perspective of existing Life interacting with its surroundings. That is, a Life
 * must be able to look around its self and see how many neighbors are touching, and where it can partner with 2 other
 * Lives to make a new Life. To do this, the World supports efficient spatial range queries to find other Life around
 * a Life. Consider the following diagram
 *
 *      A B C D E
 *      F 0 1 2 G
 *      H 3 X 4 I
 *      J 5 6 7 K
 *      L M N O P
 *
 * In this diagram, the subject under inspection is represented with an X.
 * Near neighbors are represented with digits, the number corresponds to the index of the counter array passed in.
 * Far neighbors are represented by letters in the following diagram.
 *
 * The assertion of this model is X can only spawn life as a new near neighbor, that is in the numeric positions.
 * However, it can partner with far neighbors to do so. Additionally, X must have a certain number of near neighbors
 * in order to keep living.
 *
 * The goal of this solution was to iterate over the set of existing life only once in a thread safe manner. Each Life
 * will be considered once and a spatial query will be performed to find its neighbors and decide if it lives or dies
 * and if it makes new life, making it effectively O(n).
 *
 * Design decisions:
 * Newborns are added to the world immediately, effectively mutating the world while it is being iterated. This is
 * usually considered bad practice, but it is done in this case because Newborns are not considered in decisions about
 * creating new Life, and it allows for some optimization in skipping the creation of life already created. Every new
 * Life created will be detected to be created by exactly three Life. Rather than implement a complex system of
 * alerting partner Life that it already created Life with a link or something similar, this allows either a check to
 * discard the new Life creation, or in rare multithreaded situations, an insert failure to the concurrent Map.
 *
 */
public class World {

    private static final long[] CANNOT_CREATE_LIFE_THERE = new long[0];

    // Navigable Sorted Map, allows making Range queries
    private final NavigableMap<Long, NavigableMap<Long, Life>> worldMap = new ConcurrentSkipListMap<>();

    private int age = 0;

    public int getAge() {
        return age;
    }

    /**
     * Thread-Safe insert of new Life into the World. Note: It is possible that two concurrently processing Life nodes
     * might try to insert the same new Life node for the same location. Life is created as part of the atomic insert
     * operation to avoid unnecessary object creation during processing if there is a collision.
     *
     * @param xPos The x position for new life to be created
     * @param yPos The y position for new life to be created
     * @return the new Life created or null if there was already life there or life could not be created there (oob)
     */
    public Life createLife(final long xPos, final long yPos) {
        final AtomicReference<Life> lifeRetVal = new AtomicReference<>();
        worldMap.compute(xPos, (x, v) -> {
            var yMap = v;
            if (yMap == null) {
                yMap = new ConcurrentSkipListMap<>();
            }
            final var life = new Life(xPos, yPos);
            final var oldLife = yMap.putIfAbsent(yPos, life);
            if (oldLife == null) {
                lifeRetVal.set(life);
            }
            return yMap;
        });
        return lifeRetVal.get();
    }

    /**
     * Thread-Safe removal of items from the World
     *
     * @param life The Life to remove from the World (usually because it is dead)
     */
    public void destroyLife(final Life life) {
        worldMap.computeIfPresent(life.getX(), (k, v) -> {
            v.remove(life.getY());
            // Clean up resources to avoid memory leak of empty maps
            if (v.isEmpty()) {
                return null;
            }
            return v;
        });
    }

    /**
     * Scans an area of the World and returns a List of Life in that area
     *
     * @param startX The upper left x position of the area
     * @param startY The upper left y position of the area
     * @param endX   The lower right x position of the area
     * @param endY   The lower right y position of the area
     * @return An array of Life found in the area specified
     */
    public Life[] spatialQuery(final long startX, final long startY, final long endX, final long endY) {
        assert (startX <= endX);
        assert (endY <= startY);

        final List<Life> foundLife = new ArrayList<>(25);

        final var xMap = worldMap.tailMap(startX, true);

        for (final Map.Entry<Long, NavigableMap<Long, Life>> xEntry : xMap.entrySet()) {
            if (xEntry.getKey() > endX) {
                break;
            }
            final var yMap = xEntry.getValue().tailMap(endY, true);
            for (final Map.Entry<Long, Life> yEntry : yMap.entrySet()) {
                if (yEntry.getKey() > startY) {
                    break;
                }
                foundLife.add(yEntry.getValue());
            }
        }
        return foundLife.toArray(new Life[0]);
    }

    /**
     * See class docs for more info on methodology.
     *
     * This method iterates through all the Life in the World and considers the Life in cells up to two away.
     *
     */
    public void tick() {
        // Storage for the Spatial Query
        final int[] possibleLifeCounters = new int[8];

        // List of Life to kill after resolution
        final List<Life> killList = new ArrayList<>();
        final List<Life> newLifeList = new ArrayList<>();

        for (NavigableMap<Long, Life> yMap : worldMap.values()) {
            for (Life life : yMap.values()) {

                // Skip newborns
                if (life.getAge() == 0) {
                    continue;
                }

                var closeNeighborCount = 0;

                // Initializing to 1 accounts for Self
                Arrays.fill(possibleLifeCounters, 1);

                // Here's what you do on every Tick
                // Storage for spatial query
                var x = life.getX();
                var y = life.getY();

                var neighbors = queryAroundPoint(x, y);

                for (Life neighbor : neighbors) {

                    // Skip self
                    if (neighbor == life) {
                        continue;
                    }

                    // Skip newborns
                    if (neighbor.getAge() == 0) {
                        continue;
                    }

                    // I decided to consider the neighbor in relation to the Life being examined
                    // This resulted in a lot of recalculation of the position
                    var relationalNeighborX = (int) (neighbor.getX() - x);
                    var relationalNeighborY = (int) (neighbor.getY() - y);

                    // Close neighbor check, increment neighbor counter
                    if (relationalNeighborX >= -1 && relationalNeighborX <= 1 && relationalNeighborY <= 1 && relationalNeighborY >= -1) {
                        closeNeighborCount++;
                    }

                    // Updates the possibleLifeCounters based on the position of the current Neighbor
                    updateCounters(relationalNeighborX, relationalNeighborY, possibleLifeCounters);
                }

                // See what life we created
                for (int i = 0; i < possibleLifeCounters.length; i++) {
                    if (possibleLifeCounters[i] == 3) {
                        var isDuplicate = false;
                        var absoluteCoordinates = relationalPositionToActualPosition(life, i);
                        if (absoluteCoordinates == CANNOT_CREATE_LIFE_THERE) {
                            continue;
                        }
                        var unadjustedX = absoluteCoordinates[0];
                        var unadjustedY = absoluteCoordinates[1];

                        // Duplicate check to make sure we aren't trying to create life on top of existing Life
                        for (final Life partner : neighbors) {
                            if (partner.getX() == unadjustedX && partner.getY() == unadjustedY) {
                                isDuplicate = true;
                                break;
                            }
                        }
                        if (!isDuplicate) {
                            var newLife = createLife(unadjustedX, unadjustedY);
                            newLifeList.add(newLife);
                        }
                    }
                }

                // If we didn't find the right number of close neighbors, the current life will no longer be alive
                if (closeNeighborCount != 2 && closeNeighborCount != 3) {
                    killList.add(life);
                }

                life.tick();
            }
        }

        for (Life life : killList) {
            destroyLife(life);
        }

        // Initialize all newborns
        for (Life life : newLifeList) {
            life.tick();
        }

        age++;
    }

    /**
     * Before we can begin, the first set of life has to live for 1 tick so that it is considered "old life" in the
     * first tick
     */
    public void initialize() {
        age = 1;
        for (NavigableMap<Long, Life> yMap : worldMap.values()) {
            for (Life life : yMap.values()) {
                life.tick();
            }
        }
    }

    public void printWorld() {
        System.out.println("#Life 1.06");
        for (NavigableMap<Long, Life> yMap : worldMap.values()) {
            for (Life life : yMap.values()) {
                System.out.println(life.getX() + " " + life.getY());
            }
        }
    }

    private Life[] queryAroundPoint(final long x, final long y) {
        var startX = x - 2;
        var startY = y + 2;
        var endX = x + 2;
        var endY = y - 2;

        // Overflow Detection dynamically resizes the search box at the edges
        if (startX > x) {
            System.err.println("Overflow detected on startX, setting to " + Long.MIN_VALUE);
            startX = Long.MIN_VALUE;
        }
        if (startY < y) {
            System.err.println("Overflow detected on startY, setting to " + Long.MAX_VALUE);
            startY = Long.MAX_VALUE;
        }
        if (endX < x) {
            System.err.println("Overflow detected on endX, setting to " + Long.MAX_VALUE);
            endX = Long.MAX_VALUE;
        }
        if (endY > y) {
            System.err.println("Overflow detected on endY, setting to " + Long.MIN_VALUE);
            endY = Long.MIN_VALUE;
        }
        return spatialQuery(startX, startY, endX, endY);
    }

    /**
     * A Life can only spawn a new Life that is a direct neighbor, but it can partner with far neighbors.
     *
     * In the following diagram, the subject under inspection is represented with an X.
     * Near neighbors are represented with digits, the number corresponds to the index of the counter array passed in.
     * Far neighbors are represented by letters in the following diagram.
     *
     * A B C D E
     * F 0 1 2 G
     * H 3 X 4 I
     * J 5 6 7 K
     * L M N O P
     *
     * This means X can spawn life at 3 with H and J.
     *
     * This method considers the relative position of a neighbor and updates the appropriate counters that could
     * spawn new Life with X. If that counter reaches exactly 3, Life will be spawned.
     *
     * @param relativeX The relative X-coordinate value of the Neighbor in relation the current inspected Life
     * @param relativeY The relative Y-coordinate value of the Neighbor in relation the current inspected Life
     * @param possibleLifeCounters The array to store counters in
     */
    private void updateCounters(final int relativeX, final int relativeY, final int[] possibleLifeCounters) {
        if (relativeY == 2) {
            switch (relativeX) {
                // A
                case -2 -> possibleLifeCounters[0]++;
                // B
                case -1 -> {
                    possibleLifeCounters[0]++;
                    possibleLifeCounters[1]++;
                }
                // C
                case 0 -> {
                    possibleLifeCounters[0]++;
                    possibleLifeCounters[1]++;
                    possibleLifeCounters[2]++;
                }
                // D
                case 1 -> {
                    possibleLifeCounters[1]++;
                    possibleLifeCounters[2]++;
                }
                // E
                case 2 -> possibleLifeCounters[2]++;
            }
        } else if (relativeY == 1) {
            switch (relativeX) {
                // F
                case -2 -> {
                    possibleLifeCounters[0]++;
                    possibleLifeCounters[3]++;
                }
                // 0
                case -1 -> {
                    possibleLifeCounters[1]++;
                    possibleLifeCounters[3]++;
                }
                // 1
                case 0 -> {
                    possibleLifeCounters[0]++;
                    possibleLifeCounters[2]++;
                    possibleLifeCounters[3]++;
                    possibleLifeCounters[4]++;
                }
                // 2
                case 1 -> {
                    possibleLifeCounters[1]++;
                    possibleLifeCounters[4]++;
                }
                // G
                case 2 -> {
                    possibleLifeCounters[2]++;
                    possibleLifeCounters[4]++;
                }
            }
        } else if (relativeY == 0) {
            switch (relativeX) {
                // H
                case -2 -> {
                    possibleLifeCounters[0]++;
                    possibleLifeCounters[3]++;
                    possibleLifeCounters[5]++;
                }
                case -1 -> { // 3
                    possibleLifeCounters[0]++;
                    possibleLifeCounters[1]++;
                    possibleLifeCounters[5]++;
                    possibleLifeCounters[6]++;
                }
                case 1 -> { // 4
                    possibleLifeCounters[1]++;
                    possibleLifeCounters[2]++;
                    possibleLifeCounters[6]++;
                    possibleLifeCounters[7]++;
                }
                case 2 -> { // I
                    possibleLifeCounters[2]++;
                    possibleLifeCounters[4]++;
                    possibleLifeCounters[7]++;
                }
            }
        } else if (relativeY == -1) {
            switch (relativeX) {
                case -2 -> { // J
                    possibleLifeCounters[3]++;
                    possibleLifeCounters[5]++;
                }
                case -1 -> { // 5
                    possibleLifeCounters[3]++;
                    possibleLifeCounters[6]++;
                }
                case 0 -> { // 6
                    possibleLifeCounters[3]++;
                    possibleLifeCounters[4]++;
                    possibleLifeCounters[5]++;
                    possibleLifeCounters[7]++;
                }
                case 1 -> { // 7
                    possibleLifeCounters[6]++;
                    possibleLifeCounters[4]++;
                }
                case 2 -> { // K
                    possibleLifeCounters[4]++;
                    possibleLifeCounters[7]++;
                }
            }
        } else if (relativeY == -2) {
            switch (relativeX) {
                // L
                case -2 -> possibleLifeCounters[5]++;
                // M
                case -1 -> {
                    possibleLifeCounters[5]++;
                    possibleLifeCounters[6]++;
                }
                // N
                case 0 -> {
                    possibleLifeCounters[5]++;
                    possibleLifeCounters[6]++;
                    possibleLifeCounters[7]++;
                }
                // O
                case 1 -> {
                    possibleLifeCounters[6]++;
                    possibleLifeCounters[7]++;
                }
                // P
                case 2 -> possibleLifeCounters[7]++;
            }
        }
    }

    /**
     * Storage for counters is as array with indexes to represent close neighbors used as follows:
     * 0 1 2
     * 3 X 4
     * 5 6 7
     *
     * X = the current Life being examined
     * Number = The index of the array that represents counters around X
     *
     * During an overflow, the method will throw an exception
     *
     * @param curLife currentLife that all relational positions revolve around
     * @param index the index in the storage counter array
     * @return the absolute coordinates of the index in the World
     */
    private long[] relationalPositionToActualPosition(final Life curLife, final int index) {
        var curX = curLife.getX();
        var curY = curLife.getY();
        var unadjustedX = curLife.getX();
        var unadjustedY = curLife.getY();
        var xAdj = Operation.NONE;
        var yAdj = Operation.NONE;
        switch (index) {
            case 0 -> {
                unadjustedX--;
                unadjustedY++;
                xAdj = Operation.SUB;
                yAdj = Operation.ADD;
            }
            case 1 -> {
                unadjustedY++;
                yAdj = Operation.ADD;
            }
            case 2 -> {
                unadjustedX++;
                unadjustedY++;
                xAdj = Operation.ADD;
                yAdj = Operation.ADD;
            }
            case 3 -> {
                unadjustedX--;
                xAdj = Operation.SUB;
            }
            case 4 -> {
                unadjustedX++;
                xAdj = Operation.ADD;
            }
            case 5 -> {
                unadjustedX--;
                unadjustedY--;
                xAdj = Operation.SUB;
                yAdj = Operation.SUB;
            }
            case 6 -> {
                unadjustedY--;
                yAdj = Operation.SUB;
            }
            case 7 -> {
                unadjustedX++;
                unadjustedY--;
                xAdj = Operation.ADD;
                yAdj = Operation.SUB;
            }
        }

        // Overflow Detection
        if (
                (xAdj == Operation.ADD && unadjustedX < curX) ||
                (xAdj == Operation.SUB && unadjustedX > curX) ||
                (yAdj == Operation.ADD && unadjustedY < curY) ||
                (yAdj == Operation.SUB && unadjustedY > curY)
        ) {
            System.err.println("Overflow detected attempting to create life near " + curX + " " + curY);
            return CANNOT_CREATE_LIFE_THERE;
        }

        return new long[] { unadjustedX, unadjustedY };
    }

    private enum Operation {
        NONE,
        ADD,
        SUB
    }
}

