package com.xiaofeiwu.thief;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BearingTest {

    // yaw 0 faces south (+z), 90 west (-x), 180 north (-z), -90 east (+x)
    @Test
    void facingSouthWithTheTargetSouthIsStraightAhead() {
        assertEquals(0, Bearing.arrow(0, 10, 0));
    }

    @Test
    void facingSouthTheTargetToTheWestIsOnTheRight() {
        assertEquals(2, Bearing.arrow(-10, 0, 0));
    }

    @Test
    void facingSouthTheTargetToTheEastIsOnTheLeft() {
        assertEquals(6, Bearing.arrow(10, 0, 0));
    }

    @Test
    void facingSouthTheTargetToTheNorthIsBehind() {
        assertEquals(4, Bearing.arrow(0, -10, 0));
    }

    @Test
    void turningAroundChangesTheArrow() {
        assertEquals(0, Bearing.arrow(-10, 0, 90));      // facing west, target west
        assertEquals(0, Bearing.arrow(0, -10, 180));     // facing north, target north
        assertEquals(0, Bearing.arrow(10, 0, -90));      // facing east, target east
        assertEquals(4, Bearing.arrow(0, 10, 180));      // facing north, target south: behind
    }

    @Test
    void diagonalsAreBetweenAheadAndTheSide() {
        assertEquals(1, Bearing.arrow(-10, 10, 0));      // south-west while facing south: ahead and to the right
        assertEquals(7, Bearing.arrow(10, 10, 0));
    }

    @Test
    void yawsPastAFullTurnWork() {
        assertEquals(0, Bearing.arrow(0, 10, 360));
        assertEquals(0, Bearing.arrow(0, 10, -720));
    }

    @Test
    void distanceIsTheStraightLineAcrossTheGround() {
        assertEquals(5, Bearing.distance(3, 4));
        assertEquals(0, Bearing.distance(0, 0));
    }
}
