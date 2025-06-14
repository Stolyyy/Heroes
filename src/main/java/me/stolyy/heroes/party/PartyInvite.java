package me.stolyy.heroes.party;

import java.util.UUID;

public record PartyInvite(UUID inviter, UUID invited, long timestamp) {

}
