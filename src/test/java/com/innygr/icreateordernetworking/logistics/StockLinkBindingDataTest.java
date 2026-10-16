package com.innygr.icreateordernetworking.logistics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

class StockLinkBindingDataTest {
    @Test
    void saveLoadPreservesFactoryAndCreateNetworkIdentity() {
        UUID network = UUID.randomUUID();
        UUID factory = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        ListTag rows = new ListTag();
        CompoundTag row = new CompoundTag();
        row.putString("Dimension", "minecraft:overworld");
        row.putLong("Position", 123456L);
        row.putString("CreateNetwork", network.toString());
        row.putString("Factory", factory.toString());
        row.putString("Owner", owner.toString());
        row.putString("Team", team.toString());
        rows.add(row);
        root.put("Bindings", rows);

        StockLinkBindingData loaded = StockLinkBindingData.load(root, null);
        CompoundTag saved = loaded.save(new CompoundTag(), null);
        ListTag restored = saved.getList("Bindings", Tag.TAG_COMPOUND);
        assertEquals(1, restored.size());
        CompoundTag actual = restored.getCompound(0);
        assertEquals("minecraft:overworld", actual.getString("Dimension"));
        assertEquals(123456L, actual.getLong("Position"));
        assertEquals(network.toString(), actual.getString("CreateNetwork"));
        assertEquals(factory.toString(), actual.getString("Factory"));
        assertEquals(owner.toString(), actual.getString("Owner"));
        assertEquals(team.toString(), actual.getString("Team"));
    }

    @Test
    void malformedRowsAreIgnoredWithoutLosingValidBindings() {
        CompoundTag root = new CompoundTag();
        ListTag rows = new ListTag();
        CompoundTag invalid = new CompoundTag();
        invalid.putString("Dimension", "minecraft:overworld");
        invalid.putString("CreateNetwork", "not-a-uuid");
        rows.add(invalid);
        root.put("Bindings", rows);

        StockLinkBindingData loaded = StockLinkBindingData.load(root, null);
        assertTrue(loaded.save(new CompoundTag(), null).getList("Bindings", Tag.TAG_COMPOUND).isEmpty());
    }
}
