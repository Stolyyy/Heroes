package me.stolyy.heroes.ability;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the per-hero ability HUD each tick: hotbar durability bars, passive reference cards,
 * primary active/reload action bars, and SMASH ready/active status titles.
 */
public class CooldownManager {
    private final Map<UUID, Boolean> smashReadyState = new HashMap<>();
    private final Map<UUID, Integer> ultTitleTicks = new HashMap<>();
    // Active-window length captured at activation, so the countdown can color by remaining thirds.
    private final Map<UUID, Long> ultActiveTotalMs = new HashMap<>();

    public void tick(Hero hero, Controller controller) {
        int energy = (int) hero.getEnergy().getCurrentEnergy();

        updateAbilityVisuals(hero, hero.getPrimaryAbility(), 0, energy, controller);
        updateAbilityVisuals(hero, hero.getSecondaryAbility(), 1, energy, controller);
        updateAbilityVisuals(hero, hero.getSmashAbility(), 2, energy, controller);

        for (Map.Entry<Integer, Ability> entry : CosmeticManager.resolvePassiveSlots(hero).entrySet()) {
            updatePassiveVisuals(hero, entry.getValue(), entry.getKey(), energy, controller);
        }
    }

    private void updateAbilityVisuals(Hero hero, Ability ability, int slot, int currentEnergy, Controller controller) {
        if (ability == null) return;

        // Visuals only apply to players
        if (controller.isPlayer()) {
            ItemStack item = controller.getInventoryItem(slot);
            
            if (item != null && item.getType() == Material.STICK) {
                Ammo ammo = (ability instanceof BaseAbility base) ? base.getAmmo() : null;
                // Variant families (Bug's directional secondary) render whichever timer actually
                // gates the cast the player is aiming; every other ability returns its own cooldown.
                Cooldown display = ability.getDisplayCooldown(hero);
                boolean windingUp = ability instanceof BaseAbility base && base.isWindingUp();
                boolean ready = !windingUp && display.isReady(currentEnergy);
                float progress = windingUp ? 1.0f : display.getVisualProgress(currentEnergy);

                // 1. Durability
                ItemBuilder.applyDamage(item, computeDurabilityDamage(ammo, progress));

                // 2. Ammo Stacks
                if (ammo != null) {
                    ItemBuilder.applyAmount(item, Math.max(1, ammo.getCurrent()));
                } else {
                    ItemBuilder.applyAmount(item, 1);
                }

                // 3. Model — ready requires the cooldown AND (for ammo abilities) a charge.
                // "ready || ammo>0" let STOCK ults (Shield of Justice) show Ready at spawn
                // while their start-of-game cooldown was still running.
                if (slot > 0) {
                    String type = (slot == 1) ? "Secondary" : "Ultimate";
                    boolean showsReady = ready && (ammo == null || ammo.getCurrent() > 0);
                    ItemBuilder.applyModelData(item, hero.getName(), type, showsReady ? "Ready" : "NotReady");
                }

                // 4. Primary Action Bar — reload state wins over the (green-looking)
                // cooldown bar while a CLIP refills; otherwise cooldown + ammo count.
                if (slot == 0) {
                    if (ability.getCooldownObject().isInUse()) {
                        // Active window on a primary means the weapon isn't usable yet (Punishgers'
                        // draw after a swap). The ammo count is already full at this point, so
                        // without this branch the bar would read "30/30" next to a dead trigger.
                        sendActivePrimaryActionBar(controller, ability, ammo);
                    } else if (ammo != null && ammo.isReloading()) {
                        sendReloadActionBar(controller, ammo);
                    } else {
                        long timeRemainingMs = display.getTimeRemaining();
                        sendPrimaryActionBar(controller, progress, timeRemainingMs, ammo);
                    }
                }

                // 5. Ultimate Notification + Active Timer
                if (slot == 2) {
                    // Same gate as the item model above: a STOCK ult (Sgt) whose inter-use
                    // cooldown finished but whose stock is empty is NOT ready — announcing on
                    // the bare cooldown said "Ready" seconds after firing your only charge.
                    handleSmashNotification(controller, ready && (ammo == null || ammo.getCurrent() > 0));
                    UUID uuid = controller.getUniqueId();
                    // Charge-based ults (Bulk's multi-slam) have no timed active window: their yml
                    // carries no active_duration, so cooldown.start(0) leaves activeUntil at 0 and
                    // isInUse() stays false for the whole run. For those, a non-null status text IS
                    // the activity signal — gating on isInUse() alone rendered nothing at all.
                    String status = ability.getActiveStatusText();
                    if (ability.getCooldownObject().isInUse() || status != null) {
                        int ticks = ultTitleTicks.merge(uuid, 1, Integer::sum);
                        if (ticks == 1) {
                            // Activation lives in CHAT (titles were eating the screen mid-fight);
                            // the recurring title below carries only the countdown, as a subtitle.
                            controller.sendMessage(Component.text("You activated ", NamedTextColor.GREEN)
                                    .append(Component.text(ability.getName(), NamedTextColor.GOLD))
                                    .append(Component.text("!", NamedTextColor.GREEN)));
                            ultActiveTotalMs.put(uuid,
                                    Math.max(1L, ability.getCooldownObject().getActiveTimeRemaining()));
                        }
                        if (ticks % 20 == 1) {
                            Component subtitle;
                            if (status == null) {
                                long remaining = ability.getCooldownObject().getActiveTimeRemaining();
                                long total = ultActiveTotalMs.getOrDefault(uuid, Math.max(1L, remaining));
                                subtitle = Component.text((int) Math.ceil(remaining / 1000.0) + "s",
                                        remainingColor(remaining, total));
                            } else {
                                // Charge-based ults (Bulk) publish their own status; no timer to color.
                                subtitle = Component.text(status, NamedTextColor.WHITE);
                            }
                            controller.sendTitle(Component.empty(), subtitle, 0, 30, 5);
                        }
                    } else {
                        ultTitleTicks.remove(uuid);
                        ultActiveTotalMs.remove(uuid);
                    }
                }
            }
        }

        // Tick Ammo (applies to both players and bots)
        if (ability instanceof BaseAbility base && base.getAmmo() != null) {
            base.getAmmo().tick();
        }
    }

