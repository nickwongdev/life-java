package life;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String HEADER_LINE = "#Life 1.06";

    public static void main(String[] args) {
        var lifeList = readInput();
        final var world = new World();

        for (long[] lifePos : lifeList) {
            world.createLife(lifePos[0], lifePos[1]);
        }
        world.initialize();

        for (int i = 0; i < 10; i++) {
            world.tick();
        }

        world.printWorld();
    }

    public static List<long[]> readInput() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return readBuffer(reader);
    }

    public static List<long[]> readString(String input) {
        return readBuffer(new BufferedReader(new StringReader(input)));
    }

    /**
     * I used this to debug since it's a pain to pipe stdin into java jars.
     */
    public static List<long[]> readBuffer(final BufferedReader reader) {
        var lifeList = new ArrayList<long[]>();
        try {
            final var firstLine = reader.readLine();
            if (!HEADER_LINE.equals(firstLine.trim())) {
                throw new IOException("Filename provided is not a Life 1.06 format (missing header)");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                var pieces = line.split(" ");
                var longArray = new long[]{Long.parseLong(pieces[0]), Long.parseLong(pieces[1])};
                lifeList.add(longArray);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lifeList;
    }
}
