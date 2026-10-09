package com.layue13.clashweave.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Frozen candidates are sorted before side effects; attacker interruption does not remove them. */
public final class HitBatch {

    public static final class Hit {

        public final String attacker;
        public final String target;
        public final long instance;
        public final int segment;

        public Hit(String attacker, String target, long instance, int segment) {
            this.attacker = attacker;
            this.target = target;
            this.instance = instance;
            this.segment = segment;
        }
    }

    private HitBatch() {}

    public static List<Hit> ordered(List<Hit> candidates) {
        List<Hit> result = new ArrayList<>(candidates);
        result.sort(
            Comparator.comparing((Hit hit) -> hit.target)
                .thenComparing(hit -> hit.attacker)
                .thenComparingLong(hit -> hit.instance)
                .thenComparingInt(hit -> hit.segment));
        return result;
    }
}
