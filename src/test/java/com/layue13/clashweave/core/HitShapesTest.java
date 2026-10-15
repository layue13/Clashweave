package com.layue13.clashweave.core;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;

public class HitShapesTest {

    private static String action(String extra) {
        return "{\"bufferTicks\":4,\"namespace\":\"t\",\"actions\":[{\"id\":\"t:a\",\"startup\":2,\"active\":3,\"recovery\":6,"
            + "\"damage\":1,\"reach\":3,\"arc\":0,\"distance\":0,\"speed\":0,"
            + extra
            + "\"edges\":[]}],"
            + "\"entry\":{\"ready\":{\"LIGHT\":\"t:a\"},\"entersReady\":[\"t:a\"],\"entersSheathed\":[]}}";
    }

    @Test
    public void pointToBoxIsZeroInsideAndEuclideanOutside() {
        assertEquals(0, HitShapes.pointToBox(0.5, 0.5, 0, 0, 1, 1), 1e-9);
        assertEquals(1, HitShapes.pointToBox(2, 0.5, 0, 0, 1, 1), 1e-9);
        assertEquals(Math.sqrt(2), HitShapes.pointToBox(2, 2, 0, 0, 1, 1), 1e-9);
    }

    @Test
    public void segmentToBoxFindsTheClosestPointAlongTheLine() {
        // along +z from the origin, a box 2 to 3 ahead and 0.4 to the side
        assertEquals(0.4, HitShapes.segmentToBox(0, 0, 0, 1, 5, 0.4, 2, 1.4, 3), 1e-6);
        // the segment stops before the box: the distance is to its end
        assertEquals(1, HitShapes.segmentToBox(0, 0, 0, 1, 1, -0.5, 2, 0.5, 3), 1e-6);
        // through the box
        assertEquals(0, HitShapes.segmentToBox(0, 0, 0, 1, 5, -0.5, 2, 0.5, 3), 1e-9);
    }

    @Test
    public void aThrustReachesOutAsTheBlowGoesOn() {
        // reach 3, a 0.6 wide line; a target box 2 to 2.6 ahead
        assertFalse(HitShapes.thrustHits(0, 0, 0, 1, 3, 0.4, 0.6, -0.3, 2, 0.3, 2.6)); // line is 1.2 long
        assertTrue(HitShapes.thrustHits(0, 0, 0, 1, 3, 0.8, 0.6, -0.3, 2, 0.3, 2.6)); // 2.4 long
        // wide of the line by more than half its width
        assertFalse(HitShapes.thrustHits(0, 0, 0, 1, 3, 1, 0.6, 0.5, 2, 1.1, 2.6));
        assertTrue(HitShapes.thrustHits(0, 0, 0, 1, 3, 1, 1.2, 0.5, 2, 1.1, 2.6));
    }

    @Test
    public void aBurstIsACircleAheadOfTheAttacker() {
        // centre 4 ahead, radius 1.5: a box at 4.5 ahead is in, one at 6 ahead is out, the attacker's own spot is out
        assertTrue(HitShapes.burstHits(0, 0, 0, 1, 4, 1.5, -0.3, 4.2, 0.3, 4.8));
        assertFalse(HitShapes.burstHits(0, 0, 0, 1, 4, 1.5, -0.3, 6, 0.3, 6.6));
        assertFalse(HitShapes.burstHits(0, 0, 0, 1, 4, 1.5, -0.3, -0.3, 0.3, 0.3));
    }

    @Test
    public void theShapeIsPartOfTheDataAndIsChecked() {
        assertEquals(HitShapes.SECTOR, new ActionCatalog(new StringReader(action(""))).require("t:a").shape);
        ActionCatalog thrust = new ActionCatalog(new StringReader(action("\"shape\":\"thrust\",\"width\":0.8,")));
        assertEquals(HitShapes.THRUST, thrust.require("t:a").shape);
        assertEquals(0.8, thrust.require("t:a").width, 1e-9);
        ActionCatalog burst = new ActionCatalog(
            new StringReader(action("\"shape\":\"burst\",\"width\":2,\"offset\":3,")));
        assertEquals(3, burst.require("t:a").offset, 1e-9);
        assertNotEquals(new ActionCatalog(new StringReader(action(""))).toWireJson(), thrust.toWireJson());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anUnknownShapeIsRefused() {
        new ActionCatalog(new StringReader(action("\"shape\":\"cone\",")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBurstWithoutARadiusIsRefused() {
        new ActionCatalog(new StringReader(action("\"shape\":\"burst\",")));
    }
}
