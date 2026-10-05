package com.xiaofeiwu.thief;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StealPlanTest {

    private static List<StealPlan.Slot> chest(int stacks, int count) {
        List<StealPlan.Slot> list = new ArrayList<>();
        for (int i = 0; i < stacks; i++) {
            list.add(new StealPlan.Slot(i, count, true));
        }
        return list;
    }

    @Test
    void takesNoMoreStacksThanAllowed() {
        assertEquals(3, StealPlan.plan(chest(27, 64), 3, 16, new Random(1)).size());
    }

    @Test
    void neverTakesMoreThanTheLimitFromOneStack() {
        for (StealPlan.Take t : StealPlan.plan(chest(10, 64), 5, 16, new Random(2))) {
            assertEquals(16, t.count());
        }
    }

    @Test
    void aSmallStackIsTakenWhole() {
        var takes = StealPlan.plan(List.of(new StealPlan.Slot(4, 5, true)), 3, 16, new Random(3));
        assertEquals(List.of(new StealPlan.Take(4, 5)), takes);
    }

    @Test
    void emptyAndForbiddenSlotsAreLeftAlone() {
        List<StealPlan.Slot> slots = List.of(new StealPlan.Slot(0, 0, true), new StealPlan.Slot(1, 64, false), new StealPlan.Slot(2, 10, true));
        var takes = StealPlan.plan(slots, 5, 16, new Random(4));
        assertEquals(List.of(new StealPlan.Take(2, 10)), takes);
    }

    @Test
    void anEmptyChestGivesNothing() {
        assertTrue(StealPlan.plan(chest(0, 1), 3, 16, new Random(5)).isEmpty());
        assertTrue(StealPlan.plan(List.of(new StealPlan.Slot(0, 0, true)), 3, 16, new Random(5)).isEmpty());
    }

    @Test
    void aSlotIsNeverTakenTwice() {
        Set<Integer> seen = new HashSet<>();
        for (StealPlan.Take t : StealPlan.plan(chest(27, 8), 20, 16, new Random(6))) {
            assertTrue(seen.add(t.index()));
        }
    }

    @Test
    void theChoiceIsNotAlwaysTheFirstSlots() {
        Set<Integer> first = new HashSet<>();
        for (int seed = 0; seed < 30; seed++) {
            first.add(StealPlan.plan(chest(27, 8), 1, 16, new Random(seed)).get(0).index());
        }
        assertTrue(first.size() > 5);
    }

    @Test
    void zeroStacksAllowedTakesNothing() {
        assertFalse(StealPlan.plan(chest(5, 8), 0, 16, new Random(7)).iterator().hasNext());
    }
}
