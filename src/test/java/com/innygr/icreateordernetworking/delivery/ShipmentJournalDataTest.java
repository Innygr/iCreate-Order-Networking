package com.innygr.icreateordernetworking.delivery;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

class ShipmentJournalDataTest {
    private DeliveryRequest request(UUID transactionId) {
        return new DeliveryRequest(UUID.randomUUID(), ResourceLocation.parse("minecraft:stone"),
                4, DeliveryRoute.TRAIN, UUID.randomUUID(), "0,64,0", transactionId);
    }

    @Test
    void reservationIsUniqueAndStartsPending() {
        ShipmentJournalData journal = new ShipmentJournalData();
        UUID transactionId = UUID.randomUUID();

        assertTrue(journal.reserve(request(transactionId)));
        assertFalse(journal.reserve(request(transactionId)));
        assertEquals(ShipmentJournalData.Stage.RESERVED, journal.find(transactionId).stage());
        assertEquals(1, journal.pending().size());
    }

    @Test
    void stagesCannotBeSkippedAndReceiptMustBeIdentified() {
        ShipmentJournalData journal = new ShipmentJournalData();
        UUID transactionId = UUID.randomUUID();
        assertTrue(journal.reserve(request(transactionId)));

        assertFalse(journal.advance(transactionId, ShipmentJournalData.Stage.RESERVED,
                ShipmentJournalData.Stage.DESTINATION_RECEIVED, "destination-A"));
        assertFalse(journal.advance(transactionId, ShipmentJournalData.Stage.RESERVED,
                ShipmentJournalData.Stage.DISPATCHED, null));
        assertTrue(journal.advance(transactionId, ShipmentJournalData.Stage.RESERVED,
                ShipmentJournalData.Stage.DISPATCHED, null));
        assertTrue(journal.advance(transactionId, ShipmentJournalData.Stage.DISPATCHED,
                ShipmentJournalData.Stage.DESTINATION_RECEIVED, "destination-A"));
        assertFalse(journal.advance(transactionId, ShipmentJournalData.Stage.DISPATCHED,
                ShipmentJournalData.Stage.DESTINATION_RECEIVED, "destination-B"));
        assertEquals("destination-A", journal.find(transactionId).destinationReference());
    }

    @Test
    void completedShipmentIsNoLongerPending() {
        ShipmentJournalData journal = new ShipmentJournalData();
        UUID transactionId = UUID.randomUUID();
        assertTrue(journal.reserve(request(transactionId)));
        assertTrue(journal.advance(transactionId, ShipmentJournalData.Stage.RESERVED,
                ShipmentJournalData.Stage.DISPATCHED, null));
        assertTrue(journal.advance(transactionId, ShipmentJournalData.Stage.DISPATCHED,
                ShipmentJournalData.Stage.DESTINATION_RECEIVED, "destination-A"));
        assertTrue(journal.advance(transactionId, ShipmentJournalData.Stage.DESTINATION_RECEIVED,
                ShipmentJournalData.Stage.COMPLETED, null));

        assertTrue(journal.pending().isEmpty());
        assertEquals(ShipmentJournalData.Stage.COMPLETED, journal.find(transactionId).stage());
    }

    @Test
    void journalSurvivesSaveLoadAndSkipsMalformedRows() {
        UUID transactionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID claimantId = UUID.randomUUID();
        ShipmentJournalData journal = new ShipmentJournalData();
        DeliveryRequest request = new DeliveryRequest(orderId, ResourceLocation.parse("minecraft:stone"),
                4, DeliveryRoute.TRAIN, claimantId, "overworld:10,64,10", transactionId);
        assertTrue(journal.reserve(request));
        assertTrue(journal.advance(transactionId, ShipmentJournalData.Stage.RESERVED,
                ShipmentJournalData.Stage.DISPATCHED, null));

        CompoundTag saved = journal.save(new CompoundTag(), null);
        ListTag rows = saved.getList("Shipments", Tag.TAG_COMPOUND);
        CompoundTag malformed = new CompoundTag();
        malformed.putString("Transaction", "not-a-uuid");
        rows.add(malformed);
        saved.put("Shipments", rows);

        ShipmentJournalData reloaded = ShipmentJournalData.load(saved, null);
        ShipmentJournalData.Shipment restored = reloaded.find(transactionId);
        assertNotNull(restored);
        assertEquals(ShipmentJournalData.Stage.DISPATCHED, restored.stage());
        assertEquals(orderId, restored.orderId());
        assertEquals(claimantId, restored.claimantId());
        assertEquals(4, restored.count());
        assertEquals(1, reloaded.pending().size());
    }
}
