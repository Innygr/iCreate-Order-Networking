package com.innygr.icreateordernetworking.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Ponder tutorials for the currently registered iCreate: Order Networking blocks.
 *
 * Scenes deliberately demonstrate only current concepts. Vanilla blocks are visual
 * stand-ins; they are not claimed to be functional logistics connections.
 */
public final class ModPonderScenes {
    private ModPonderScenes() {}

    public static void terminalLogger(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "terminal_logger_basics", "Tracking a delivery");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.BARREL.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.HOPPER.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        reveal(scene, util, 1, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "A Terminal Logger is the point to inspect when tracking delivery activity.", 1, 1, 2);
        reveal(scene, util, 2, 1, 2, 2, Direction.DOWN);
        explain(scene, util, "Follow the shipment through its recorded stages instead of guessing where it stopped.", 2, 1, 2);
        reveal(scene, util, 3, 1, 2, 3, Direction.DOWN);
        explain(scene, util, "Verify the destination receipt before treating a delivery as complete.", 3, 1, 2);
        scene.markAsFinished();
    }

    public static void brassTerminalLogger(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "brass_terminal_logger", "The brass logger");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.COPPER_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.BRICKS.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.GOLD_BLOCK.defaultBlockState(), false);
        reveal(scene, util, 1, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "Choose the logger variant that matches the component you are building with.", 1, 1, 2);
        reveal(scene, util, 2, 1, 2, 2, Direction.DOWN);
        explain(scene, util, "This brass-themed layout is illustrative; the blocks shown are not the actual logger.", 2, 1, 2);
        reveal(scene, util, 3, 1, 2, 3, Direction.DOWN);
        explain(scene, util, "Use the real block interface and its available options to configure your setup.", 3, 1, 2);
        scene.markAsFinished();
    }

    public static void orderTerminal(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "order_terminal", "From request to fulfilment");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.BARREL.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.LECTERN.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        reveal(scene, util, 1, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "Start at the Order Terminal to create or inspect an order.", 1, 1, 2);
        reveal(scene, util, 2, 1, 2, 2, Direction.DOWN);
        explain(scene, util, "Claim an available order before working on it.", 2, 1, 2);
        reveal(scene, util, 3, 1, 2, 3, Direction.DOWN);
        explain(scene, util, "Fulfil the order and check its state; do not assume a request is delivered merely because it was claimed.", 3, 1, 2);
        scene.markAsFinished();
    }

    public static void networkStatistics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "network_statistics", "Diagnosing network activity");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.REDSTONE_LAMP.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.OBSERVER.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.COMPARATOR.defaultBlockState(), false);
        reveal(scene, util, 1, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "Begin by checking the network's reported activity.", 1, 1, 2);
        reveal(scene, util, 2, 1, 2, 2, Direction.DOWN);
        explain(scene, util, "Use the available status information to narrow down where a request stalled.", 2, 1, 2);
        reveal(scene, util, 3, 1, 2, 3, Direction.DOWN);
        explain(scene, util, "These redstone blocks are visual props, not live network sensors.", 3, 1, 2);
        scene.markAsFinished();
    }

    public static void scrapper(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "scrapper_basics", "Scrapper input and output");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.RAW_IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        reveal(scene, util, 1, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "Check that an input is accepted by the Scrapper before processing.", 1, 1, 2);
        reveal(scene, util, 2, 1, 2, 2, Direction.DOWN);
        explain(scene, util, "The iron blocks are examples only; they do not represent a recipe.", 2, 1, 2);
        reveal(scene, util, 3, 1, 2, 3, Direction.DOWN);
        explain(scene, util, "Inspect the actual recipe data and block behavior for valid outputs.", 3, 1, 2);
        scene.markAsFinished();
    }

    public static void missileThruster(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "missile_thruster", "Fuel efficiency overview");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.WATER_CAULDRON.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.FURNACE.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.LAVA_CAULDRON.defaultBlockState(), false);
        reveal(scene, util, 1, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "Water is the lower-efficiency fuel option in the current range helper.", 1, 1, 2);
        reveal(scene, util, 2, 1, 2, 2, Direction.DOWN);
        explain(scene, util, "The thruster block is shown as a reference point, not a working engine assembly.", 2, 1, 2);
        reveal(scene, util, 3, 1, 2, 3, Direction.DOWN);
        explain(scene, util, "The current helper gives water half the range efficiency of lava; full launch integration is not implemented yet.", 3, 1, 2);
        scene.markAsFinished();
    }

    public static void missileTag(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "missile_tag", "Missile identity data");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.OAK_SIGN.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.WHITE_BANNER.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        reveal(scene, util, 1, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "A Missile Tag can store an identifier for a tagged assembly.", 1, 1, 2);
        reveal(scene, util, 2, 1, 2, 2, Direction.DOWN);
        explain(scene, util, "Its data also includes a team name and banner data.", 2, 1, 2);
        reveal(scene, util, 3, 1, 2, 3, Direction.DOWN);
        explain(scene, util, "The sign and banner are props; impact integration and an in-world banner renderer remain future work.", 3, 1, 2);
        scene.markAsFinished();
    }

    public static void transportAdapters(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "transport_adapters", "Transport integration overview");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.RAIL.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.CHEST.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        reveal(scene, util, 1, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "Create trains are a planned route for moving shipments between locations.", 1, 1, 2);
        reveal(scene, util, 2, 1, 2, 2, Direction.DOWN);
        explain(scene, util, "Portable Storage Interfaces are the intended train transfer integration point.", 2, 1, 2);
        reveal(scene, util, 3, 1, 2, 3, Direction.DOWN);
        explain(scene, util, "Aeronautics Docking Connectors are the intended airship integration point; these adapters are not yet a complete wired workflow.", 3, 1, 2);
        scene.markAsFinished();
    }

    public static void orbitalMissile(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = start(builder, "orbital_missile", "Orbital assembly overview");
        showBase(scene, util);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 2, 2), Blocks.FURNACE.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 3, 2), Blocks.ANVIL.defaultBlockState(), false);
        reveal(scene, util, 2, 1, 2, 1, Direction.DOWN);
        explain(scene, util, "The planned assembly is inverted, with its anchor at Y=250 or higher.", 2, 1, 2);
        reveal(scene, util, 2, 2, 2, 2, Direction.DOWN);
        explain(scene, util, "Starting high is intended to reduce the fuel needed for the climb.", 2, 2, 2);
        reveal(scene, util, 2, 3, 2, 3, Direction.DOWN);
        explain(scene, util, "The validator and message helpers exist; live launch, descent physics, and impact integration remain future work.", 2, 3, 2);
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

    private static void reveal(CreateSceneBuilder scene, SceneBuildingUtil util,
                               int x, int y, int z, int pause, Direction direction) {
        scene.world().showSection(util.select().position(x, y, z), direction);
        scene.idle(8 + pause * 2);
    }

    private static void explain(CreateSceneBuilder scene, SceneBuildingUtil util,
                                String message, int x, int y, int z) {
        scene.overlay().showText(65)
                .text(message)
                .placeNearTarget()
                .pointAt(util.vector().topOf(x, y, z));
        scene.idle(70);
    }
}