    // Passive reference cards (PAPER, CosmeticManager#resolvePassiveSlots) get the same
    // durability-bar cooldown treatment as the STICK ability slots, plus a 4-stage charging
    // texture (see gui_passive_charging-0..3 in the resource pack) that ramps toward the
    // animated "ready" look as the cooldown completes. Display only — passive energy/ammo/execute
    // logic lives entirely elsewhere.
    private void updatePassiveVisuals(Hero hero, Ability passive, int slot, int currentEnergy, Controller controller) {
        if (passive == null || !controller.isPlayer()) return;

        ItemStack item = controller.getInventoryItem(slot);
        if (item == null || item.getType() != Material.PAPER) return;

        Ammo ammo = (passive instanceof BaseAbility base) ? base.getAmmo() : null;
        Cooldown display = passive.getDisplayCooldown(hero);
        boolean ready = display.isReady(currentEnergy) && (ammo == null || ammo.getCurrent() > 0);
        float progress = display.getVisualProgress(currentEnergy);

        // 1. Durability — identical treatment to the STICK ability path.
        ItemBuilder.applyDamage(item, computeDurabilityDamage(ammo, progress));

        // 2. Model — ready shows the animated pulse; on cooldown, a static frame chosen by charge.
        // `progress` is fraction of cooldown REMAINING (1.0 = just used, 0.0 = ready), so charge
        // fraction is its complement. Stage 3 (most charged) uses the same source frame the ready
        // animation opens on, so the swap into "Ready" lands adjacent rather than jumping.
        String type = passive.getType().name().toLowerCase().replace('_', '-');
        if (ready) {
            ItemBuilder.applyModelData(item, "gui", "passive", type);
        } else {
            int stage = Math.min(3, (int) ((1.0f - progress) * 4));
            ItemBuilder.applyModelData(item, "gui", "passive", "charging-" + stage);
        }
    }

    // Shared by the STICK ability path and the PAPER passive path so their bars can't drift.
    private static int computeDurabilityDamage(Ammo ammo, float progress) {
        int damage = (int) (progress * 100);
        if (ammo != null && ammo.getMode() == Ammo.Mode.CLIP && ammo.getCurrent() > 0) {
            damage = (int) ((1.0f - ((float) ammo.getCurrent() / ammo.getMax())) * 100);
        } else if (ammo != null && ammo.getMode() == Ammo.Mode.STOCK && ammo.getCurrent() < ammo.getMax()) {
            // Charging a stock: the bar fills with the next charge's regen progress
            // (the short inter-use cooldown would otherwise show a blank/full bar).
            damage = (int) ((1.0 - ammo.getChargeProgress()) * 100);
        }
        return damage;
    }

