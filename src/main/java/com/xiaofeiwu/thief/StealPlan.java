package com.xiaofeiwu.thief;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Which slots of a container a thief takes from, and how much of each. Pure logic: the thief grabs a few stacks at random, never
 * more than a set number of items from one stack, and leaves alone what is not allowed or is empty.
 */
final class StealPlan {

    record Slot(int index, int count, boolean allowed) {
    }

    record Take(int index, int count) {
    }

    private StealPlan() {
    }

    static List<Take> plan(List<Slot> slots, int maxStacks, int maxPerStack, Random random) {
        List<Slot> candidates = new ArrayList<>();
        for (Slot s : slots) {
            if (s.allowed() && s.count() > 0) {
                candidates.add(s);
            }
        }
        Collections.shuffle(candidates, random);
        List<Take> takes = new ArrayList<>();
        for (Slot s : candidates) {
            if (takes.size() >= Math.max(0, maxStacks)) {
                break;
            }
            int n = Math.min(s.count(), Math.max(1, maxPerStack));
            takes.add(new Take(s.index(), n));
        }
        return takes;
    }
}
