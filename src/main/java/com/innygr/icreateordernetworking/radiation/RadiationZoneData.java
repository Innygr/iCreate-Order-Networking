package com.innygr.icreateordernetworking.radiation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension persistent radiation zones. Zones remain until rain reaches them. */
public final class RadiationZoneData extends SavedData {
    private static final String DATA_NAME = "icreate_order_networking_radiation_zones";
    private final List<Zone> zones = new ArrayList<>();

    public record Zone(BlockPos center, int radius) {
        public Zone {
            center = center.immutable();
            radius = Math.max(1, radius);
        }
    }

    public static RadiationZoneData get(net.minecraft.server.level.ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RadiationZoneData::new, RadiationZoneData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static RadiationZoneData load(CompoundTag tag, HolderLookup.Provider registries) {
        RadiationZoneData data = new RadiationZoneData();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag zone = list.getCompound(i);
            data.zones.add(new Zone(BlockPos.of(zone.getLong("Center")), zone.getInt("Radius")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Zone zone : zones) {
            CompoundTag saved = new CompoundTag();
            saved.putLong("Center", zone.center().asLong());
            saved.putInt("Radius", zone.radius());
            list.add(saved);
        }
        tag.put("Zones", list);
        return tag;
    }

    public List<Zone> zones() {
        return List.copyOf(zones);
    }

    public void add(BlockPos center, int radius) {
        zones.add(new Zone(center, radius));
        setDirty();
    }

    public void remove(Zone zone) {
        if (zones.remove(zone)) {
            setDirty();
        }
    }
}
