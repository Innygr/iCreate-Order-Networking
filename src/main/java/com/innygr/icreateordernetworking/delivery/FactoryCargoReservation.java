package com.innygr.icreateordernetworking.delivery;

import net.minecraft.server.level.ServerLevel;

/** Performs physical factory stock reservation after the durable journal exists. */
@FunctionalInterface
interface FactoryCargoReservation {
    /** Return false only when no physical mutation was made; throw to retain the journal for recovery. */
    boolean reserve(ServerLevel level, DeliveryRequest request);
}
