package life;

public record Pos(long xPos, long yPos) {
    public long distance(final Pos p2) {
        return distance(p2.xPos, p2.yPos);
    }
    public long distance(final long x, final long y) {
        return Math.max((x - xPos), (y - yPos));
    }
}
