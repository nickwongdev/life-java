package life;

/**
 * A representation of a single Life.
 */
public class Life {

    private final Pos pos;

    // How many ticks this Life has existed
    private long age = 0;

    public Life(Pos pos) {
        this.pos = pos;
    }

    public Pos getPos() {
        return pos;
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