    private void sendPrimaryActionBar(Controller controller, float progress, long timeRemainingMs, Ammo ammo) {
        int greenCount = (int) ((1.0f - progress) * 10);
        int redCount = 10 - greenCount;

        Component bar = Component.text("Primary: ", NamedTextColor.WHITE)
                .append(Component.text("■".repeat(greenCount), NamedTextColor.GREEN))
                .append(Component.text("■".repeat(redCount), NamedTextColor.RED));

        if (timeRemainingMs > 0) {
            double seconds = timeRemainingMs / 1000.0;
            bar = bar.append(Component.text(String.format(" (%.1fs)", seconds), NamedTextColor.RED));
        }

        if (ammo != null) {
            bar = bar.append(Component.text(" " + ammo.getCurrent() + "/" + ammo.getMax(),
                    ammoColor(ammo.getCurrent(), ammo.getMax())));
        }

        controller.sendActionBar(bar);
    }

    // Primary mid-active-phase: the trigger is dead, so the bar stays fully red and carries the
    // ability's own status text (Ability#getActiveStatusText(), e.g. "Swapping") rather than a
    // cooldown countdown that would read as ready-ish.
    private void sendActivePrimaryActionBar(Controller controller, Ability ability, Ammo ammo) {
        String status = ability.getActiveStatusText();
        if (status == null) status = "Ready in";
        double seconds = ability.getCooldownObject().getActiveTimeRemaining() / 1000.0;

        Component bar = Component.text("Primary: ", NamedTextColor.WHITE)
                .append(Component.text("■".repeat(10), NamedTextColor.RED))
                .append(Component.text(String.format(" %s (%.1fs)", status, seconds), NamedTextColor.YELLOW));

        if (ammo != null) {
            bar = bar.append(Component.text(" " + ammo.getCurrent() + "/" + ammo.getMax(),
                    ammoColor(ammo.getCurrent(), ammo.getMax())));
        }

        controller.sendActionBar(bar);
    }

    // CLIP reload: the bar fills red→green with reload progress instead of showing cooldown.
    private void sendReloadActionBar(Controller controller, Ammo ammo) {
        int doneCount = (int) (ammo.getReloadProgress() * 10);
        double seconds = ammo.getReloadRemainingMs() / 1000.0;

        Component bar = Component.text("Primary: ", NamedTextColor.WHITE)
                .append(Component.text("■".repeat(doneCount), NamedTextColor.GREEN))
                .append(Component.text("■".repeat(10 - doneCount), NamedTextColor.RED))
                .append(Component.text(String.format(" Reloading (%.1fs)", seconds), NamedTextColor.YELLOW))
                .append(Component.text(" 0/" + ammo.getMax(), NamedTextColor.RED));

        controller.sendActionBar(bar);
    }

    private static NamedTextColor ammoColor(int current, int max) {
        if (max <= 0) return NamedTextColor.GREEN;
        double fraction = current / (double) max;
        if (fraction < 1.0 / 3) return NamedTextColor.RED;
        if (fraction < 2.0 / 3) return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    private void handleSmashNotification(Controller controller, boolean currentlyReady) {
        UUID uuid = controller.getUniqueId();
        boolean previouslyReady = smashReadyState.getOrDefault(uuid, true);

        if (currentlyReady && !previouslyReady) {
            Component msg = Component.text("SMASH Ability is Ready!", NamedTextColor.GREEN);
            controller.sendMessage(msg);
            // Subtitle only, and brief — the full-size title was too much screen mid-fight.
            controller.sendTitle(Component.empty(), msg, 5, 20, 5);
        }

        smashReadyState.put(uuid, currentlyReady);
    }

    private static NamedTextColor remainingColor(long remainingMs, long totalMs) {
        double fraction = remainingMs / (double) totalMs;
        if (fraction > 2.0 / 3) return NamedTextColor.GREEN;
        if (fraction > 1.0 / 3) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    public void resetAll() {
        smashReadyState.clear();
        ultTitleTicks.clear();
        ultActiveTotalMs.clear();
    }
}