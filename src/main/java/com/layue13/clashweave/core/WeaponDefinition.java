package com.layue13.clashweave.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Internal server-resolved content, not a frozen extension API. Type owns timing/geometry. */
public final class WeaponDefinition {

    public final String style, actionData;
    public final double damageMultiplier, poiseMultiplier, costMultiplier;
    public final List<String> saSlots, effectSlots;
    public final AppearanceState appearance;

    public WeaponDefinition(String style, String actionData, double damage, double poise, double cost, List<String> sa,
        List<String> effects, AppearanceState appearance) {
        this.style = AppearanceState.id(style);
        this.actionData = AppearanceState.id(actionData);
        damageMultiplier = damage;
        poiseMultiplier = poise;
        costMultiplier = cost;
        saSlots = slots(sa);
        effectSlots = slots(effects);
        this.appearance = java.util.Objects.requireNonNull(appearance);
    }

    private static List<String> slots(List<String> source) {
        if (source == null || source.size() > 4) throw new IllegalArgumentException("Slot bound");
        List<String> result = new ArrayList<>();
        for (String id : source) result.add(AppearanceState.id(id));
        return Collections.unmodifiableList(result);
    }

    public void validate(double minimum, double maximum) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum <= 0 || maximum < minimum)
            throw new IllegalArgumentException("Multiplier bounds");
        for (double v : new double[] { damageMultiplier, poiseMultiplier, costMultiplier })
            if (!Double.isFinite(v) || v < minimum || v > maximum)
                throw new IllegalArgumentException("Multiplier out of range");
    }

    public static WeaponDefinition katana() {
        return new WeaponDefinition(
            "katana",
            "katana",
            1,
            1,
            1,
            Collections.emptyList(),
            Collections.emptyList(),
            AppearanceState.DEFAULT);
    }
}
