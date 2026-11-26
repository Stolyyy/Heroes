package me.stolyy.heroes.npc;

/**
 * Wrapper record for NPC handles.
 * The internalNpc object is the underlying implementation-specific NPC object.
 */
public record NpcHandle(String id, Object internalNpc) {}
