package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.prompt.PromptManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** Declarative slot definition for {@link SettingsGUI}. */
public interface Row {

    int slot();

    default boolean visible() {
        return true;
    }

    ItemStack render();

    void onClick(SettingsGUI host, ClickType click);

    static <S> Row toggle(int slot, Target<S> target, Lens<S, Boolean> lens, String key,
                          Material onMat, Material offMat) {
        return toggle(slot, target, lens, key, onMat, null, offMat, null, () -> true);
    }

    static <S> Row toggle(int slot, Target<S> target, Lens<S, Boolean> lens, String key,
                          Material onMat, @Nullable ModelKey onModel,
                          Material offMat, @Nullable ModelKey offModel) {
        return toggle(slot, target, lens, key, onMat, onModel, offMat, offModel, () -> true);
    }

    static <S> Row toggle(int slot, Target<S> target, Lens<S, Boolean> lens, String key,
                          Material onMat, @Nullable ModelKey onModel,
                          Material offMat, @Nullable ModelKey offModel,
                          BooleanSupplier visible) {
        return new Row() {
            @Override public int slot() { return slot; }
            @Override public boolean visible() { return visible.getAsBoolean(); }

            @Override
            public ItemStack render() {
                boolean on = Boolean.TRUE.equals(lens.get().apply(target.get()));
                Material mat = on ? onMat : offMat;
                ModelKey model = on ? onModel : offModel;
                ItemBuilder builder = ItemBuilder.from(mat)
                        .name(GuiText.component(key + (on ? ".on" : ".off")));
                List<Component> lore = GuiText.lore(key + (on ? ".on-lore" : ".off-lore"));
                if (!lore.isEmpty()) builder.lore(lore);
                if (model != null) model.applyTo(builder);
                return builder.build();
            }

            @Override
            public void onClick(SettingsGUI host, ClickType click) {
                boolean next = !Boolean.TRUE.equals(lens.get().apply(target.get()));
                target.update(s -> lens.set().apply(s, next));
                host.playConfirm();
                host.refresh();
            }
        };
    }

    static <S> Row intValue(int slot, Target<S> target, Lens<S, Integer> lens, String key,
                            Material mat, int[] cycle, int min, int max) {
        return intValue(slot, target, lens, key, mat, null, cycle, cycle[0], min, max,
                v -> Map.of("value", String.valueOf(v)), () -> true);
    }

    static <S> Row intValue(int slot, Target<S> target, Lens<S, Integer> lens, String key,
                            Material mat, int[] cycle, int min, int max,
                            Function<Integer, Map<String, String>> vars) {
        return intValue(slot, target, lens, key, mat, null, cycle, cycle[0], min, max, vars, () -> true);
    }

    static <S> Row intValue(int slot, Target<S> target, Lens<S, Integer> lens, String key,
                            Material mat, @Nullable ModelKey model,
                            int[] cycle, int fallbackNext, int min, int max,
                            Function<Integer, Map<String, String>> vars,
                            BooleanSupplier visible) {
        return new Row() {
            @Override public int slot() { return slot; }
            @Override public boolean visible() { return visible.getAsBoolean(); }

            @Override
            public ItemStack render() {
                int current = lens.get().apply(target.get());
                Map<String, String> resolved = vars.apply(current);
                ItemBuilder builder = ItemBuilder.from(mat)
                        .name(GuiText.component(key + ".name", resolved))
                        .lore(GuiText.lore(key + ".lore", resolved));
                if (model != null) model.applyTo(builder);
                return builder.build();
            }

            @Override
            public void onClick(SettingsGUI host, ClickType click) {
                if (click == ClickType.RIGHT) {
                    PromptManager.promptInt(host.player(),
                            GuiText.raw(key + ".prompt", "min", min, "max", max),
                            min, max,
                            value -> {
                                target.update(s -> lens.set().apply(s, value));
                                host.playPromptConfirm();
                                host.reopen();
                            },
                            host::reopen);
                    return;
                }
                int current = lens.get().apply(target.get());
                int next = fallbackNext;
                for (int i = 0; i < cycle.length; i++) {
                    if (cycle[i] == current) {
                        next = cycle[(i + 1) % cycle.length];
                        break;
                    }
                }
                int chosen = next;
                target.update(s -> lens.set().apply(s, chosen));
                host.playConfirm();
                host.refresh();
            }
        };
    }

