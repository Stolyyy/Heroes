package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;

import java.util.List;

/**
 * Data-driven Bug charm whose effects are declared as {@code modifiers:} entries in {@code bug.yml}.
 */
public class StatCharm extends Charm {
    public record ModifierEntry(StatTarget target, StatTarget.Op op, double value) {}

    private final Object key;
    private final List<ModifierEntry> entries;

    public StatCharm(Object key, List<ModifierEntry> entries) {
        this.key = key;
        this.entries = List.copyOf(entries);
    }

    @Override
    protected void onActivate(Hero hero) {
        for (ModifierEntry entry : entries) {
            entry.target().apply(hero, key, entry.op(), entry.value());
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        for (ModifierEntry entry : entries) {
            entry.target().remove(hero, key);
        }
    }
}
