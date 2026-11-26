package me.stolyy.heroes.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.RotationTrait;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Citizens2 implementation of {@link NpcService}; the only class importing Citizens types. */
public class CitizensNpcService implements NpcService {

    private final Map<String, NPC> managedNpcs = new HashMap<>();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public NpcHandle create(String id, Location location) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(EntityType.PLAYER, id);
        npc.data().setPersistent("heroes_npc_id", id);
        // Heroes rebuilds all NPCs from config or match state on demand, so never persist to Citizens saves.yml.
        npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
        npc.spawn(location);
        managedNpcs.put(id, npc);
        return new NpcHandle(id, npc);
    }

    @Override
    public void remove(String id) {
        NPC npc = managedNpcs.remove(id);
        if (npc != null) {
            npc.destroy();
        }
    }

    @Override
    public void removeAll() {
        for (String id : managedNpcs.keySet().toArray(new String[0])) {
            remove(id);
        }
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        List<NPC> toRemove = new ArrayList<>();
        for (NPC npc : registry.sorted()) {
            if (npc.data().has("heroes_npc_id")) {
                toRemove.add(npc);
            }
        }
        for (NPC npc : toRemove) {
            npc.destroy();
        }
    }

    @Override
    public void setDisplayName(NpcHandle handle, String displayName) {
        getNpc(handle).setName(displayName);
    }

    @Override
    public void setSkin(NpcHandle handle, String skinValue, String signature) {
        NPC npc = getNpc(handle);
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        if (signature != null && !signature.isEmpty()) {
            skinTrait.setSkinPersistent(handle.id(), signature, skinValue);
        } else {
            skinTrait.setSkinName(skinValue);
        }
    }

    @Override
    public void setEquipment(NpcHandle handle, ItemStack helmet, ItemStack chest,
                             ItemStack legs, ItemStack boots, ItemStack mainHand) {
        NPC npc = getNpc(handle);
        Equipment equipment = npc.getOrAddTrait(Equipment.class);
        if (helmet != null) equipment.set(Equipment.EquipmentSlot.HELMET, helmet);
        if (chest != null) equipment.set(Equipment.EquipmentSlot.CHESTPLATE, chest);
        if (legs != null) equipment.set(Equipment.EquipmentSlot.LEGGINGS, legs);
        if (boots != null) equipment.set(Equipment.EquipmentSlot.BOOTS, boots);
        if (mainHand != null) equipment.set(Equipment.EquipmentSlot.HAND, mainHand);
    }

    @Override
    public void setShowInTab(NpcHandle handle, boolean show) {
        // REMOVE_FROM_TABLIST controls tab visibility; use setNameplateVisible for the overhead nametag.
        getNpc(handle).data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, !show);
    }

    @Override
    public void setNameplateVisible(NpcHandle handle, boolean visible) {
        getNpc(handle).data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, visible);
    }

    @Override
    public void setProtected(NpcHandle handle, boolean protectedFlag) {
        getNpc(handle).setProtected(protectedFlag);
    }

    @Override
    public void setTurnToPlayer(NpcHandle handle, boolean turn) {
        getNpc(handle).getOrAddTrait(LookClose.class).lookClose(turn);
    }

    @Override
    public void spawn(NpcHandle handle) {
        NPC npc = getNpc(handle);
        if (!npc.isSpawned()) {
            npc.spawn(npc.getStoredLocation());
        }
    }

    @Override
    public void despawn(NpcHandle handle) {
        NPC npc = getNpc(handle);
        if (npc.isSpawned()) {
            npc.despawn();
        }
    }

    @Override
    public void teleport(NpcHandle handle, Location location) {
        NPC npc = getNpc(handle);
        if (!npc.isSpawned()) {
            throw new IllegalStateException("Cannot teleport NPC " + handle.id() + ": it is not spawned");
        }
        npc.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @Override
    public void lookAt(NpcHandle handle, Location target) {
        NPC npc = getNpc(handle);
        if (npc.isSpawned() && npc.getEntity() != null) {
            npc.faceLocation(target);
        }
    }

    @Override
    public void navigateTo(NpcHandle handle, Location target) {
        NPC npc = getNpc(handle);
        if (npc.isSpawned()) {
            npc.getNavigator().setTarget(target);
        }
    }

    @Override
    public void navigateToEntity(NpcHandle handle, LivingEntity target, boolean aggressive) {
        NPC npc = getNpc(handle);
        if (npc.isSpawned()) {
            npc.getNavigator().setTarget(target, aggressive);
        }
    }

    @Override
    public void setNavigationLookAt(NpcHandle handle, Supplier<Location> lookTarget) {
        NPC npc = getNpc(handle);
        npc.getOrAddTrait(RotationTrait.class).getGlobalParameters().immediate(true);
        Function<Navigator, Location> fn = lookTarget == null ? null : nav -> lookTarget.get();
        // Set both default and local parameters so active paths take the look target without waiting for reset.
        npc.getNavigator().getDefaultParameters().lookAtFunction(fn);
        npc.getNavigator().getLocalParameters().lookAtFunction(fn);
    }

    @Override
    public void stopNavigation(NpcHandle handle) {
        NPC npc = getNpc(handle);
        if (npc.isSpawned()) {
            npc.getNavigator().cancelNavigation();
        }
    }

    @Override
    public boolean isNavigating(NpcHandle handle) {
        NPC npc = getNpc(handle);
        return npc.isSpawned() && npc.getNavigator().isNavigating();
    }

    @Override
    public void setNavigationSpeed(NpcHandle handle, float speed) {
        NPC npc = getNpc(handle);
        if (!npc.isSpawned()) return;
        if (!(npc.getEntity() instanceof LivingEntity living)) {
            throw new IllegalStateException("Cannot set navigation speed for NPC '" + handle.id() + "': missing LivingEntity");
        }
        AttributeInstance attr = living.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) {
            throw new IllegalStateException("Cannot set navigation speed for NPC '" + handle.id() + "': missing MOVEMENT_SPEED attribute");
        }
        // Citizens scales player-NPC velocity quadratically in (speedModifier * baseAttr); sqrt(speed / base) restores linear parity.
        float base = (float) attr.getBaseValue();
        npc.getNavigator().getLocalParameters().speedModifier((float) Math.sqrt(speed / base));
    }

    @Override
    public LivingEntity getBukkitEntity(NpcHandle handle) {
        NPC npc = getNpc(handle);
        if (npc.isSpawned() && npc.getEntity() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    @Override
    public boolean isNpcEntity(Entity entity) {
        return CitizensAPI.getNPCRegistry().isNPC(entity);
    }

    private NPC getNpc(NpcHandle handle) {
        if (handle == null) {
            throw new IllegalStateException("NpcHandle is null");
        }
        if (handle.internalNpc() == null) {
            throw new IllegalStateException("NpcHandle '" + handle.id() + "' has no underlying Citizens NPC");
        }
        return (NPC) handle.internalNpc();
    }
}
