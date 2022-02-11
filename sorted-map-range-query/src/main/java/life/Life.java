package life;

/**
 * A representation of a single Life.
 */
public class Life {

    // The X and Y coordinate for the location of this Life
    private final long x;
    private final long y;

    // How many ticks this Life has existed
    private long age;

    public Life(long x, long y) {
        this.x = x;
        this.y = y;
    }

    public long getX() {
        return x;
    }

    public long getY() {
        return y;
    }

    public long getAge() {
        return age;
    }

    public void tick() {
        this.age++;
    }

    @Override
    public String toString() {
        return "Life{" +
                "x=" + x +
                ", y=" + y +
                ", age=" + age +
                '}';
    }
}
