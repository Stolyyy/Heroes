package me.stolyy.heroes.npc;

import me.stolyy.heroes.Heroes;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Citizens2 implementation of NpcService.
 * This class is the only one that imports Citizens types.
 */
public class CitizensNpcService implements NpcService {

    private final Map<String, NPC> managedNpcs = new HashMap<>();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public NpcHandle create(String id, Location location) {
        try {
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            NPC npc = registry.createNPC(EntityType.PLAYER, id);
            npc.data().setPersistent("heroes_npc_id", id);
            // Every Heroes NPC (lobby displays, game bots, minions, practice dummies) is rebuilt
            // from config or game state on demand, so none of them belong in Citizens' saves.yml.
            // Without this a crash leaves them persisted and they come back as orphans on boot;
            // removeAll() still sweeps any that an older jar wrote.
            npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
            npc.spawn(location);
            managedNpcs.put(id, npc);
            return new NpcHandle(id, npc);
        } catch (Exception e) {
            Heroes.getInstance().getLogger().warning("Failed to create NPC: " + id + " - " + e.getMessage());
            return null;
        }
    }

    @Override
    public void remove(String id) {
        NPC npc = managedNpcs.remove(id);
        if (npc != null) {
            try {
                npc.destroy();
            } catch (Exception e) {
                Heroes.getInstance().getLogger().warning("Failed to remove NPC: " + id + " - " + e.getMessage());
            }
        }
    }

    @Override
    public void removeAll() {
        // Remove NPCs tracked in-memory
        for (String id : managedNpcs.keySet().toArray(new String[0])) {
            remove(id);
        }
        // Also remove any persisted Citizens NPCs from previous runs
        try {
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            List<NPC> toRemove = new java.util.ArrayList<>();
            for (NPC npc : registry.sorted()) {
                if (npc.data().has("heroes_npc_id")) {
                    toRemove.add(npc);
                }
            }
            for (NPC npc : toRemove) {
                npc.destroy();
            }
        } catch (Exception e) {
            Heroes.getInstance().getLogger().warning("Failed to cleanup persisted NPCs: " + e.getMessage());
        }
    }

    @Override
    public void setDisplayName(NpcHandle handle, String displayName) {
        NPC npc = getNpc(handle);
        if (npc != null) {
            npc.setName(displayName);
        }
    }

    @Override
    public void setSkin(NpcHandle handle, String skinValue, String signature) {
        NPC npc = getNpc(handle);
        if (npc != null) {
            try {
                net.citizensnpcs.trait.SkinTrait skinTrait = npc.getOrAddTrait(net.citizensnpcs.trait.SkinTrait.class);
                if (signature != null && !signature.isEmpty()) {
                    skinTrait.setSkinPersistent(handle.id(), signature, skinValue);
                } else {
                    // Treat as a player name to fetch skin from
                    skinTrait.setSkinName(skinValue);
                }
            } catch (Exception e) {
                Heroes.getInstance().getLogger().warning("Failed to set skin for NPC: " + handle.id() + " - " + e.getMessage());
            }
        }
    }

    @Override
    public void setEquipment(NpcHandle handle, ItemStack helmet, ItemStack chest,
                             ItemStack legs, ItemStack boots, ItemStack mainHand) {
        NPC npc = getNpc(handle);
        if (npc != null) {
            Equipment equipment = npc.getOrAddTrait(Equipment.class);
            if (helmet != null) equipment.set(Equipment.EquipmentSlot.HELMET, helmet);
            if (chest != null) equipment.set(Equipment.EquipmentSlot.CHESTPLATE, chest);
            if (legs != null) equipment.set(Equipment.EquipmentSlot.LEGGINGS, legs);
            if (boots != null) equipment.set(Equipment.EquipmentSlot.BOOTS, boots);
            if (mainHand != null) equipment.set(Equipment.EquipmentSlot.HAND, mainHand);
        }
    }

