package me.stolyy.heroes.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keyed flat + multiplicative stat modifier stack: {@code effective = (base + sum(flat)) * product(mult)}.
 * Order-independent removal via owner keys prevents competing buffs/charms from clobbering each other.
 */
public final class ModifierStack {
    private final Map<Object, Double> flat = new LinkedHashMap<>();
    private final Map<Object, Double> mult = new LinkedHashMap<>();

    public void putFlat(Object key, double delta) {
        if (delta == 0.0) {
            flat.remove(key);
        } else {
            flat.put(key, delta);
        }
    }

    public void putMult(Object key, double factor) {
        if (factor == 1.0) {
            mult.remove(key);
        } else {
            mult.put(key, factor);
        }
    }

    public void remove(Object key) {
        flat.remove(key);
        mult.remove(key);
    }

    public void clear() {
        flat.clear();
        mult.clear();
    }

    public boolean isEmpty() {
        return flat.isEmpty() && mult.isEmpty();
    }

    public double flatSum() {
        double sum = 0.0;
        for (double delta : flat.values()) {
            sum += delta;
        }
        return sum;
    }

    public double product() {
        double prod = 1.0;
        for (double factor : mult.values()) {
            prod *= factor;
        }
        return prod;
    }

    public double evaluate(double base) {
        return (base + flatSum()) * product();
    }

    public double evaluate(double base, double min) {
        return Math.max(min, evaluate(base));
    }
}
