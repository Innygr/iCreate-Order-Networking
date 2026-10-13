package com.innygr.icreateordernetworking.logistics;

import java.util.UUID;

/**
 * Server-side identity contract for blocks that represent a factory.
 * Implementations must enforce their own live owner/team access policy.
 */
public interface FactoryIdentityProvider {
    UUID getFactoryId();
    UUID getFactoryOwnerId();
    UUID getFactoryPartyTeamId();
    boolean canAccessFactory(UUID playerId);
}
