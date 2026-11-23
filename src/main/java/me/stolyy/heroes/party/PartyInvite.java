package me.stolyy.heroes.party;

import java.util.UUID;

/**
 * @param bulk true when this came from /party invite-all. The invitee experience is identical;
 *             the flag only suppresses the inviter-side expiry notice, which would otherwise fire
 *             once per target 60 seconds after a single command.
 */
public record PartyInvite(UUID inviter, UUID invited, long timestamp, boolean bulk) {

}
