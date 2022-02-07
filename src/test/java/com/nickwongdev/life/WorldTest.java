package com.nickwongdev.life;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {

    /**
     * Purely tests that createLife does not throw an Exception when called
     */
    @Test
    void createLife() {
        final var world = new World();
        assertNotNull(world.createLife(0, 0));
        assertNull(world.createLife(0, 0));
    }

    @Test
    void destroyLife() {
        final var world = new World();
        world.createLife(-100, -100);
        world.createLife(0, 0);
        world.createLife(0, 1);
        world.createLife(1, 0);
        world.createLife(1, 100);
        world.createLife(100, 1);


        var result = world.spatialQuery(-100, -100, -100, -100);
        assertEquals(1, result.length);
        assertEquals(-100, result[0].getX());
        assertEquals(-100, result[0].getY());

        world.destroyLife(result[0]);

        result = world.spatialQuery(-100, -100, -100, -100);
        assertEquals(0, result.length);
    }

    /**
     * Tests normal cases for Spatial Query
     */
    @Test
    void spatialQuery() {
        final var world = new World();
        world.createLife(-100, -100);
        world.createLife(0, 0);
        world.createLife(0, 1);
        world.createLife(1, 0);
        world.createLife(1, 100);
        world.createLife(100, 1);

        final var lifeResult = world.spatialQuery(-1, 1, 1, -1);
        assertEquals(3, lifeResult.length);
        assertEquals(0, lifeResult[0].getX());
        assertEquals(0, lifeResult[0].getY());
        assertEquals(0, lifeResult[1].getX());
        assertEquals(1, lifeResult[1].getY());
        assertEquals(1, lifeResult[2].getX());
        assertEquals(0, lifeResult[2].getY());

        final var biggerResult = world.spatialQuery(-1000, 1000, 1000, -1000);
        assertEquals(6, biggerResult.length);
        assertEquals(-100, biggerResult[0].getX());
        assertEquals(-100, biggerResult[0].getY());
        assertEquals(0, biggerResult[1].getX());
        assertEquals(0, biggerResult[1].getY());
        assertEquals(0, biggerResult[2].getX());
        assertEquals(1, biggerResult[2].getY());
        assertEquals(1, biggerResult[3].getX());
        assertEquals(0, biggerResult[3].getY());
        assertEquals(1, biggerResult[4].getX());
        assertEquals(100, biggerResult[4].getY());
        assertEquals(100, biggerResult[5].getX());
        assertEquals(1, biggerResult[5].getY());
    }

    @Test
    public void testSpinner() {
        final var world = new World();

        // Draw a Spinner
        world.createLife(-1, 0);
        world.createLife(0, 0);
        world.createLife(1, 0);
        world.initialize();

        while(world.getAge() < 100) {
            var resarray = world.spatialQuery(-1, 1, 1, -1);

            System.out.println("World Tick: " + world.getAge());
            for(Life life : resarray) {
                System.out.println(life.toString());
            }

            world.tick();
        }

        var resarray = world.spatialQuery(-1, 1, 1, -1);

        // Assert after 100 cycles the spinner is in the appropriate position
        assertEquals(3, resarray.length);
        assertEquals(-1, resarray[0].getY());
        assertEquals(0, resarray[0].getX());
        assertEquals(0, resarray[1].getY());
        assertEquals(0, resarray[1].getX());
        assertEquals(1, resarray[2].getY());
        assertEquals(0, resarray[2].getX());
    }
}