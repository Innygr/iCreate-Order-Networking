package com.innygr.icreateordernetworking.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

public final class ModPonderScenes {
    private ModPonderScenes() {}

    public static void terminalLogger(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "terminal_logger_basics", "Tracking an item delivery");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.BARREL.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.HOPPER.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        text(scene, "The Terminal Logger is the monitoring point for delivery activity.");
        text(scene, "Configure the real logistics setup before relying on delivery records.");
        scene.markAsFinished();
    }

    public static void brassTerminalLogger(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "brass_terminal_logger", "Choosing the brass logger");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.COPPER_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.BRICKS.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.GOLD_BLOCK.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        text(scene, "The brass variant is a separate logger component.");
        text(scene, "Use its actual configuration and inventory behavior; this scene is an introduction.");
        scene.markAsFinished();
    }

    public static void orderTerminal(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "order_terminal", "Creating and fulfilling orders");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.BARREL.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.LECTERN.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        text(scene, "Use the Order Terminal to access the mod's order workflow.");
        text(scene, "An order must be claimed and fulfilled through the available order actions.");
        text(scene, "Check the delivery result before assuming an order is complete.");
        scene.markAsFinished();
    }

    public static void networkStatistics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "network_statistics", "Reading network statistics");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.REDSTONE_LAMP.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.OBSERVER.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.COMPARATOR.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        text(scene, "Network Statistics provides a view of network activity.");
        text(scene, "Inspect the current network state rather than guessing where a request stalled.");
        scene.markAsFinished();
    }

    public static void scrapper(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "scrapper_basics", "Using the Scrapper");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.RAW_IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        text(scene, "The Scrapper is a dedicated processing component.");
        text(scene, "Check its accepted inputs and output behavior in the block interface and recipe data.");
        scene.markAsFinished();
    }

    public static void missileThruster(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "missile_thruster", "Fuel and thruster basics");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.WATER_CAULDRON.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.FURNACE.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.LAVA_CAULDRON.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        text(scene, "The fuel helper assigns water half the range efficiency of lava.");
        text(scene, "Fuel-range helpers exist, but this block is not yet a complete missile engine.");
        text(scene, "This is an overview, not a launch procedure.");
        scene.markAsFinished();
    }

    public static void missileTag(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "missile_tag", "Assigning missile identity");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.OAK_SIGN.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.WHITE_BANNER.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        text(scene, "A Missile Tag stores an identifier, team name, and banner data.");
        text(scene, "The team name can be used by the dedicated death-message helpers.");
        text(scene, "Missile impact integration and the in-world banner renderer remain future work.");
        scene.markAsFinished();
    }

    public static void transportAdapters(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "transport_adapters", "Connecting mobile transport");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.RAIL.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        text(scene, "Train delivery integration is intended to use Create Portable Storage Interfaces.");
        text(scene, "Airship integration is intended to use Aeronautics Docking Connectors.");
        text(scene, "These adapters are integration targets; this scene does not claim the complete transport workflow is wired up.");
        scene.markAsFinished();
    }

    public static void orbitalMissile(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "orbital_missile", "Orbital missile overview");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 2, 2), Blocks.FURNACE.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 3, 2), Blocks.ANVIL.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 3, 2), Direction.DOWN);
        text(scene, "The planned orbital assembly must be inverted and its anchor must be at Y=250 or higher.");
        text(scene, "Starting high and descending should reduce the fuel needed for the climb.");
        text(scene, "The validator and message helpers exist, but live launch, descent physics, and impact integration remain future work.");
        scene.markAsFinished();
    }

    private static CreateSceneBuilder start(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        return scene;
    }

    private static void showBase(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);
    }

    private static void text(CreateSceneBuilder scene, String message) {
        scene.overlay().showText(70).text(message).placeNearTarget();
        scene.idle(75);
    }
}