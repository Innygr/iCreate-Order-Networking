package com.innygr.icreateordernetworking.logistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.innygr.icreateordernetworking.blocks.OrderTerminalBlockEntity;
import com.innygr.icreateordernetworking.compat.CreateStockLinkCompat;
import com.innygr.icreateordernetworking.compat.FTBTeamsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent, owner-authorized association between a loaded Create Stock Link
 * and an iCreate factory. A Create frequency alone is never treated as an
 * iCreate factory identity or as permission to issue sourcing requests.
 */
public final class StockLinkBindingData extends SavedData {
    private static final String DATA_NAME = "icreate_order_networking_stock_link_bindings";
    private final Map<Key, Binding> bindings = new LinkedHashMap<>();

    public record Binding(String dimension, long packedPosition, UUID createNetworkId,
                          UUID factoryId, UUID factoryOwnerId, UUID factoryTeamId) {}
    private record Key(String dimension, long packedPosition) {}

    /**
     * A shared Create frequency is not a factory permission boundary. Source
     * Stock Links must be bound to the same factory identity as the selected
     * root link, even when the actor belongs to both factories' team.
     */
    static boolean belongsToSameFactory(Binding expected, Binding candidate) {
        return expected != null && candidate != null
                && expected.factoryId().equals(candidate.factoryId())
                && expected.factoryOwnerId().equals(candidate.factoryOwnerId())
                && java.util.Objects.equals(expected.factoryTeamId(), candidate.factoryTeamId())
                && expected.createNetworkId().equals(candidate.createNetworkId());
    }

    public static StockLinkBindingData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StockLinkBindingData::new, StockLinkBindingData::load), DATA_NAME);
    }

    /** Bind only when the actor currently has access to the selected factory terminal. */
    public boolean bind(ServerLevel level, BlockPos stockLinkPos, UUID actorId,
                        OrderTerminalBlockEntity terminal) {
        if (!validContext(level, stockLinkPos, actorId) || terminal == null
                || !terminal.canAccessFactory(actorId) || !terminal.isConfigured()) return false;
        UUID createNetworkId = CreateStockLinkCompat.networkIdAt(level, stockLinkPos);
        UUID factoryId = terminal.getFactoryId();
        UUID ownerId = terminal.getFactoryOwnerId();
        UUID teamId = terminal.getFactoryPartyTeamId();
        if (createNetworkId == null || factoryId == null || ownerId == null) return false;
        if (teamId == null) {
            if (!ownerId.equals(actorId)) return false;
        } else if (!teamId.equals(FTBTeamsCompat.partyTeamId(actorId).orElse(null))
                || !teamId.equals(FTBTeamsCompat.partyTeamId(ownerId).orElse(null))) {
            return false;
        }
        Key key = key(level, stockLinkPos);
        bindings.put(key, new Binding(key.dimension(), key.packedPosition(), createNetworkId,
                factoryId, ownerId, teamId));
        setDirty();
        return true;
    }

    /**
     * Resolve a binding only if the link remains loaded, still has the same
     * Create frequency, and the actor still belongs to the authorized owner/team.
     */
    public Binding authorizedBinding(ServerLevel level, BlockPos stockLinkPos, UUID actorId) {
        if (!validContext(level, stockLinkPos, actorId)) return null;
        Key key = key(level, stockLinkPos);
        Binding binding = bindings.get(key);
        if (binding == null || !binding.createNetworkId().equals(
                CreateStockLinkCompat.networkIdAt(level, stockLinkPos))) return null;
        if (binding.factoryTeamId() == null) {
            return binding.factoryOwnerId().equals(actorId) ? binding : null;
        }
        UUID actorTeam = FTBTeamsCompat.partyTeamId(actorId).orElse(null);
        UUID ownerTeam = FTBTeamsCompat.partyTeamId(binding.factoryOwnerId()).orElse(null);
        return binding.factoryTeamId().equals(actorTeam)
                && binding.factoryTeamId().equals(ownerTeam) ? binding : null;
    }

    public boolean unbind(ServerLevel level, BlockPos stockLinkPos, UUID actorId,
                          OrderTerminalBlockEntity terminal) {
        if (!validContext(level, stockLinkPos, actorId) || terminal == null
                || !terminal.canAccessFactory(actorId)) return false;
        Key key = key(level, stockLinkPos);
        Binding binding = bindings.get(key);
        if (binding == null || !binding.factoryId().equals(terminal.getFactoryId())) return false;
        bindings.remove(key);
        setDirty();
        return true;
    }

    private static boolean validContext(ServerLevel level, BlockPos pos, UUID actorId) {
        return level != null && pos != null && actorId != null && level.getServer().isSameThread()
                && level.hasChunkAt(pos) && CreateStockLinkCompat.networkIdAt(level, pos) != null;
    }

    private static Key key(ServerLevel level, BlockPos pos) {
        return new Key(level.dimension().location().toString(), pos.asLong());
    }

    public static StockLinkBindingData load(CompoundTag tag, HolderLookup.Provider registries) {
        StockLinkBindingData data = new StockLinkBindingData();
        ListTag rows = tag.getList("Bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            try {
                String dimension = row.getString("Dimension");
                long position = row.getLong("Position");
                UUID network = UUID.fromString(row.getString("CreateNetwork"));
                UUID factory = UUID.fromString(row.getString("Factory"));
                UUID owner = UUID.fromString(row.getString("Owner"));
                UUID team = row.contains("Team") ? UUID.fromString(row.getString("Team")) : null;
                if (!dimension.isBlank() && dimension.length() <= 256 && !data.bindings.containsKey(
                        new Key(dimension, position))) {
                    Key key = new Key(dimension, position);
                    data.bindings.put(key, new Binding(dimension, position, network, factory, owner, team));
                }
            } catch (RuntimeException ignored) {
                // Ignore corrupt rows; do not prevent world startup.
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        for (Binding binding : bindings.values()) {
            CompoundTag row = new CompoundTag();
            row.putString("Dimension", binding.dimension());
            row.putLong("Position", binding.packedPosition());
            row.putString("CreateNetwork", binding.createNetworkId().toString());
            row.putString("Factory", binding.factoryId().toString());
            row.putString("Owner", binding.factoryOwnerId().toString());
            if (binding.factoryTeamId() != null) row.putString("Team", binding.factoryTeamId().toString());
            rows.add(row);
        }
        tag.put("Bindings", rows);
        return tag;
    }
}
