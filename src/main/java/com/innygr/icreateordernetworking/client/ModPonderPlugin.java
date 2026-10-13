package com.innygr.icreateordernetworking.client;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import com.innygr.icreateordernetworking.registry.ModBlocks;
import com.innygr.icreateordernetworking.ponder.ModPonderScenes;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class ModPonderPlugin implements PonderPlugin {
    @Override public String getModId() { return ICreateOrderNetworking.MOD_ID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ModBlocks.TERMINAL_LOGGER.getId()).addStoryBoard("terminal_logger/basics", ModPonderScenes::terminalLogger);
        helper.forComponents(ModBlocks.BRASS_TERMINAL_LOGGER.getId()).addStoryBoard("terminal_logger/brass", ModPonderScenes::brassTerminalLogger);
        helper.forComponents(ModBlocks.ORDER_TERMINAL.getId()).addStoryBoard("orders/terminal", ModPonderScenes::orderTerminal);
        helper.forComponents(ModBlocks.NETWORK_STATISTICS.getId()).addStoryBoard("network/statistics", ModPonderScenes::networkStatistics);
        helper.forComponents(ModBlocks.SCRAPPER.getId()).addStoryBoard("scrapper/basics", ModPonderScenes::scrapper);
        helper.forComponents(ModBlocks.MISSILE_THRUSTER.getId()).addStoryBoard("missile/fuel", ModPonderScenes::missileThruster);
        helper.forComponents(ModBlocks.MISSILE_TAG.getId()).addStoryBoard("missile/team_tag", ModPonderScenes::missileTag);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation logistics = ResourceLocation.fromNamespaceAndPath(ICreateOrderNetworking.MOD_ID, "logistics");
        ResourceLocation missile = ResourceLocation.fromNamespaceAndPath(ICreateOrderNetworking.MOD_ID, "missiles");
        helper.registerTag(logistics, "iCreate Logistics", "Blocks used to configure orders and track deliveries.",
                ModBlocks.ORDER_TERMINAL.getId(), ModBlocks.TERMINAL_LOGGER.getId(),
                ModBlocks.BRASS_TERMINAL_LOGGER.getId(), ModBlocks.NETWORK_STATISTICS.getId());
        helper.registerTag(missile, "Missile Systems", "Fuel and identity components for the planned missile system.",
                ModBlocks.MISSILE_THRUSTER.getId(), ModBlocks.MISSILE_TAG.getId());
    }
}