package com.innygr.icreateordernetworking.compat;

import com.innygr.icreateordernetworking.blocks.NetworkStatisticsBlockEntity;
import com.innygr.icreateordernetworking.logistics.NetworkStatsData;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Create Display Link source for the currently configured statistics block. */
public final class NetworkStatisticsDisplaySource extends SingleLineDisplaySource {
    public static final NetworkStatisticsDisplaySource INSTANCE = new NetworkStatisticsDisplaySource();

    private NetworkStatisticsDisplaySource() {}

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity source = context.getSourceBlockEntity();
        if (!(source instanceof NetworkStatisticsBlockEntity statistics)) return EMPTY_LINE;

        NetworkStatsData.Snapshot snapshot = statistics.getDisplaySnapshot();
        if (snapshot == null) return Component.literal("Stats unavailable");
        String scope = statistics.getMode() == NetworkStatisticsBlockEntity.Mode.TEAM_GLOBAL
                ? "TEAM"
                : statistics.getFactoryId() == null ? "FACTORY"
                : "F:" + statistics.getFactoryId().toString().substring(0, 4);
        return Component.literal(scope + " I:" + snapshot.totalInputItems()
                + " O:" + snapshot.totalOutputItems()
                + " R:" + snapshot.rejectedTransfers());
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    protected String getTranslationKey() {
        return "network_statistics";
    }
}