    @Override
    public void setShowInTab(NpcHandle handle, boolean show) {
        NPC npc = getNpc(handle);
        if (npc != null) {
            // REMOVE_FROM_TABLIST is the tab-list control. This used to set NAMEPLATE_VISIBLE —
            // which is the overhead name, a different thing entirely — so "hidden from tab" NPCs
            // were actually nameless-but-tabbed... except Citizens' global disable-tablist default
            // was doing the tab removal anyway. Callers that want the nameplate hidden too now say
            // so explicitly via setNameplateVisible.
            npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, !show);
        }
    }

    @Override
    public void setNameplateVisible(NpcHandle handle, boolean visible) {
        NPC npc = getNpc(handle);
        if (npc != null) {
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, visible);
        }
    }

    @Override
    public void setProtected(NpcHandle handle, boolean protectedFlag) {
        NPC npc = getNpc(handle);
        if (npc != null) {
            npc.setProtected(protectedFlag);
        }
    }

    @Override
    public void setTurnToPlayer(NpcHandle handle, boolean turn) {
        NPC npc = getNpc(handle);
        if (npc != null) {
            // The LookClose trait's own setter is the real switch — a "lookclose" data() key is
            // read by nothing in Citizens 2.0.35 (verified by decompile), so don't write one.
            npc.getOrAddTrait(net.citizensnpcs.trait.LookClose.class).lookClose(turn);
        }
    }

    @Override
    public void spawn(NpcHandle handle) {
        NPC npc = getNpc(handle);
        if (npc != null && !npc.isSpawned()) {
            try {
                npc.spawn(npc.getStoredLocation());
            } catch (Exception e) {
                Heroes.getInstance().getLogger().warning("Failed to spawn NPC: " + handle.id() + " - " + e.getMessage());
            }
        }
    }

    @Override
    public void despawn(NpcHandle handle) {
        NPC npc = getNpc(handle);
        if (npc != null && npc.isSpawned()) {
            try {
                npc.despawn();
            } catch (Exception e) {
                Heroes.getInstance().getLogger().warning("Failed to despawn NPC: " + handle.id() + " - " + e.getMessage());
            }
        }
    }

    @Override
    public void teleport(NpcHandle handle, Location location) {
        NPC npc = getNpc(handle);
        // NPC.teleport acts on the live entity, so an unspawned NPC cannot be teleported.
        if (npc == null || !npc.isSpawned()) {
            Heroes.getInstance().getLogger().severe("Cannot teleport NPC " + handle.id() + ": it is not spawned");
            return;
        }
        npc.teleport(location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @Override
    public void lookAt(NpcHandle handle, Location target) {
        NPC npc = getNpc(handle);
        if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
            npc.faceLocation(target);
        }
    }

    // --- Navigation ---

    @Override
    public void navigateTo(NpcHandle handle, Location target) {
        NPC npc = getNpc(handle);
        if (npc != null && npc.isSpawned()) {
            npc.getNavigator().setTarget(target);
        }
    }

    @Override
    public void navigateToEntity(NpcHandle handle, LivingEntity target, boolean aggressive) {
        NPC npc = getNpc(handle);
        if (npc != null && npc.isSpawned()) {
            npc.getNavigator().setTarget(target, aggressive);
        }
    }

    @Override
    public void setNavigationLookAt(NpcHandle handle, java.util.function.Supplier<Location> lookTarget) {
        NPC npc = getNpc(handle);
        if (npc == null) return;
        // Navigation owns server-side yaw unconditionally (faceLocation per tick loses the
        // race), so Citizens' supported kiting hook is NavigatorParameters.lookAtFunction: it
        // opens a RotationTrait PACKET session that rewrites the outbound rotation packets —
        // always wins client-side, but ONLY when ProtocolLib is installed (silent cosmetic
        // no-op otherwise). Rotation params are cloned into the session when navigation picks
        // the function up, so snap-to-target must be configured BEFORE then (default rotation
        // lerp is 40 degrees/tick — far too slow for combat).
        npc.getOrAddTrait(net.citizensnpcs.trait.RotationTrait.class)
                .getGlobalParameters().immediate(true);
        java.util.function.Function<net.citizensnpcs.api.ai.Navigator, Location> fn =
                lookTarget == null ? null : nav -> lookTarget.get();
        // setTarget clones default params into local params, so a pre-set local is discarded
        // and a default-only set misses the CURRENT navigation — set BOTH.
        npc.getNavigator().getDefaultParameters().lookAtFunction(fn);
        npc.getNavigator().getLocalParameters().lookAtFunction(fn);
    }

    @Override
    public void stopNavigation(NpcHandle handle) {
        NPC npc = getNpc(handle);
        if (npc != null && npc.isSpawned()) {
            npc.getNavigator().cancelNavigation();
        }
    }

    @Override
    public boolean isNavigating(NpcHandle handle) {
        NPC npc = getNpc(handle);
        return npc != null && npc.isSpawned() && npc.getNavigator().isNavigating();
    }

    @Override
    public void setNavigationSpeed(NpcHandle handle, float speed) {
        NPC npc = getNpc(handle);
        if (npc != null && npc.isSpawned()) {
            // DECOMPILE-VERIFIED (2.0.35, v1_21_R7 remapped): for a ground-navigating PLAYER
            // NPC, params.baseSpeed is consumed by NOTHING — the previous baseSpeed(...) call
            // was a silent no-op, which is why bots crawled. The only live knob is
            // speedModifier: Citizens' EntityMoveControl sets BOTH the forward input and the
            // speed scalar to (speedModifier x MOVEMENT_SPEED base), so the NPC moves at the
            // SQUARE of that product while a real player moves linearly at its attribute:
            //   attribute-equivalent speed = (speedModifier x A)^2
            // Inverting it makes `speed` mean "multiple of this hero's own pace" for real:
            //   (X x A)^2 = A x speed  ->  X = sqrt(speed / A)
            // (1.0 = walk parity, 1.3 = always-sprinting parity.) The move control reads the
            // BASE attribute value (modifiers are ignored), read live here; Citizens resets it
            // to 0.3 on entity re-creation — BotController.ensureCombatState re-pins it.
            float base = 0.3f; // Citizens' own player-NPC default
            if (npc.getEntity() instanceof LivingEntity living) {
                org.bukkit.attribute.AttributeInstance attr =
                        living.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                if (attr != null) base = (float) attr.getBaseValue();
            }
            npc.getNavigator().getLocalParameters().speedModifier((float) Math.sqrt(speed / base));
        }
    }

    @Override
    public LivingEntity getBukkitEntity(NpcHandle handle) {
        NPC npc = getNpc(handle);
        if (npc != null && npc.isSpawned() && npc.getEntity() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    @Override
    public boolean isNpcEntity(org.bukkit.entity.Entity entity) {
        return CitizensAPI.getNPCRegistry().isNPC(entity);
    }

    // --- Internal ---

    private NPC getNpc(NpcHandle handle) {
        if (handle == null || handle.internalNpc() == null) {
            return null;
        }
        return (NPC) handle.internalNpc();
    }
}
