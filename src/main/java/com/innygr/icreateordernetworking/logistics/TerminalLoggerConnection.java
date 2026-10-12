package com.innygr.icreateordernetworking.logistics;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Read-only snapshot of a block adjacent to a Terminal Logger.
 *
 * DETECTED_API_PENDING is intentionally not a "ready" or "delivered" state:
 * cargo movement must be implemented by a version-verified adapter and
 * destination receipt must be confirmed separately.
 */
public record TerminalLoggerConnection(
        Direction face,
        Kind kind,
        TransferDirection transferDirection,
        Status status,
        ResourceLocation blockId,
        String detail) {

    public enum Kind {
        PACKAGER,
        FROGPORT,
        BELT,
        BELT_CONNECTOR,
        PORTABLE_STORAGE_INTERFACE,
        DOCKING_CONNECTOR
    }

    public enum TransferDirection {
        INPUT,
        OUTPUT
    }

    public enum Status {
        DETECTED_API_PENDING,
        MOD_MISSING,
        INCOMPATIBLE
    }
}
