package com.innygr.icreateordernetworking.logistics;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import com.innygr.icreateordernetworking.compat.AeronauticsDockingCompat;
import com.innygr.icreateordernetworking.compat.CreateBeltCompat;
import com.innygr.icreateordernetworking.compat.CreateCompat;
import com.innygr.icreateordernetworking.compat.CreateTrainCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Detects candidate direct logistics endpoints next to a Terminal Logger.
 * This is discovery only: it never extracts, inserts, reserves, or consumes
 * cargo and must not be used as proof that a delivery completed.
 */
public final class TerminalLoggerConnectionDetector {
    private static final ResourceLocation PACKAGER =
            ResourceLocation.fromNamespaceAndPath("create", "packager");

    private TerminalLoggerConnectionDetector() {}

    public static List<TerminalLoggerConnection> detect(TerminalLoggerBlockEntity logger) {
        if (logger == null || logger.getLevel() == null) return List.of();
        Level level = logger.getLevel();
        if (level.isClientSide) return List.of();

        List<TerminalLoggerConnection> connections = new ArrayList<>();
        Direction output = logger.getOutputDirection();
        BlockPos origin = logger.getBlockPos();

        for (Direction face : Direction.values()) {
            BlockPos targetPos = origin.relative(face);
            if (!level.hasChunkAt(targetPos)) continue;

            BlockState state = level.getBlockState(targetPos);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            TerminalLoggerConnection.Kind kind = classify(id);
            if (kind == null) continue;

            TerminalLoggerConnection.TransferDirection direction =
                    face == output
                            ? TerminalLoggerConnection.TransferDirection.OUTPUT
                            : TerminalLoggerConnection.TransferDirection.INPUT;

            boolean modAvailable = switch (kind) {
                case PACKAGER -> CreateCompat.isCreateLoaded()
                        && BuiltInRegistries.BLOCK.containsKey(PACKAGER);
                case BELT_CONNECTOR -> CreateBeltCompat.isBeltInfrastructureAvailable();
                case PORTABLE_STORAGE_INTERFACE -> CreateTrainCompat.isPortableStorageInterfaceAvailable();
                case DOCKING_CONNECTOR -> AeronauticsDockingCompat.isDockingConnectorAvailable();
            };

            TerminalLoggerConnection.Status status = modAvailable
                    ? TerminalLoggerConnection.Status.DETECTED_API_PENDING
                    : TerminalLoggerConnection.Status.MOD_MISSING;
            String detail = modAvailable
                    ? "Endpoint detected; transfer adapter and receipt verification are not implemented yet."
                    : "Endpoint ID found, but its optional mod stack is not available.";

            connections.add(new TerminalLoggerConnection(
                    face, kind, direction, status, id, detail));
        }
        return List.copyOf(connections);
    }

    private static TerminalLoggerConnection.Kind classify(ResourceLocation id) {
        if (id.equals(PACKAGER)) return TerminalLoggerConnection.Kind.PACKAGER;
        if (id.equals(CreateBeltCompat.beltConnectorId())) {
            return TerminalLoggerConnection.Kind.BELT_CONNECTOR;
        }
        if (id.equals(CreateTrainCompat.portableStorageInterfaceId())) {
            return TerminalLoggerConnection.Kind.PORTABLE_STORAGE_INTERFACE;
        }
        if (id.equals(AeronauticsDockingCompat.dockingConnectorId())) {
            return TerminalLoggerConnection.Kind.DOCKING_CONNECTOR;
        }
        return null;
    }
}