    static <S> Row doubleValue(int slot, Target<S> target, Lens<S, Double> lens, String key,
                               Material mat, @Nullable ModelKey model,
                               double[] cycle, double min, double max, boolean allowDecimals,
                               Function<Double, Map<String, String>> vars) {
        return new Row() {
            @Override public int slot() { return slot; }

            @Override
            public ItemStack render() {
                double current = lens.get().apply(target.get());
                Map<String, String> resolved = vars.apply(current);
                ItemBuilder builder = ItemBuilder.from(mat)
                        .name(GuiText.component(key + ".name", resolved))
                        .lore(GuiText.lore(key + ".lore", resolved));
                if (model != null) model.applyTo(builder);
                return builder.build();
            }

            @Override
            public void onClick(SettingsGUI host, ClickType click) {
                if (click == ClickType.RIGHT) {
                    if (allowDecimals) {
                        PromptManager.promptDouble(host.player(),
                                GuiText.raw(key + ".prompt", "min", formatNum(min), "max", formatNum(max)),
                                min, max,
                                value -> {
                                    target.update(s -> lens.set().apply(s, value));
                                    host.playPromptConfirm();
                                    host.reopen();
                                },
                                host::reopen);
                    } else {
                        PromptManager.promptInt(host.player(),
                                GuiText.raw(key + ".prompt", "min", (int) min, "max", (int) max),
                                (int) min, (int) max,
                                value -> {
                                    target.update(s -> lens.set().apply(s, (double) value));
                                    host.playPromptConfirm();
                                    host.reopen();
                                },
                                host::reopen);
                    }
                    return;
                }
                double current = lens.get().apply(target.get());
                double next = cycle[0];
                for (double step : cycle) {
                    if (step > current + 1e-9) {
                        next = step;
                        break;
                    }
                }
                double chosen = next;
                target.update(s -> lens.set().apply(s, chosen));
                host.playConfirm();
                host.refresh();
            }
        };
    }

    static <S> Row choiceIntOrAll(int slot, Target<S> target, Lens<S, String> lens, String key,
                                  Material mat, List<String> cycle, int min, int max) {
        return new Row() {
            @Override public int slot() { return slot; }

            @Override
            public ItemStack render() {
                String current = lens.get().apply(target.get());
                String display = "all".equalsIgnoreCase(current) ? GuiText.raw("gui.common.all") : current;
                return ItemBuilder.from(mat)
                        .name(GuiText.component(key + ".name", "value", display))
                        .lore(GuiText.lore(key + ".lore", "value", display))
                        .build();
            }

            @Override
            public void onClick(SettingsGUI host, ClickType click) {
                if (click == ClickType.RIGHT) {
                    PromptManager.promptInt(host.player(),
                            GuiText.raw(key + ".prompt", "min", min, "max", max),
                            min, max,
                            value -> {
                                target.update(s -> lens.set().apply(s, String.valueOf(value)));
                                host.playConfirm();
                                host.reopen();
                            },
                            host::reopen);
                    return;
                }
                String current = lens.get().apply(target.get());
                String next = cycle.getFirst();
                for (int i = 0; i < cycle.size(); i++) {
                    if (cycle.get(i).equalsIgnoreCase(current)) {
                        next = cycle.get((i + 1) % cycle.size());
                        break;
                    }
                }
                String chosen = next;
                target.update(s -> lens.set().apply(s, chosen));
                host.playConfirm();
                host.refresh();
            }
        };
    }

    static Row action(int slot, Supplier<ItemStack> render, BiConsumer<SettingsGUI, ClickType> onClick) {
        return new Row() {
            @Override public int slot() { return slot; }
            @Override public ItemStack render() { return render.get(); }
            @Override public void onClick(SettingsGUI host, ClickType click) { onClick.accept(host, click); }
        };
    }

    static Row display(int slot, Supplier<ItemStack> render) {
        return action(slot, render, (host, click) -> {});
    }

    private static String formatNum(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
