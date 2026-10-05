package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Tests that run in the game itself ({@code ./gradlew runGameTestServer}), for what cannot be checked by compiling: that a rope holds on a
 * log and lets go when the log goes, and that a thief in a closed room finds its way out to a ripe field. They do nothing in a normal game.
 */
@GameTestHolder(ThiefMod.MODID)
@PrefixGameTestTemplate(false)
public final class ThiefGameTests {

    private ThiefGameTests() {
    }

    private static void floor(GameTestHelper h) {
        h.killAllEntities();                   // whatever a run before this one left standing here (tied-up thieves do not despawn, and the test world is kept between runs)
        RescueGoal.searchRange = 10.0D;        // test areas are close together: do not rescue from the next one
        ThiefEntity.confessRange = 16.0D;      // nor make the thieves of the next one shine
        ThiefEntity.autoRescuers = false;      // nor call up rescuers that wander into the next one
        for (int x = 0; x < 14; x++) {
            for (int z = 0; z < 14; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    @GameTest(batch = "t001", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void ropeHoldsOnALog(GameTestHelper h) {
        floor(h);
        BlockPos log = new BlockPos(3, 1, 3);
        h.setBlock(log, Blocks.OAK_LOG);
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 3));
        thief.rescueProofForTest = true;        // thieves of the tests next to this one must not cut it loose
        LeashFenceKnotEntity knot = RopeKnotEntity.getOrCreate(h.getLevel(), h.absolutePos(log));
        thief.tieWithRope(knot);
        h.runAfterDelay(250, () -> {
            h.assertTrue(!knot.isRemoved(), "the knot was taken away from a log");
            h.assertTrue(thief.tiedUp() && thief.getLeashHolder() == knot, "the thief is not tied to the knot any more");
            h.succeed();
        });
    }

    @GameTest(batch = "t002", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void ropeLetsGoWhenTheLogIsBroken(GameTestHelper h) {
        floor(h);
        BlockPos log = new BlockPos(3, 1, 3);
        h.setBlock(log, Blocks.OAK_LOG);
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 3));
        LeashFenceKnotEntity knot = RopeKnotEntity.getOrCreate(h.getLevel(), h.absolutePos(log));
        thief.tieWithRope(knot);
        h.runAfterDelay(5, () -> h.setBlock(log, Blocks.AIR));
        h.runAfterDelay(300, () -> {
            h.assertTrue(!thief.isLeashed(), "the thief is still tied to a knot with nothing under it");
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(5, 1, 3), 6.0D);        // the rope came back as a rope, not as a lead
            h.succeed();
        });
    }

    @GameTest(batch = "t003", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aTiedThiefStaysWhereItIs(GameTestHelper h) {
        floor(h);
        BlockPos log = new BlockPos(3, 1, 3);
        h.setBlock(log, Blocks.OAK_LOG);
        h.setBlock(new BlockPos(9, 1, 9), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        h.setBlock(new BlockPos(9, 0, 9), Blocks.FARMLAND);
        StealGoal.requirePlayerNearby = false;
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(4, 1, 3));
        thief.tieWithRope(RopeKnotEntity.getOrCreate(h.getLevel(), h.absolutePos(log)));
        h.runAfterDelay(300, () -> {
            h.assertTrue(!thief.hasLoot(), "a tied thief stole");
            h.assertTrue(thief.blockPosition().distSqr(h.absolutePos(log)) <= 36, "a tied thief got away from its post");
            h.succeed();
        });
    }

    /** The room from the bug report: a small room, a wooden door, a ripe field outside. */
    private static ThiefEntity roomWithDoor(GameTestHelper h, boolean ironDoor) {
        floor(h);
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                boolean wall = x == 1 || x == 5 || z == 1 || z == 5;
                for (int y = 1; y <= 3; y++) {
                    if (wall && !(x == 3 && z == 5 && y <= 2)) {
                        h.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
                    }
                }
                h.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);       // a roof
            }
        }
        var door = (ironDoor ? Blocks.IRON_DOOR : Blocks.OAK_DOOR).defaultBlockState();
        h.setBlock(new BlockPos(3, 1, 5), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(3, 2, 5), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int x = 2; x <= 4; x++) {
            h.setBlock(new BlockPos(x, 0, 9), Blocks.FARMLAND);
            h.setBlock(new BlockPos(x, 1, 9), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        StealGoal.requirePlayerNearby = false;
        return h.spawn(ModEntities.THIEF.get(), new BlockPos(3, 1, 3));
    }

    @GameTest(batch = "t004", template = "field", setupTicks = 20, timeoutTicks = 1500)
    public static void thiefLeavesARoomWithAWoodenDoorToRobTheField(GameTestHelper h) {
        ThiefEntity thief = roomWithDoor(h, false);
        h.succeedWhen(() -> {
            if (!thief.hasLoot()) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("the thief has not picked the crop yet; it is at " + thief.blockPosition().toShortString() + ", goals: " + thief.runningGoals());
            }
        });
    }

    @GameTest(batch = "t005", template = "field", setupTicks = 20, timeoutTicks = 700)
    public static void thiefStaysInARoomWithAnIronDoor(GameTestHelper h) {
        ThiefEntity thief = roomWithDoor(h, true);
        h.runAfterDelay(600, () -> {
            h.assertTrue(!thief.hasLoot(), "the thief got through an iron door");
            h.succeed();
        });
    }

    @GameTest(batch = "t006", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void theMagicianLooksLikeTheCowNextToIt(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        h.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(7, 1, 3));
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(3, 1, 3));
        thief.becomeMagician();
        h.runAfterDelay(150, () -> {
            h.assertTrue(thief.isDisguised(), "the thief did not drink its potion with a cow next to it");
            // one of the creatures that were there (the next test area may have a villager): the cow, or a villager
            var type = thief.disguiseType();
            h.assertTrue(type == net.minecraft.world.entity.EntityType.COW || type == net.minecraft.world.entity.EntityType.VILLAGER, "it looks like " + type);
            h.assertTrue(Math.abs(thief.getDimensions(net.minecraft.world.entity.Pose.STANDING).width - type.getDimensions().width) < 0.01F, "it is not as big as " + type);
            h.assertTrue(thief.potions() == 1, "a potion was not used up: " + thief.potions());
            h.succeed();
        });
    }

    @GameTest(batch = "t007", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aBlowShowsWhoTheMagicianIs(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        h.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(7, 1, 3));
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(3, 1, 3));
        thief.becomeMagician();
        h.runAfterDelay(150, () -> {
            h.assertTrue(thief.isDisguised(), "not disguised before the blow");
            thief.hurt(h.getLevel().damageSources().generic(), 1.0F);
            h.assertTrue(!thief.isDisguised(), "still looks like a cow after being hit");
            h.assertTrue(Math.abs(thief.getDimensions(net.minecraft.world.entity.Pose.STANDING).width - 0.6F) < 0.01F, "not a thief's size again");
            h.succeed();
        });
    }

    @GameTest(batch = "t008", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void anOrdinaryThiefNeverLooksLikeAnythingElse(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        h.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(7, 1, 3));
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(3, 1, 3));
        h.runAfterDelay(250, () -> {
            h.assertTrue(!thief.isDisguised(), "an ordinary thief drank its potion");
            h.assertTrue(thief.potions() == 2, "an ordinary thief used a potion");
            h.succeed();
        });
    }

    // ------------------------------------------------ searching, the whip, hanging ------------------------------------------------

    private static final net.minecraft.world.phys.Vec3 NOWHERE = net.minecraft.world.phys.Vec3.ZERO;

    /** A thief with two different stacks on it, tied to a log. */
    private static ThiefEntity tiedThiefWithLoot(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 3), Blocks.OAK_LOG);
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 3));
        thief.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        thief.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));
        thief.tieWithRope(RopeKnotEntity.getOrCreate(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3))));
        return thief;
    }

    @GameTest(batch = "t009", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void searchingATiedThiefTakesOneThingAtATime(GameTestHelper h) {
        ThiefEntity thief = tiedThiefWithLoot(h);
        var player = h.makeMockPlayer();
        player.setShiftKeyDown(true);
        var first = thief.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(first.consumesAction(), "searching did nothing");
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 5, "the first thing was not handed over");
        h.assertTrue(thief.lootCount() == 1, "more than one thing was taken at once");
        var second = thief.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.GOLD_INGOT) == 3, "the second thing was not handed over: result " + second + ", gold " + player.getInventory().countItem(net.minecraft.world.item.Items.GOLD_INGOT)
                + ", iron " + player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) + ", loot left " + thief.lootCount() + ", tied " + thief.tiedUp());
        h.assertTrue(!thief.hasLoot(), "the thief still has loot");
        h.succeed();
    }

    @GameTest(batch = "t010", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aThiefThatIsNotTiedCannotBeSearched(GameTestHelper h) {
        floor(h);
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 3));
        thief.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        var player = h.makeMockPlayer();
        player.setShiftKeyDown(true);
        thief.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 0 && thief.lootCount() == 1, "a free thief was searched");
        h.succeed();
    }

    @GameTest(batch = "t011", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void theWhipDoesNotKillAndShakesLootLoose(GameTestHelper h) {
        ThiefEntity thief = tiedThiefWithLoot(h);
        var player = h.makeMockPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.WHIP.get()));
        thief.hurt(h.getLevel().damageSources().playerAttack(player), 1000.0F);
        h.assertTrue(thief.isAlive() && thief.getHealth() >= 1.0F, "the whip killed the thief (health " + thief.getHealth() + ")");
        h.assertTrue(thief.lootCount() == 1, "no loot was shaken loose from a tied thief: " + thief.lootCount());
        h.succeed();
    }

    @GameTest(batch = "t012", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aFreeThiefLashedWithAWhipKeepsItsLoot(GameTestHelper h) {
        floor(h);
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 3));
        thief.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        var player = h.makeMockPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.WHIP.get()));
        thief.hurt(h.getLevel().damageSources().playerAttack(player), 1000.0F);
        h.assertTrue(thief.isAlive() && thief.lootCount() == 1, "a free thief spilled its loot or died");
        h.succeed();
    }

    /** The height of the thief's raised hands above the floor: the rope is what is left up to the underside of the block. */
    private static double ropeShowing(GameTestHelper h, ThiefEntity thief, int ceilingY) {
        return ceilingY - (h.relativeVec(thief.position()).y + ThiefEntity.HANDS_UP);
    }

    @GameTest(batch = "t013", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aThiefHungUpHangsByTheHandsWithRopeShowingAndIsLetDown(GameTestHelper h) {
        floor(h);
        BlockPos ceiling = new BlockPos(5, 6, 5);
        h.setBlock(ceiling, Blocks.STONE);
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(5, 1, 5));
        thief.hangFrom(h.absolutePos(ceiling));
        var player = h.makeMockPlayer();
        h.runAfterDelay(100, () -> {
            h.assertTrue(thief.isHung() && thief.isNoGravity() && thief.isRopeTied(), "not hanging");
            double gap = ropeShowing(h, thief, 6);
            h.assertTrue(gap > 1.4D && gap < 1.6D, "the rope showing between the block and the hands is " + gap + ", not 1.5");
            double feet = h.relativeVec(thief.position()).y;
            h.assertTrue(feet > 1.5D, "the feet are not clear of the floor, at height " + feet);
            thief.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);        // an empty hand lets it down
        });
        h.runAfterDelay(160, () -> {
            h.assertTrue(!thief.isHung() && !thief.isNoGravity() && !thief.isLeashed() && !thief.isRopeTied(), "still hung or tied after being let down");
            h.assertTrue(h.relativeVec(thief.position()).y < 1.3D, "did not come down to the floor");
            h.succeed();
        });
    }

    @GameTest(batch = "t014", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void underALowCeilingTheRopeIsShorterButStillShows(GameTestHelper h) {
        floor(h);
        BlockPos ceiling = new BlockPos(5, 4, 5);        // three free blocks below: just enough
        h.setBlock(ceiling, Blocks.STONE);
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(5, 1, 5));
        thief.hangFrom(h.absolutePos(ceiling));
        h.runAfterDelay(60, () -> {
            double gap = ropeShowing(h, thief, 4);
            h.assertTrue(gap > 0.45D && gap < 0.6D, "the rope showing under a low ceiling is " + gap + ", not 0.5");
            h.assertTrue(h.relativeVec(thief.position()).y > 1.2D, "the feet are on the floor");
            h.succeed();
        });
    }

    @GameTest(batch = "t015", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void theRopeWillNotHangAThiefUnderATooLowCeiling(GameTestHelper h) {
        floor(h);
        BlockPos ceiling = new BlockPos(5, 3, 5);        // only two free blocks
        h.setBlock(ceiling, Blocks.STONE);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 3.5)));
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 4));
        thief.tieWithRope(player);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(ceiling)), net.minecraft.core.Direction.DOWN, h.absolutePos(ceiling), false);
        ModItems.ROPE.get().useOn(new net.minecraft.world.item.context.UseOnContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.ROPE.get()), hit));
        h.assertTrue(!thief.isHung(), "hung under a ceiling with room for only two blocks");
        h.succeed();
    }

    @GameTest(batch = "t016", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aHungThiefFallsWhenTheBlockAboveIsBroken(GameTestHelper h) {
        floor(h);
        BlockPos ceiling = new BlockPos(5, 4, 5);
        h.setBlock(ceiling, Blocks.STONE);
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(5, 1, 5));
        thief.hangFrom(h.absolutePos(ceiling));
        h.runAfterDelay(30, () -> h.setBlock(ceiling, Blocks.AIR));
        h.runAfterDelay(120, () -> {
            h.assertTrue(!thief.isHung() && !thief.isLeashed(), "still hung with nothing above");
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(5, 1, 5), 6.0D);
            h.succeed();
        });
    }

    @GameTest(batch = "t017", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void theRopeOnTheUndersideOfABlockHangsTheThiefYouLead(GameTestHelper h) {
        floor(h);
        BlockPos ceiling = new BlockPos(5, 4, 5);
        h.setBlock(ceiling, Blocks.STONE);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 3.5)));
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 4));
        thief.tieWithRope(player);
        var stack = new net.minecraft.world.item.ItemStack(ModItems.ROPE.get());
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(ceiling)), net.minecraft.core.Direction.DOWN, h.absolutePos(ceiling), false);
        var result = ModItems.ROPE.get().useOn(new net.minecraft.world.item.context.UseOnContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit));
        h.assertTrue(result.consumesAction(), "the rope did nothing on the underside of a block");
        h.assertTrue(thief.isHung(), "the thief was not hung");
        h.succeed();
    }

    @GameTest(batch = "t018", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void theRopeWillNotHangAThiefWhereThereIsNoRoom(GameTestHelper h) {
        floor(h);
        BlockPos ceiling = new BlockPos(5, 4, 5);
        h.setBlock(ceiling, Blocks.STONE);
        h.setBlock(new BlockPos(5, 3, 5), Blocks.STONE);        // right under it
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 3.5)));
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 4));
        thief.tieWithRope(player);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(ceiling)), net.minecraft.core.Direction.DOWN, h.absolutePos(ceiling), false);
        ModItems.ROPE.get().useOn(new net.minecraft.world.item.context.UseOnContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.ROPE.get()), hit));
        h.assertTrue(!thief.isHung(), "hung into a block");
        h.succeed();
    }

    // ------------------------------------------------ the rack ------------------------------------------------

    private static void buildRack(GameTestHelper h, BlockPos bottom, net.minecraft.core.Direction facing) {
        RackBlock.build(h.getLevel(), h.absolutePos(bottom), facing);
    }

    private static net.minecraft.world.phys.BlockHitResult hitOn(GameTestHelper h, BlockPos rel, net.minecraft.core.Direction face) {
        return new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(rel)), face, h.absolutePos(rel), false);
    }

    private static net.minecraft.world.InteractionResult useRope(GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos rel, net.minecraft.core.Direction face) {
        return ModItems.ROPE.get().useOn(new net.minecraft.world.item.context.UseOnContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModItems.ROPE.get()), hitOn(h, rel, face)));
    }

    private static net.minecraft.world.InteractionResult putRackDown(GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos floorBlock) {
        return ModItems.RACK.get().useOn(new net.minecraft.world.item.context.UseOnContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModItems.RACK.get()), hitOn(h, floorBlock, net.minecraft.core.Direction.UP)));
    }

    @GameTest(batch = "t019", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void theRackItemPutsDownAFrameThreeBlocksWideAndThreeHigh(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 10.5)));
        var result = putRackDown(h, player, new BlockPos(6, 0, 6));
        h.assertTrue(result.consumesAction(), "the rack was not put down: " + result);
        for (int x = 5; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                h.assertTrue(h.getBlockState(new BlockPos(x, y, 6)).is(ModBlocks.RACK.get()), "no block of the rack at " + x + ", " + y);
            }
            h.assertTrue(h.getBlockState(new BlockPos(x, 4, 6)).isAir(), "the rack is higher than three blocks, at column " + x);
        }
        h.assertTrue(h.getBlockState(new BlockPos(4, 2, 6)).isAir() && h.getBlockState(new BlockPos(8, 2, 6)).isAir(), "the rack is wider than three blocks");
        var mid = h.getBlockState(new BlockPos(6, 1, 6));
        h.assertTrue(RackBlock.isAnchor(mid), "the middle of the bottom row is not the anchor");
        h.assertTrue(h.getBlockState(new BlockPos(5, 1, 6)).getValue(RackBlock.SIDE) != h.getBlockState(new BlockPos(7, 1, 6)).getValue(RackBlock.SIDE), "the two outer columns are the same side");
        h.succeed();
    }

    @GameTest(batch = "t020", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void theRackNeedsAllNinePlacesFreeAndFirmGroundUnderThreeOfThem(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 10.5)));
        h.setBlock(new BlockPos(6, 3, 6), Blocks.STONE);                // the middle, up at the top: a low ceiling
        putRackDown(h, player, new BlockPos(6, 0, 6));
        h.assertTrue(h.getBlockState(new BlockPos(6, 1, 6)).isAir(), "put up under a ceiling that is too low");
        h.setBlock(new BlockPos(6, 3, 6), Blocks.AIR);
        h.setBlock(new BlockPos(7, 2, 6), Blocks.STONE);                // in the way, in an outer column
        putRackDown(h, player, new BlockPos(6, 0, 6));
        h.assertTrue(h.getBlockState(new BlockPos(6, 1, 6)).isAir(), "put up with a block in the way in an outer column");
        h.setBlock(new BlockPos(7, 2, 6), Blocks.AIR);
        h.setBlock(new BlockPos(5, 0, 6), Blocks.AIR);                  // no ground under the left foot of the frame
        putRackDown(h, player, new BlockPos(6, 0, 6));
        h.assertTrue(h.getBlockState(new BlockPos(6, 1, 6)).isAir(), "put up with no ground under one of the three at the bottom");
        h.succeed();
    }

    @GameTest(batch = "t021", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void breakingAnyBlockOfTheRackTakesTheWholeFrame(GameTestHelper h) {
        floor(h);
        buildRack(h, new BlockPos(6, 1, 6), net.minecraft.core.Direction.SOUTH);
        h.setBlock(new BlockPos(5, 2, 6), Blocks.AIR);                  // the middle of the left column
        h.runAfterDelay(5, () -> {
            for (int x = 5; x <= 7; x++) {
                for (int y = 1; y <= 3; y++) {
                    h.assertTrue(h.getBlockState(new BlockPos(x, y, 6)).isAir(), "part of the frame was left standing at " + x + ", " + y);
                }
            }
            h.succeed();
        });
    }

    @GameTest(batch = "t022", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aThiefLedToTheRackIsTiedToItStandingInFront(GameTestHelper h) {
        floor(h);
        BlockPos bottom = new BlockPos(5, 1, 5);
        buildRack(h, bottom, net.minecraft.core.Direction.SOUTH);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 9.5)));
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 8));
        thief.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        thief.tieWithRope(player);
        // click the middle block: the bottom one is the one that counts
        h.assertTrue(useRope(h, player, new BlockPos(5, 2, 5), net.minecraft.core.Direction.SOUTH).consumesAction(), "the rope did nothing on the rack");
        h.assertTrue(thief.isRacked() && thief.tiedUp() && !thief.isLeashed(), "the thief is not tied to the rack: racked " + thief.isRacked() + ", tied " + thief.tiedUp() + ", leashed " + thief.isLeashed() + ", holder " + thief.getLeashHolder() + ", ropeTied " + thief.isRopeTied() + ", posted " + thief.isPosted() + ", hung " + thief.isHung() + ", at " + thief.blockPosition().toShortString() + ", alive " + thief.isAlive() + ", removed " + thief.isRemoved() + " || " + RopeItem.lastDecision + " || bound there by " + ThiefEntity.RACKED_BY.get(h.absolutePos(new BlockPos(5, 1, 5))));
        h.runAfterDelay(100, () -> {
            var at = h.relativeVec(thief.position());
            h.assertTrue(Math.abs(at.x - 5.5D) < 0.05D && Math.abs(at.z - (5.5D + 1.0D / 16.0D)) < 0.05D && Math.abs(at.y - (1.0D + RackBlock.LIFT)) < 0.05D, "the thief is not at the rack: " + at);
            h.assertTrue(thief.getYRot() == 0.0F, "the thief does not face out of the rack: " + thief.getYRot());
            // a racked thief can be searched like any tied thief
            player.setShiftKeyDown(true);
            thief.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 5, "a thief on the rack could not be searched");
            h.succeed();
        });
    }

    @GameTest(batch = "t023", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void anEmptyHandLetsAThiefDownFromTheRack(GameTestHelper h) {
        floor(h);
        BlockPos bottom = new BlockPos(5, 1, 5);
        buildRack(h, bottom, net.minecraft.core.Direction.SOUTH);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 9.5)));
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(5, 1, 8));
        thief.tieWithRope(player);
        useRope(h, player, bottom, net.minecraft.core.Direction.SOUTH);
        h.runAfterDelay(20, () -> thief.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND));
        h.runAfterDelay(60, () -> {
            h.assertTrue(!thief.isRacked() && !thief.isRopeTied() && !thief.isNoGravity(), "still on the rack after being let down");
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(5, 1, 5), 4.0D);
            h.succeed();
        });
    }

    @GameTest(batch = "t024", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aThiefOnTheRackIsFreedWhenTheRackIsBroken(GameTestHelper h) {
        floor(h);
        BlockPos bottom = new BlockPos(5, 1, 5);
        buildRack(h, bottom, net.minecraft.core.Direction.SOUTH);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 9.5)));
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(5, 1, 8));
        thief.tieWithRope(player);
        useRope(h, player, bottom, net.minecraft.core.Direction.SOUTH);
        h.runAfterDelay(20, () -> h.setBlock(bottom.above(2).west(), Blocks.AIR));
        h.runAfterDelay(80, () -> {
            h.assertTrue(!thief.isRacked() && !thief.isRopeTied(), "still tied to a rack that is gone");
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(5, 1, 5), 6.0D);
            h.succeed();
        });
    }

    @GameTest(batch = "t025", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void oneRackHoldsOneThief(GameTestHelper h) {
        floor(h);
        BlockPos bottom = new BlockPos(5, 1, 5);
        buildRack(h, bottom, net.minecraft.core.Direction.SOUTH);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 9.5)));
        ThiefEntity first = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(4, 1, 8));
        ThiefEntity second = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(6, 1, 8));
        first.tieWithRope(player);
        useRope(h, player, bottom, net.minecraft.core.Direction.SOUTH);
        second.tieWithRope(player);
        useRope(h, player, bottom, net.minecraft.core.Direction.SOUTH);
        h.assertTrue(first.isRacked() && !second.isRacked(), "a second thief was tied to a rack that was taken");
        h.succeed();
    }

    @GameTest(batch = "t026", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void theWhipShakesLootLooseFromAThiefOnTheRack(GameTestHelper h) {
        floor(h);
        BlockPos bottom = new BlockPos(5, 1, 5);
        buildRack(h, bottom, net.minecraft.core.Direction.SOUTH);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 9.5)));
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 8));
        thief.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        thief.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));
        thief.tieWithRope(player);
        useRope(h, player, bottom, net.minecraft.core.Direction.SOUTH);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.WHIP.get()));
        thief.hurt(h.getLevel().damageSources().playerAttack(player), 1000.0F);
        h.assertTrue(thief.isAlive() && thief.lootCount() == 1, "the whip did not shake a thing loose from a thief on the rack, or killed it");
        h.succeed();
    }

    // ------------------------------------------------ tied all round to a tree or a post ------------------------------------------------

    /** A thief led by the player, who stands a few blocks away. */
    private static ThiefEntity ledThief(GameTestHelper h, net.minecraft.world.entity.player.Player player) {
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 9));
        thief.tieWithRope(player);
        return thief;
    }

    @GameTest(batch = "t027", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aThiefTiedToALogStandsWithItsBackToItAndIsNotHurtByIt(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        ThiefEntity thief = ledThief(h, player);
        h.assertTrue(useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST).consumesAction(), "the rope did nothing on the log");
        h.assertTrue(thief.isPosted() && thief.tiedUp() && !thief.isLeashed(), "the thief is not tied to the log");
        h.runAfterDelay(200, () -> {
            var at = h.relativeVec(thief.position());
            h.assertTrue(Math.abs(at.x - (3 + 1 + 0.26D)) < 0.02D && Math.abs(at.z - 6.5D) < 0.02D && Math.abs(at.y - 1.0D) < 0.02D, "not standing against the east face of the log: " + at);
            h.assertTrue(Math.abs(thief.getYRot() - net.minecraft.core.Direction.EAST.toYRot()) < 0.01F, "not facing away from the log: " + thief.getYRot());
            h.assertTrue(thief.getHealth() == thief.getMaxHealth(), "the thief was hurt standing at the log (suffocating?): " + thief.getHealth());
            h.succeed();
        });
    }

    @GameTest(batch = "t028", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aThinFencePostIsReachedFromItsOwnFaceNotFromTheBlock(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_FENCE);
        var player = h.makeMockPlayer();
        ThiefEntity thief = ledThief(h, player);
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST);
        h.runAfterDelay(100, () -> {
            h.assertTrue(thief.isPosted(), "not tied to the fence");
            double x = h.relativeVec(thief.position()).x;
            h.assertTrue(Math.abs(x - (3 + 0.625D + 0.26D)) < 0.02D, "not against the fence post: x " + x);
            h.assertTrue(thief.getHealth() == thief.getMaxHealth(), "hurt at the fence");
            h.succeed();
        });
    }

    @GameTest(batch = "t029", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void clickingTheTopOfAPillarTiesTheThiefOnTheSideOfThePlayer(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        ThiefEntity thief = ledThief(h, player);        // the player is to the south of the log
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.UP);
        h.assertTrue(thief.isPosted() && thief.postDir() == net.minecraft.core.Direction.SOUTH, "tied on the wrong side: " + thief.postDir());
        h.succeed();
    }

    @GameTest(batch = "t030", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void thereMustBeRoomAndGroundWhereTheThiefWillStand(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(4, 2, 6), Blocks.STONE);        // no room for the head, on the east side
        h.setBlock(new BlockPos(3, 0, 7), Blocks.AIR);          // and no ground on the south side
        var player = h.makeMockPlayer();
        ThiefEntity thief = ledThief(h, player);
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST);
        h.assertTrue(!thief.isPosted(), "tied where there is no room for the head");
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.SOUTH);
        h.assertTrue(!thief.isPosted(), "tied where there is no ground");
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.WEST);
        h.assertTrue(thief.isPosted(), "not tied where there was room: " + RopeItem.lastDecision + " || bound there by " + ThiefEntity.RACKED_BY.get(h.absolutePos(new BlockPos(3, 1, 6))) + " || this thief " + thief.getUUID() + " at " + thief.position());
        h.succeed();
    }

    @GameTest(batch = "t031", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void onePostHoldsOneThiefOnEachSide(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        ThiefEntity first = ledThief(h, player);
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST);
        ThiefEntity second = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(2, 1, 9));
        second.tieWithRope(player);
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST);
        h.assertTrue(first.isPosted() && !second.isPosted(), "two thieves on the same side of the post");
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.WEST);
        h.assertTrue(second.isPosted(), "the other side of the post was not free");
        h.succeed();
    }

    @GameTest(batch = "t032", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aThiefTiedToAPostCanBeSearchedAndLetDownAndIsFreedWhenThePostIsBroken(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(9, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        ThiefEntity thief = ledThief(h, player);
        thief.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST);
        player.setShiftKeyDown(true);
        thief.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 5, "could not search a thief tied to a post");
        player.setShiftKeyDown(false);
        thief.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);        // an empty hand lets it go
        h.assertTrue(!thief.isPosted() && !thief.isRopeTied(), "still tied after being let go");
        h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(4, 1, 6), 4.0D);
        // and a second one, whose post is broken
        ThiefEntity other = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(8, 1, 9));
        other.tieWithRope(player);
        useRope(h, player, new BlockPos(9, 1, 6), net.minecraft.core.Direction.EAST);
        h.assertTrue(other.isPosted(), "the second thief was not tied");
        h.setBlock(new BlockPos(9, 1, 6), Blocks.AIR);
        h.runAfterDelay(40, () -> {
            h.assertTrue(!other.isPosted() && !other.isRopeTied(), "still tied to a post that is gone");
            h.succeed();
        });
    }

    // ------------------------------------------------ rescue and the crew ------------------------------------------------

    /** A thief tied to a log, for the others to rescue. */
    private static ThiefEntity captiveAtALog(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        ThiefEntity captive = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 9));
        captive.tieWithRope(player);
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST);
        return captive;
    }

    @GameTest(batch = "t033", template = "field", setupTicks = 20, timeoutTicks = 1200)
    public static void aFreeThiefCutsATiedOneLoose(GameTestHelper h) {
        ThiefEntity captive = captiveAtALog(h);
        h.assertTrue(captive.isPosted(), "the captive was not tied up");
        StealGoal.requirePlayerNearby = false;
        h.spawn(ModEntities.THIEF.get(), new BlockPos(10, 1, 11));
        h.succeedWhen(() -> {
            h.assertTrue(!captive.isPosted() && !captive.isRopeTied(), "the captive is still tied up");
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(4, 1, 6), 6.0D);
        });
    }

    @GameTest(batch = "t034", template = "field", setupTicks = 20, timeoutTicks = 1500)
    public static void anotherThiefComesToRescueTheOneThatWasCaught(GameTestHelper h) {
        ThiefEntity captive = captiveAtALog(h);
        StealGoal.requirePlayerNearby = false;
        ThiefEntity rescuer = captive.sendRescuer(5, 9);
        h.assertTrue(rescuer != null && rescuer != captive, "no rescuer could be sent");
        h.succeedWhen(() -> h.assertTrue(!captive.isPosted() && !captive.isRopeTied(), "the captive is still tied up (at " + h.relativeVec(captive.position()) + "), the rescuer is at " + h.relativeVec(rescuer.position()) + " doing: [" + rescuer.runningGoals() + "], disguised " + rescuer.isDisguised() + ", glowing " + rescuer.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) + ", negotiating " + rescuer.isNegotiating() + ", vengeful " + rescuer.isVengeful() + ", alive " + rescuer.isAlive() + " || " + rescuer.rescueLog));
    }

    @GameTest(batch = "t035", template = "field", setupTicks = 20, timeoutTicks = 800)
    public static void aCrewMemberBeingChasedHandsItsLootToAMateAndTheRecordFollows(GameTestHelper h) {
        floor(h);
        java.util.UUID crew = java.util.UUID.randomUUID();
        ThiefEntity holder = h.spawn(ModEntities.THIEF.get(), new BlockPos(4, 1, 6));
        ThiefEntity mate = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(9, 1, 6));
        holder.joinCrew(crew, false);
        mate.joinCrew(crew, false);
        holder.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        holder.finishedStealing(h.absolutePos(new BlockPos(1, 1, 1)));
        h.runAfterDelay(10, () -> holder.hurt(h.getLevel().damageSources().generic(), 0.5F));        // chased
        h.succeedWhen(() -> {
            h.assertTrue(!holder.hasLoot() && mate.hasLoot(), "the loot has not gone to the mate yet: holder " + holder.hasLoot() + ", mate " + mate.hasLoot() + ", goals " + holder.runningGoals());
            // the ledger is the whole world's: look for this theft by who has it, not by being the newest
            var records = ThiefLedger.get(h.getLevel()).newest(30);
            h.assertTrue(records.stream().anyMatch(r -> r.thiefId().equals(mate.getUUID())), "the ledger does not point at the one that has it now");
            h.assertTrue(records.stream().noneMatch(r -> r.thiefId().equals(holder.getUUID()) && !r.defeated() && !r.recovered()), "the ledger still points at the one that handed it over");
            h.assertTrue(holder.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) && !mate.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "the decoy should shine, the runner not");
        });
    }

    @GameTest(batch = "t036", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aCrewIsMadeOfRobbersAndOneLookout(GameTestHelper h) {
        floor(h);
        var random = new java.util.Random(5);
        ThiefEntity first = NightVisits.spawnNear(h.getLevel(), h.absolutePos(new BlockPos(7, 1, 7)), 3, 6, null, random);
        h.assertTrue(first != null, "no thief could be put down");
        int n = NightVisits.formCrew(h.getLevel(), first, 3, random);
        h.assertTrue(n == 3, "the crew has " + n + " members, not three");
        java.util.UUID crew = first.crewId();
        h.assertTrue(crew != null, "the first thief has no crew");
        h.assertTrue(first.isAlive() && !first.isRemoved(), "the first thief is gone: " + first.getRemovalReason());
        var members = h.getLevel().getEntitiesOfClass(ThiefEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(7, 1, 7))).inflate(30.0D), e -> crew.equals(e.crewId()));
        h.assertTrue(members.size() == 3, "the crew has " + members.size() + " members that carry its id; the first is at " + first.blockPosition().toShortString() + " and the box is around " + h.absolutePos(new BlockPos(7, 1, 7)).toShortString() + ", all thieves nearby: " + h.getLevel().getEntitiesOfClass(ThiefEntity.class, new net.minecraft.world.phys.AABB(first.blockPosition()).inflate(60.0D)).size());
        h.assertTrue(members.stream().filter(ThiefEntity::isLookout).count() == 1, "a crew of three has not exactly one lookout");
        h.succeed();
    }

    /** A wall round the floor, so that thieves that wander do not walk off the edge of the test area. */
    private static void wallsAround(GameTestHelper h) {
        for (int i = 0; i < 14; i++) {
            for (int y = 1; y <= 2; y++) {
                h.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
                h.setBlock(new BlockPos(i, y, 13), Blocks.STONE);
                h.setBlock(new BlockPos(0, y, i), Blocks.STONE);
                h.setBlock(new BlockPos(13, y, i), Blocks.STONE);
            }
        }
    }

    @GameTest(batch = "t037", template = "field", setupTicks = 20, timeoutTicks = 900)
    public static void theLookoutWatchesWhileTheRobberRobs(GameTestHelper h) {
        floor(h);
        wallsAround(h);
        StealGoal.requirePlayerNearby = false;
        h.setBlock(new BlockPos(10, 0, 10), Blocks.FARMLAND);
        h.setBlock(new BlockPos(10, 1, 10), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        java.util.UUID crew = java.util.UUID.randomUUID();
        ThiefEntity robber = h.spawn(ModEntities.THIEF.get(), new BlockPos(4, 1, 4));
        ThiefEntity lookout = h.spawn(ModEntities.THIEF.get(), new BlockPos(3, 1, 4));
        robber.joinCrew(crew, false);
        lookout.joinCrew(crew, true);
        h.runAfterDelay(800, () -> {
            h.assertTrue(robber.hasLoot(), "the robber did not rob the field");
            h.assertTrue(!lookout.hasLoot() && !lookout.hasStolen(), "the lookout stole");
            h.succeed();
        });
    }

    @GameTest(batch = "t038", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aCrewDrawnBackByTheLookoutDoesNotSteal(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        h.setBlock(new BlockPos(8, 0, 8), Blocks.FARMLAND);
        h.setBlock(new BlockPos(8, 1, 8), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        java.util.UUID crew = java.util.UUID.randomUUID();
        ThiefEntity robber = h.spawn(ModEntities.THIEF.get(), new BlockPos(4, 1, 4));
        ThiefEntity lookout = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(2, 1, 4));
        robber.joinCrew(crew, false);
        lookout.joinCrew(crew, true);
        lookout.alertCrew();
        h.assertTrue(robber.alertTicks() > 0, "the whistle did not reach the robber");
        h.runAfterDelay(150, () -> {
            h.assertTrue(!robber.hasLoot(), "an alerted robber stole during the alert");
            h.succeed();
        });
    }

    @GameTest(batch = "t039", template = "field", setupTicks = 20, timeoutTicks = 700)
    public static void theLookoutStaysCloseToTheRobbers(GameTestHelper h) {
        floor(h);
        wallsAround(h);
        StealGoal.requirePlayerNearby = false;
        java.util.UUID crew = java.util.UUID.randomUUID();
        ThiefEntity robber = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(11, 1, 11));
        ThiefEntity lookout = h.spawn(ModEntities.THIEF.get(), new BlockPos(2, 1, 2));
        robber.joinCrew(crew, false);
        lookout.joinCrew(crew, true);
        h.runAfterDelay(500, () -> {
            double d = Math.sqrt(lookout.distanceToSqr(robber));
            h.assertTrue(d < 13.0D, "the lookout stayed " + d + " blocks from the robber");
            h.succeed();
        });
    }

    // ------------------------------------------------ speed ------------------------------------------------

    /**
     * Blocks per second a thief really covers on level ground at this speed factor: the fastest of three stretches of a second, in case something
     * in the shared test area gets in its way once.
     */
    private static void measure(GameTestHelper h, ThiefEntity thief, double factor, java.util.function.DoubleConsumer result) {
        thief.clearGoalsForTest();
        h.runAfterDelay(5, () -> thief.getNavigation().moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(12.5, 1, thief.getZ() - h.absoluteVec(net.minecraft.world.phys.Vec3.ZERO).z)).x,
                thief.getY(), thief.getZ(), factor));
        double[] best = new double[1];
        double[] from = new double[1];
        for (int tick : new int[]{15, 25, 35}) {
            h.runAfterDelay(tick, () -> from[0] = thief.getX());
            h.runAfterDelay(tick + 20, () -> best[0] = Math.max(best[0], (thief.getX() - from[0]) / 1.0D));
        }
        h.runAfterDelay(60, () -> result.accept(best[0]));
    }

    @GameTest(batch = "t040", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aCrewMemberRunsAboutAsFastAsAPlayerWalksAndNotFasterThanOneSprints(GameTestHelper h) {
        floor(h);
        wallsAround(h);
        ThiefEntity lone = h.spawn(ModEntities.THIEF.get(), new BlockPos(1, 1, 4));
        ThiefEntity crew = h.spawn(ModEntities.THIEF.get(), new BlockPos(1, 1, 8));
        crew.joinCrew(java.util.UUID.randomUUID(), false);
        double[] speeds = new double[2];
        measure(h, lone, 1.15D, v -> speeds[0] = v);
        measure(h, crew, 1.15D, v -> speeds[1] = v);
        h.runAfterDelay(70, () -> {
            String report = String.format("measured: lone %.2f, crew %.2f blocks/s; from the attributes: lone %.2f, crew %.2f (a player walks 4.32 and sprints 5.61)", speeds[0], speeds[1],
                    lone.estimatedSpeed(1.15D), crew.estimatedSpeed(1.15D));
            // what the speeds should be, worked out from the attributes: a crew member about as fast as a player walks, a lone thief slower, neither near a sprint
            h.assertTrue(crew.estimatedSpeed(1.15D) > lone.estimatedSpeed(1.15D) + 0.5D, "a crew member is not faster than a lone thief: " + report);
            h.assertTrue(crew.estimatedSpeed(1.15D) > 3.9D && crew.estimatedSpeed(1.15D) < 4.7D, "a crew member does not run at about walking pace: " + report);
            h.assertTrue(crew.estimatedSpeed(1.15D) < 5.5D, "a crew member is as fast as a sprinting player: " + report);
            // what is measured can only be slower than that (something in the shared test area may be in the way), never faster, and they did move
            h.assertTrue(speeds[1] <= crew.estimatedSpeed(1.15D) + 0.5D && speeds[0] <= lone.estimatedSpeed(1.15D) + 0.5D, "the formula is wrong: a thief is faster than it says: " + report);
            h.assertTrue(speeds[1] > 2.5D && speeds[0] > 2.0D, "they hardly moved: " + report);
            h.succeed();
        });
    }

    @GameTest(batch = "t041", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void renamedTheThreeSecondCutTakesLongerThanAnInstant(GameTestHelper h) {
        ThiefEntity captive = captiveAtALog(h);
        StealGoal.requirePlayerNearby = false;
        h.spawn(ModEntities.THIEF.get(), new BlockPos(10, 1, 11));
        h.runAfterDelay(30, () -> h.assertTrue(captive.isPosted(), "freed far too quickly: the cutting takes three seconds of standing beside it"));
        h.runAfterDelay(35, h::succeed);
    }

    // ------------------------------------------------ other creatures: zombies, villagers ------------------------------------------------

    private static net.minecraft.world.entity.Mob led(GameTestHelper h, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, net.minecraft.world.entity.player.Player player) {
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        var mob = h.spawn(type, new BlockPos(5, 1, 9));
        var result = ModItems.ROPE.get().interactLivingEntity(new net.minecraft.world.item.ItemStack(ModItems.ROPE.get()), player, mob, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(result.consumesAction(), "the rope did nothing on " + type);
        return mob;
    }

    @GameTest(batch = "t042", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aRopeTiesAHuskAndAVillagerButNotACow(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);
        h.assertTrue(Captives.state(husk).mode() == Captives.Mode.LED && husk.isNoAi() && Captives.holder(husk) == player && !husk.isLeashed(), "the husk is not tied and led");
        var villager = led(h, net.minecraft.world.entity.EntityType.VILLAGER, player);
        h.assertTrue(Captives.state(villager).mode() == Captives.Mode.LED && villager.isNoAi(), "the villager is not tied");
        var cow = h.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(7, 1, 9));
        var result = ModItems.ROPE.get().interactLivingEntity(new net.minecraft.world.item.ItemStack(ModItems.ROPE.get()), player, cow, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(!result.consumesAction() && !Captives.isBound(cow), "a cow was tied with the rope");
        h.succeed();
    }

    @GameTest(batch = "t043", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aHuskTiedToALogStandsAgainstItAndIsNotHurtByIt(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);
        h.assertTrue(useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST).consumesAction(), "the rope did nothing on the log");
        h.assertTrue(Captives.state(husk).mode() == Captives.Mode.POST && !husk.isLeashed(), "the husk is not tied to the log");
        h.runAfterDelay(200, () -> {
            var at = h.relativeVec(husk.position());
            h.assertTrue(Math.abs(at.x - (3 + 1 + 0.26D)) < 0.03D && Math.abs(at.z - 6.5D) < 0.03D && Math.abs(at.y - 1.0D) < 0.03D, "not standing against the east face of the log: " + at);
            h.assertTrue(husk.getHealth() == husk.getMaxHealth() && husk.isNoAi(), "the husk was hurt, or its AI is on: " + husk.getHealth());
            h.succeed();
        });
    }

    @GameTest(batch = "t044", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aVillagerOnTheRackStandsInFrontOfItAndOneHungUpHangsClear(GameTestHelper h) {
        floor(h);
        RackBlock.build(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), net.minecraft.core.Direction.SOUTH);
        h.setBlock(new BlockPos(11, 6, 11), Blocks.STONE);
        var player = h.makeMockPlayer();
        var villager = led(h, net.minecraft.world.entity.EntityType.VILLAGER, player);
        useRope(h, player, new BlockPos(6, 2, 6), net.minecraft.core.Direction.SOUTH);
        var other = led(h, net.minecraft.world.entity.EntityType.VINDICATOR, player);
        useRope(h, player, new BlockPos(11, 6, 11), net.minecraft.core.Direction.DOWN);
        h.runAfterDelay(100, () -> {
            h.assertTrue(Captives.state(villager).mode() == Captives.Mode.RACK, "the villager is not on the rack");
            var at = h.relativeVec(villager.position());
            h.assertTrue(Math.abs(at.x - 6.5D) < 0.05D && Math.abs(at.z - (6.5D + 1.0D / 16.0D)) < 0.05D && Math.abs(at.y - (1.0D + RackBlock.LIFT)) < 0.05D, "the villager is not at the rack: " + at);
            h.assertTrue(Captives.state(other).mode() == Captives.Mode.HUNG, "the vindicator is not hung");
            double feet = h.relativeVec(other.position()).y;
            h.assertTrue(feet > 1.5D, "the vindicator hangs with its feet on the floor, at " + feet);
            h.succeed();
        });
    }

    @GameTest(batch = "t045", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void anEmptyHandLetsAGoAndSneakingSearchesWhatItWearsAndCarries(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);
        husk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        var villager = led(h, net.minecraft.world.entity.EntityType.VILLAGER, player);
        ((net.minecraft.world.entity.npc.Villager) villager).getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 3));
        player.setShiftKeyDown(true);
        net.minecraftforge.common.ForgeHooks.onInteractEntityAt(player, husk, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_HELMET) == 1 && husk.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(), "the helmet was not searched off the husk");
        net.minecraftforge.common.ForgeHooks.onInteractEntityAt(player, villager, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.BREAD) == 3, "the bread was not searched out of the villager's pockets");
        player.setShiftKeyDown(false);
        var result = net.minecraftforge.common.ForgeHooks.onInteractEntityAt(player, husk, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(result != null && !Captives.isBound(husk) && !husk.isNoAi() && !husk.isLeashed(), "an empty hand did not let the husk go and give it its AI back");
        h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(5, 1, 9), 6.0D);
        h.succeed();
    }

    @GameTest(batch = "t046", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void theWhipDoesNotKillATiedCreatureShakesSomethingLooseAndKillsAFreeOne(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);
        husk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.WHIP.get()));
        husk.hurt(h.getLevel().damageSources().playerAttack(player), 1000.0F);
        h.assertTrue(husk.isAlive() && husk.getHealth() >= 1.0F, "the whip killed a tied husk");
        h.assertTrue(husk.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(), "nothing was shaken loose from the tied husk");
        h.assertItemEntityPresent(net.minecraft.world.item.Items.IRON_HELMET, new BlockPos(5, 1, 9), 6.0D);
        var free = h.spawn(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(8, 1, 9));
        free.hurt(h.getLevel().damageSources().playerAttack(player), 1000.0F);
        h.assertTrue(!free.isAlive(), "the whip did not hurt a free husk the way a weapon does");
        h.succeed();
    }

    @GameTest(batch = "t047", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aLedCreatureIsFreedWhenTheRopeIsStretchedTooFar(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);
        h.runAfterDelay(10, () -> player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)).add(0, 0, -30)));
        h.runAfterDelay(40, () -> {
            h.assertTrue(!Captives.isBound(husk) && !husk.isNoAi() && !husk.isLeashed(), "still tied after the rope was stretched too far");
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(5, 1, 9), 6.0D);
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(husk.blockPosition()).inflate(8.0D),
                    e -> e.getItem().is(net.minecraft.world.item.Items.LEAD)).isEmpty(), "a lead was dropped instead of the rope");
            h.succeed();
        });
    }

    @GameTest(batch = "t048", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aCreatureTiedToALogIsFreedWhenTheLogIsBroken(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST);
        h.runAfterDelay(20, () -> h.setBlock(new BlockPos(3, 1, 6), Blocks.AIR));
        h.runAfterDelay(60, () -> {
            h.assertTrue(!Captives.isBound(husk) && !husk.isNoAi() && !husk.isNoGravity(), "still tied to a log that is gone");
            h.succeed();
        });
    }

    @GameTest(batch = "t049", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aLedCreatureFollowsTheOneWhoLeadsIt(GameTestHelper h) {
        floor(h);
        wallsAround(h);
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);
        h.runAfterDelay(5, () -> player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(11.5, 1, 9.5))));
        h.runAfterDelay(150, () -> {
            double d = Math.sqrt(husk.distanceToSqr(player));
            h.assertTrue(Captives.isBound(husk), "the husk was freed instead of led");
            h.assertTrue(d < 4.2D && d > 1.0D, "the husk did not follow: it is " + d + " blocks from the one who leads it");
            h.assertTrue(husk.isNoAi(), "the husk's AI is on while it is led");
            h.assertTrue(h.relativeVec(husk.position()).y < 1.2D, "the husk is not on the ground: " + h.relativeVec(husk.position()).y);
            h.succeed();
        });
    }

    @GameTest(batch = "t050", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aLedCreatureFallsToTheGroundAndDoesNotHover(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        var husk = h.spawn(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(3, 5, 9));          // four blocks up
        ModItems.ROPE.get().interactLivingEntity(new net.minecraft.world.item.ItemStack(ModItems.ROPE.get()), player, husk, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.runAfterDelay(60, () -> {
            h.assertTrue(h.relativeVec(husk.position()).y < 1.2D, "a creature that is led hangs in the air at " + h.relativeVec(husk.position()).y);
            h.succeed();
        });
    }

    @GameTest(batch = "t051", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aLedCreatureJumpsUpABlockThatIsInItsWay(GameTestHelper h) {
        floor(h);
        wallsAround(h);
        for (int z = 1; z <= 12; z++) {
            h.setBlock(new BlockPos(7, 1, z), Blocks.STONE);        // a ledge one block high right across the way
        }
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);          // at x = 5, the player at 3.5
        h.runAfterDelay(5, () -> player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(10.5, 2, 9.5))));        // on the ledge's far side, up on the ledge's level
        h.runAfterDelay(200, () -> {
            h.assertTrue(Captives.isBound(husk), "the husk was freed instead of led");
            var at = h.relativeVec(husk.position());
            // up on the ledge (a block is 1 high, so the feet are at 2) and past its near edge: it stops there, being then within three blocks of the one who leads it
            h.assertTrue(at.y > 1.9D && at.x > 7.0D, "the husk did not get up onto the ledge: it is at " + at);
            h.succeed();
        });
    }

    @GameTest(batch = "t052", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void aLedCreatureDoesNotJumpWhereThereIsNothingInItsWay(GameTestHelper h) {
        floor(h);
        wallsAround(h);
        var player = h.makeMockPlayer();
        var husk = led(h, net.minecraft.world.entity.EntityType.HUSK, player);
        double[] highest = new double[1];
        for (int t2 = 5; t2 < 120; t2 += 5) {
            h.runAfterDelay(t2, () -> highest[0] = Math.max(highest[0], h.relativeVec(husk.position()).y));
        }
        h.runAfterDelay(5, () -> player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(11.5, 1, 9.5))));
        h.runAfterDelay(130, () -> {
            h.assertTrue(highest[0] < 1.1D, "the husk hopped along on flat ground, up to " + highest[0]);
            h.succeed();
        });
    }

    @GameTest(batch = "t053", template = "field", setupTicks = 20, timeoutTicks = 1500)
    public static void aFreeThiefCutsAHungOneDownEvenWhenItLooksLikeASheep(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        BlockPos ceiling = new BlockPos(5, 6, 5);
        h.setBlock(ceiling, Blocks.STONE);
        ThiefEntity captive = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 5));
        captive.hangFrom(h.absolutePos(ceiling));
        ThiefEntity rescuer = h.spawn(ModEntities.THIEF.get(), new BlockPos(11, 1, 11));
        rescuer.disguiseAs(net.minecraft.world.entity.EntityType.SHEEP);
        h.assertTrue(rescuer.isDisguised(), "the rescuer is not disguised");
        h.succeedWhen(() -> {
            h.assertTrue(!captive.isHung() && !captive.isRopeTied(), "the hung one is still hung; the rescuer is at " + rescuer.blockPosition().toShortString() + " doing: " + rescuer.runningGoals());
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(5, 1, 5), 6.0D);
            h.assertTrue(!rescuer.isDisguised(), "the rescuer still looks like a sheep after cutting the rope");
        });
    }

    @GameTest(batch = "t054", template = "field", setupTicks = 20, timeoutTicks = 1500)
    public static void aFreeThiefCutsOneOffTheRack(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        RackBlock.build(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 4)), net.minecraft.core.Direction.SOUTH);
        ThiefEntity captive = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(6, 1, 8));
        captive.bindToRack(h.absolutePos(new BlockPos(6, 1, 4)), net.minecraft.core.Direction.SOUTH);
        ThiefEntity rescuer = h.spawn(ModEntities.THIEF.get(), new BlockPos(11, 1, 11));
        StringBuilder why = new StringBuilder();
        h.runAfterDelay(3, () -> {
            for (BlockPos rel : new BlockPos[]{new BlockPos(6, 1, 5), new BlockPos(6, 1, 4), new BlockPos(6, 1, 3), new BlockPos(7, 1, 5)}) {
                var spot = h.absolutePos(rel);
                why.append(rel.toShortString()).append(": stand ").append(!h.getLevel().getBlockState(spot).blocksMotion()).append(" reach ").append(Targets.reach(rescuer, spot, false)).append("; ");
            }
            why.append("tied ").append(captive.tiedUp()).append(" racked ").append(captive.isRacked()).append(" alive ").append(captive.isAlive()).append(" range ").append(RescueGoal.searchRange)
                    .append(" dist ").append(Math.sqrt(rescuer.distanceToSqr(captive)));
        });
        h.succeedWhen(() -> {
            h.assertTrue(!captive.isRacked() && !captive.isRopeTied(), "the one on the rack is still tied; captive at " + h.relativeVec(captive.position()) + ", rescuer at "
                    + h.relativeVec(rescuer.position()) + " doing: [" + rescuer.runningGoals() + "], rescuer tied " + rescuer.tiedUp() + ", alert " + rescuer.alertTicks() + " || " + why + " || " + rescuer.rescueLog);
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(6, 1, 4), 6.0D);
        });
    }

    @GameTest(batch = "t055", template = "field", setupTicks = 20, timeoutTicks = 700)
    public static void tiedUpZombiesAndSkeletonsBurnInTheSunAndTheOnesThatDoNotStillDoNot(GameTestHelper h) {
        floor(h);
        Captives.forceSun = Boolean.TRUE;
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 11.5)));
        java.util.List<net.minecraft.world.entity.Mob> burners = new java.util.ArrayList<>();
        java.util.List<net.minecraft.world.entity.Mob> immune = new java.util.ArrayList<>();
        int x = 1;
        for (var type : java.util.List.of(net.minecraft.world.entity.EntityType.ZOMBIE, net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER, net.minecraft.world.entity.EntityType.SKELETON,
                net.minecraft.world.entity.EntityType.HUSK, net.minecraft.world.entity.EntityType.STRAY, net.minecraft.world.entity.EntityType.WITHER_SKELETON)) {
            var mob = h.spawn((net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>) type, new BlockPos(x, 1, 6));
            ModItems.ROPE.get().interactLivingEntity(new net.minecraft.world.item.ItemStack(ModItems.ROPE.get()), player, mob, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.setBlock(new BlockPos(x, 7, 3), Blocks.STONE);
            Captives.hang(mob, h.absolutePos(new BlockPos(x, 7, 3)));
            (x <= 5 ? burners : immune).add(mob);
            x += 2;
        }
        // one with a helmet: it does not burn (the helmet is worn down instead)
        var helmeted = h.spawn(net.minecraft.world.entity.EntityType.SKELETON, new BlockPos(13, 1, 6));
        helmeted.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        ModItems.ROPE.get().interactLivingEntity(new net.minecraft.world.item.ItemStack(ModItems.ROPE.get()), player, helmeted, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.setBlock(new BlockPos(13, 7, 3), Blocks.STONE);
        Captives.hang(helmeted, h.absolutePos(new BlockPos(13, 7, 3)));
        boolean[] burnedAtAnyTime = new boolean[6];
        java.util.List<net.minecraft.world.entity.Mob> all = new java.util.ArrayList<>(burners);
        all.addAll(immune);
        for (int tick = 5; tick <= 500; tick += 5) {
            h.runAfterDelay(tick, () -> {
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).isOnFire() || !all.get(i).isAlive()) {
                        burnedAtAnyTime[i] = true;          // a burning thing goes out after eight seconds and is lit again a little later: look often
                    }
                }
            });
        }
        h.runAfterDelay(505, () -> {
            Captives.forceSun = null;
            for (int i = 0; i < all.size(); i++) {
                boolean shouldBurn = i < burners.size();
                h.assertTrue(burnedAtAnyTime[i] == shouldBurn, all.get(i).getType() + (shouldBurn ? " tied up in the sun did not burn" : " burned, but does not burn in the sun"));
            }
            h.assertTrue(!helmeted.isOnFire(), "a skeleton in a helmet burned");
            h.succeed();
        });
    }

    // ------------------------------------------------ bounty, questions, resentment, ransom ------------------------------------------------

    private static final net.minecraft.world.item.Item EMERALD = net.minecraft.world.item.Items.EMERALD;

    private static ThiefEntity ledCaptive(GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos at) {
        ThiefEntity t = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), at);
        t.tieWithRope(player);
        return t;
    }

    @GameTest(batch = "t056", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aThiefLedToTheBountyBoardIsTakenForEmeraldsAndItsLootGoesToThePlayer(GameTestHelper h) {
        floor(h);
        var board = ModBlocks.BOUNTY_BOARD.get().defaultBlockState();
        h.setBlock(new BlockPos(6, 1, 6), board);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 8.5)));
        ThiefEntity lone = ledCaptive(h, player, new BlockPos(6, 1, 9));
        lone.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        var hit = hitOn(h, new BlockPos(6, 1, 6), net.minecraft.core.Direction.SOUTH);
        var result = board.getBlock().use(board, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(result.consumesAction(), "the board did nothing");
        h.assertTrue(lone.isRemoved(), "the thief was not taken away");
        h.assertTrue(player.getInventory().countItem(EMERALD) == 3, "the bounty for a lone thief is not 3 emeralds: " + player.getInventory().countItem(EMERALD));
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 5, "what the thief carried did not come to the player");
        h.assertTrue(player.getInventory().countItem(ModItems.ROPE.get()) == 1, "the rope did not come back");
        // a member of a crew is worth more
        ThiefEntity member = ledCaptive(h, player, new BlockPos(7, 1, 9));
        member.joinCrew(java.util.UUID.randomUUID(), false);
        board.getBlock().use(board, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(player.getInventory().countItem(EMERALD) == 3 + 5, "the bounty for a crew member is not 5 emeralds: " + player.getInventory().countItem(EMERALD));
        h.succeed();
    }

    @GameTest(batch = "t057", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void theBoardPaysNothingForAThiefThatIsNotLedTiedUp(GameTestHelper h) {
        floor(h);
        var board = ModBlocks.BOUNTY_BOARD.get().defaultBlockState();
        h.setBlock(new BlockPos(6, 1, 6), board);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 8.5)));
        ThiefEntity free = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(6, 1, 9));        // not tied
        board.getBlock().use(board, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), player, net.minecraft.world.InteractionHand.MAIN_HAND, hitOn(h, new BlockPos(6, 1, 6), net.minecraft.core.Direction.SOUTH));
        h.assertTrue(!free.isRemoved() && player.getInventory().countItem(EMERALD) == 0, "a free thief was paid for");
        h.succeed();
    }

    @GameTest(batch = "t058", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aLashMakesATiedThiefTellWhereTheOthersAreAndTheyShine(GameTestHelper h) {
        floor(h);
        ThiefEntity.confessChanceOverride = 1.0D;
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        ThiefEntity captive = ledCaptive(h, player, new BlockPos(4, 1, 9));
        ThiefEntity other = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(12, 1, 3));
        ThiefEntity another = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(10, 1, 12));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.WHIP.get()));
        captive.hurt(h.getLevel().damageSources().playerAttack(player), 1.0F);
        ThiefEntity.confessChanceOverride = null;
        h.assertTrue(other.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) && another.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "the others do not shine after it told");
        h.assertTrue(!captive.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING) || captive.hasStolen(), "the one that told shines too");
        h.assertTrue(captive.grudge() >= 12.0F, "the lash did not add to its resentment: " + captive.grudge());
        h.succeed();
    }

    @GameTest(batch = "t059", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aThiefThatTellsOnlyTellsOnce(GameTestHelper h) {
        floor(h);
        ThiefEntity.confessChanceOverride = 1.0D;
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        ThiefEntity captive = ledCaptive(h, player, new BlockPos(4, 1, 9));
        ThiefEntity other = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(12, 1, 3));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.WHIP.get()));
        captive.hurt(h.getLevel().damageSources().playerAttack(player), 1.0F);
        other.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        captive.invulnerableTime = 0;
        captive.hurt(h.getLevel().damageSources().playerAttack(player), 1.0F);
        ThiefEntity.confessChanceOverride = null;
        h.assertTrue(!other.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "it told a second time");
        h.succeed();
    }

    @GameTest(batch = "t060", template = "field", setupTicks = 20, timeoutTicks = 700)
    public static void resentmentGrowsFasterOnARackAndDecidesWhatItDoesWhenLetGo(GameTestHelper h) {
        floor(h);
        RackBlock.build(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 4)), net.minecraft.core.Direction.SOUTH);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 11.5)));
        ThiefEntity racked = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(6, 1, 8));
        racked.rememberCaptor(player);
        racked.bindToRack(h.absolutePos(new BlockPos(6, 1, 4)), net.minecraft.core.Direction.SOUTH);
        h.runAfterDelay(260, () -> {
            h.assertTrue(racked.grudge() >= 20.0F, "after 13 seconds on a rack it is not resentful: " + racked.grudge());
            racked.addGrudge(40.0F);
            racked.giveGoalsBackForTest();        // it was spawned without a mind of its own, to be left alone; now it has one
            ThiefEntity.grudgeRollOverride = 0.0D;        // the chance has to come out as the attack
            racked.interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);        // the player lets it down
            ThiefEntity.grudgeRollOverride = null;
            h.assertTrue(!racked.isRacked(), "it was not let down");
            h.assertTrue(racked.isVengeful() && racked.getTarget() == player, "a thief with that much resentment does not go for the one who tied it");
        });
        h.runAfterDelay(380, () -> {
            double d = Math.sqrt(racked.distanceToSqr(player));
            h.assertTrue(d < 3.0D, "the vengeful thief did not come at the player: it is " + d + " blocks away");
            h.succeed();
        });
    }

    @GameTest(batch = "t061", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aMiddlingGrudgeRunsFasterButNotFasterThanASprintingPlayerAndALittleDoesNothing(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        // the crew has the faster base: the cap has to hold for it too
        ThiefEntity crew = ledCaptive(h, player, new BlockPos(4, 1, 9));
        crew.joinCrew(java.util.UUID.randomUUID(), false);
        ThiefEntity lone = ledCaptive(h, player, new BlockPos(6, 1, 9));
        ThiefEntity little = ledCaptive(h, player, new BlockPos(8, 1, 9));
        double crewBase = crew.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double loneBase = lone.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        crew.addGrudge(40.0F);
        lone.addGrudge(40.0F);
        little.addGrudge(10.0F);
        ThiefEntity.grudgeRollOverride = 0.99D;        // the chance comes out as running, not the attack
        for (ThiefEntity t : new ThiefEntity[]{crew, lone, little}) {
            t.dropLeash(true, false);        // let go
        }
        ThiefEntity.grudgeRollOverride = null;
        double crewNow = crew.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double loneNow = lone.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        h.assertTrue(lone.isHurried() && loneNow > loneBase, "a lone thief with a middling grudge is not faster: " + loneBase + " -> " + loneNow);
        h.assertTrue(crew.isHurried() && crewNow > crewBase - 0.0001D, "a crew member with a middling grudge is slower: " + crewBase + " -> " + crewNow);
        h.assertTrue(crew.estimatedSpeed(1.15D) < 5.6D && lone.estimatedSpeed(1.15D) < 5.6D, "a hurried thief outruns a sprinting player: crew " + crew.estimatedSpeed(1.15D) + ", lone " + lone.estimatedSpeed(1.15D));
        h.assertTrue(!little.isHurried() && !little.isVengeful(), "a little resentment did something");
        h.assertTrue(!lone.isVengeful() && !crew.isVengeful(), "the roll said run, and it attacked");
        h.succeed();
    }

    @GameTest(batch = "t062", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void movingAThiefFromTheLeadToAPostDoesNotCountAsLettingItGo(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        ThiefEntity captive = ledCaptive(h, player, new BlockPos(5, 1, 9));
        captive.addGrudge(80.0F);
        useRope(h, player, new BlockPos(3, 1, 6), net.minecraft.core.Direction.EAST);
        h.assertTrue(captive.isPosted(), "it was not tied to the post");
        h.assertTrue(captive.grudge() >= 80.0F && !captive.isVengeful(), "moving it from the lead to the post set it free in effect: grudge " + captive.grudge());
        h.succeed();
    }

    /** A crew with a captive, two free mates that carry things, and the player who has the captive. */
    private static ThiefEntity[] crewWithACaptive(GameTestHelper h, net.minecraft.world.entity.player.Player player) {
        java.util.UUID crew = java.util.UUID.randomUUID();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 6.5)));
        ThiefEntity captive = ledCaptive(h, player, new BlockPos(6, 1, 7));
        ThiefEntity lookout = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(11, 1, 11));
        ThiefEntity robber = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(2, 1, 11));
        captive.joinCrew(crew, false);
        lookout.joinCrew(crew, true);
        robber.joinCrew(crew, false);
        robber.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        lookout.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));
        return new ThiefEntity[]{captive, lookout, robber};
    }

    @GameTest(batch = "t063", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void whenACrewMemberIsCaughtTheLookoutHoldsUpASignAndOffersLootForIt(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        var c = crewWithACaptive(h, player);
        h.assertTrue(c[0].offerRansom(), "no offer was made");
        h.assertTrue(c[1].isNegotiating() && c[0].ransomActive(), "the lookout does not hold the sign, or the offer does not stand");
        h.assertTrue(c[1].getMainHandItem().is(net.minecraft.world.item.Items.OAK_SIGN), "the sign is not in its hand");
        h.assertTrue(!c[2].isNegotiating(), "two of them are negotiating");
        h.succeed();
    }

    @GameTest(batch = "t064", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void noOfferIsMadeWhenNobodyCarriesAnything(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        var c = crewWithACaptive(h, player);
        c[1].clearLootForTest();
        c[2].clearLootForTest();
        h.assertTrue(!c[0].offerRansom() && !c[0].ransomActive(), "an offer was made with nothing to offer");
        h.succeed();
    }

    @GameTest(batch = "t065", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void lettingTheCaptiveGoWhileTheOfferStandsGetsTheLootBack(GameTestHelper h) {
        floor(h);
        ThiefEntity.ransomShareOverride = 1.0D;
        var player = h.makeMockPlayer();
        var c = crewWithACaptive(h, player);
        c[0].offerRansom();
        c[0].interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);          // an empty hand
        ThiefEntity.ransomShareOverride = null;
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 5 && player.getInventory().countItem(net.minecraft.world.item.Items.GOLD_INGOT) == 3,
                "the loot did not come back: iron " + player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) + ", gold " + player.getInventory().countItem(net.minecraft.world.item.Items.GOLD_INGOT));
        h.assertTrue(!c[1].hasLoot() && !c[2].hasLoot(), "the mates still carry it");
        h.assertTrue(!c[1].isNegotiating() && !c[0].ransomActive(), "the offer still stands after it was taken");
        h.succeed();
    }

    @GameTest(batch = "t066", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aBlowToTheOneWithTheSignEndsTheTalk(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        var c = crewWithACaptive(h, player);
        c[0].offerRansom();
        c[1].hurt(h.getLevel().damageSources().generic(), 1.0F);
        h.assertTrue(!c[1].isNegotiating() && !c[0].ransomActive(), "the talk went on after a blow");
        // and with no offer standing, letting it go gets nothing
        c[0].interactAt(player, NOWHERE, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 0, "loot was handed over without an offer");
        h.succeed();
    }

    @GameTest(batch = "t067", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void theOfferRunsOutAndMeanwhileTheOthersDoNotFreeTheCaptive(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        java.util.UUID crew = java.util.UUID.randomUUID();
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 12.5)));
        ThiefEntity captive = ledCaptive(h, player, new BlockPos(6, 1, 7));
        captive.joinCrew(crew, false);
        ThiefEntity free = h.spawn(ModEntities.THIEF.get(), new BlockPos(6, 1, 10));          // would rescue it, but for the offer
        free.joinCrew(crew, false);
        free.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
        h.assertTrue(captive.offerRansom() && free.isNegotiating(), "no offer");
        // the free one stands there; it is tied by a lead, which the rescuer cuts off the one that holds it, so look at the captive itself after the time it would take
        h.runAfterDelay(150, () -> {
            h.assertTrue(captive.tiedUp(), "the captive was freed while the offer stood");
            h.succeed();
        });
    }

    @GameTest(batch = "t068", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void whatAThiefTellsIsOnlyAboutTheOnesThatAreFreeNotThoseTiedUpToo(GameTestHelper h) {
        floor(h);
        ThiefEntity.confessChanceOverride = 1.0D;
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 9.5)));
        ThiefEntity first = ledCaptive(h, player, new BlockPos(4, 1, 9));
        ThiefEntity second = ledCaptive(h, player, new BlockPos(8, 1, 9));          // tied up too: the player has just tied it
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.WHIP.get()));
        first.hurt(h.getLevel().damageSources().playerAttack(player), 1.0F);
        h.assertTrue(!second.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "it gave away the one that was tied up beside it");
        // nothing to tell, so it has not told: a free one that comes later is still news
        ThiefEntity free = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(12, 1, 3));
        first.invulnerableTime = 0;
        first.hurt(h.getLevel().damageSources().playerAttack(player), 1.0F);
        ThiefEntity.confessChanceOverride = null;
        h.assertTrue(free.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "it did not tell about the free one, though it had told nothing before");
        h.assertTrue(!second.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "it gave away the one that was tied up, the second time");
        h.succeed();
    }

    /** A crew that has not been tied up yet: the captive is part of it from the start, as a crew member is. */
    private static ThiefEntity[] crewForTheTimer(GameTestHelper h, net.minecraft.world.entity.player.Player player, boolean mateCarries) {
        java.util.UUID crew = java.util.UUID.randomUUID();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 6.5)));
        ThiefEntity captive = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(6, 1, 7));
        captive.joinCrew(crew, false);               // before it is tied: that is when the offer is decided
        captive.rescueProofForTest = true;
        ThiefEntity lookout = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(11, 1, 11));
        lookout.joinCrew(crew, true);
        if (mateCarries) {
            lookout.addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));
        }
        captive.tieWithRope(player);
        return new ThiefEntity[]{captive, lookout};
    }

    @GameTest(batch = "t069", template = "field", setupTicks = 20, timeoutTicks = 700)
    public static void theOfferComesByItselfAShortTimeAfterTheCapture(GameTestHelper h) {
        floor(h);
        ThiefEntity.ransomChanceOverride = 1.0D;
        var player = h.makeMockPlayer();
        var c = crewForTheTimer(h, player, true);
        ThiefEntity.ransomChanceOverride = null;
        h.runAfterDelay(30, () -> h.assertTrue(!c[1].isNegotiating(), "the offer came at once, not after ten seconds or more"));
        h.succeedWhen(() -> h.assertTrue(c[1].isNegotiating() && c[0].ransomActive(), "no offer yet: " + c[0].ransomState()));
    }

    @GameTest(batch = "t070", template = "field", setupTicks = 20, timeoutTicks = 900)
    public static void aCrewThatHasStolenNothingYetOffersAsSoonAsItHasSomething(GameTestHelper h) {
        floor(h);
        ThiefEntity.ransomChanceOverride = 1.0D;
        var player = h.makeMockPlayer();
        var c = crewForTheTimer(h, player, false);
        ThiefEntity.ransomChanceOverride = null;
        h.runAfterDelay(500, () -> {
            h.assertTrue(!c[1].isNegotiating(), "an offer with nothing to offer");
            c[1].addLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));         // it steals something now
        });
        h.succeedWhen(() -> h.assertTrue(c[1].isNegotiating(), "no offer after the crew had something: " + c[0].ransomState()));
    }

    @GameTest(batch = "t071", template = "field", setupTicks = 20, timeoutTicks = 700)
    public static void aCrewThatDecidedNotToOfferNeverDoes(GameTestHelper h) {
        floor(h);
        ThiefEntity.ransomChanceOverride = 0.0D;
        var player = h.makeMockPlayer();
        var c = crewForTheTimer(h, player, true);
        ThiefEntity.ransomChanceOverride = null;
        h.runAfterDelay(600, () -> {
            h.assertTrue(!c[1].isNegotiating(), "an offer was made by a crew that had decided against it");
            h.succeed();
        });
    }

    // ------------------------------------------------ the magician, the 2x2 board, the guide ------------------------------------------------

    private static ThiefEntity magicianAt(GameTestHelper h, BlockPos at) {
        ThiefEntity m = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), at);
        m.becomeMagician();
        return m;
    }

    @GameTest(batch = "t072", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void theMagicianHasFortyHealthAndASwapOfPlacesWithACopyOfItself(GameTestHelper h) {
        floor(h);
        ThiefEntity m = magicianAt(h, new BlockPos(6, 1, 6));
        h.assertTrue(m.getMaxHealth() == 40.0F && m.getHealth() == 40.0F && m.isMagician() && m.looksLikeMagician(), "not a magician with forty health");
        var before = m.position();
        int copies = m.castIllusion();
        h.assertTrue(copies == 3, "the magician has " + copies + " copies, not three");
        var decoys = m.decoys();
        h.assertTrue(decoys.size() == 3 && decoys.stream().allMatch(d -> d.isDecoy() && d.looksLikeMagician() && !d.isMagician()), "the copies are not copies");
        h.assertTrue(decoys.stream().anyMatch(d -> d.position().distanceTo(before) < 0.1D), "it did not change places with one of them");
        h.assertTrue(m.position().distanceTo(before) > 0.5D, "it is still where it was");
        h.assertTrue(m.castIllusion() == 3 && m.decoys().size() == 3, "a second casting made more than three");
        h.succeed();
    }

    @GameTest(batch = "t073", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aCopyTurnsToSmokeWhenHitWhateverHitsItAndTheRopeIsNotUsedUp(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 9.5)));
        ThiefEntity m = magicianAt(h, new BlockPos(6, 1, 6));
        m.castIllusion();
        var copies = new java.util.ArrayList<>(m.decoys());
        copies.get(0).hurt(h.getLevel().damageSources().generic(), 1.0F);
        h.assertTrue(copies.get(0).isRemoved(), "a blow did not end a copy");
        var rope = new net.minecraft.world.item.ItemStack(ModItems.ROPE.get());
        var result = ModItems.ROPE.get().interactLivingEntity(rope, player, copies.get(1), net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(result.consumesAction() && copies.get(1).isRemoved() && rope.getCount() == 1 && !copies.get(1).isRopeTied(), "the rope did not undo a copy, or was used up");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.WHIP.get()));
        copies.get(2).hurt(h.getLevel().damageSources().playerAttack(player), 1000.0F);
        h.assertTrue(copies.get(2).isRemoved(), "the whip did not end a copy");
        h.assertTrue(m.isAlive() && m.getHealth() == 40.0F, "the real magician was hurt by a copy being hit");
        h.succeed();
    }

    @GameTest(batch = "t074", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void whenTheMagicianIsCaughtAllItsCopiesAreGone(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 9.5)));
        ThiefEntity m = magicianAt(h, new BlockPos(6, 1, 6));
        m.castIllusion();
        var copies = new java.util.ArrayList<>(m.decoys());
        m.tieWithRope(player);
        h.runAfterDelay(5, () -> {
            h.assertTrue(copies.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved), "copies are left after the magician was tied up");
            h.succeed();
        });
    }

    @GameTest(batch = "t075", template = "field", setupTicks = 20, timeoutTicks = 400)
    public static void theMagicianMakesItsCopiesByItselfWhenAPlayerIsNearAndCopiesDoNotLast(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        ThiefEntity m = magicianAt(h, new BlockPos(6, 1, 6));
        h.runAfterDelay(40, () -> h.assertTrue(m.decoys().size() == 0, "copies before the first few seconds"));
        h.succeedWhen(() -> h.assertTrue(m.decoys().size() == 3, "no copies made yet"));
    }

    @GameTest(batch = "t076", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aKilledMagicianDropsItsBadgeAndEmeralds(GameTestHelper h) {
        floor(h);
        ThiefEntity m = magicianAt(h, new BlockPos(6, 1, 6));
        m.hurt(h.getLevel().damageSources().genericKill(), 1000.0F);
        h.assertItemEntityPresent(ModItems.MAGICIAN_TOKEN.get(), new BlockPos(6, 1, 6), 4.0D);
        h.assertItemEntityPresent(net.minecraft.world.item.Items.EMERALD, new BlockPos(6, 1, 6), 4.0D);
        h.succeed();
    }

    @GameTest(batch = "t077", template = "field", setupTicks = 20, timeoutTicks = 500)
    public static void theMagicianNeverSteals(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        h.setBlock(new BlockPos(9, 0, 9), Blocks.FARMLAND);
        h.setBlock(new BlockPos(9, 1, 9), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        ThiefEntity m = h.spawn(ModEntities.THIEF.get(), new BlockPos(4, 1, 4));
        m.becomeMagician();
        h.runAfterDelay(450, () -> {
            h.assertTrue(!m.hasLoot() && !m.hasStolen(), "the magician stole");
            h.succeed();
        });
    }

    @GameTest(batch = "t078", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aCrewOfFourWithAMagicianHasAMagicianALookoutAndTwoRobbers(GameTestHelper h) {
        floor(h);
        ThiefEntity.magicianChanceOverride = 1.0D;
        var random = new java.util.Random(11);
        ThiefEntity first = NightVisits.spawnNear(h.getLevel(), h.absolutePos(new BlockPos(7, 1, 7)), 3, 6, null, random);
        int n = NightVisits.formCrew(h.getLevel(), first, 4, random);
        ThiefEntity.magicianChanceOverride = null;
        h.assertTrue(n == 4, "the crew has " + n + " members");
        var members = h.getLevel().getEntitiesOfClass(ThiefEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(7, 1, 7))).inflate(30.0D), e -> first.crewId().equals(e.crewId()));
        h.assertTrue(members.stream().filter(ThiefEntity::isMagician).count() == 1 && members.stream().filter(ThiefEntity::isLookout).count() == 1
                && members.stream().filter(m -> !m.isMagician() && !m.isLookout()).count() == 2, "the crew is not a magician, a lookout and two robbers");
        // and a crew of two has none
        ThiefEntity.magicianChanceOverride = 1.0D;
        ThiefEntity small = NightVisits.spawnNear(h.getLevel(), h.absolutePos(new BlockPos(7, 1, 7)), 3, 6, null, random);
        NightVisits.formCrew(h.getLevel(), small, 2, random);
        ThiefEntity.magicianChanceOverride = null;
        h.assertTrue(!small.isMagician(), "a crew of two has a magician");
        h.succeed();
    }

    @GameTest(batch = "t079", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aMagicianBroughtToTheBoardPaysTenEmeraldsAndAGiveBadgeAndABadgeFetchesEight(GameTestHelper h) {
        floor(h);
        var board = ModBlocks.BOUNTY_BOARD.get().defaultBlockState();
        h.setBlock(new BlockPos(6, 1, 6), board);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 8.5)));
        ThiefEntity m = magicianAt(h, new BlockPos(6, 1, 9));
        m.tieWithRope(player);
        var hit = hitOn(h, new BlockPos(6, 1, 6), net.minecraft.core.Direction.SOUTH);
        board.getBlock().use(board, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(m.isRemoved(), "the magician was not taken");
        h.assertTrue(player.getInventory().countItem(EMERALD) == 10, "the bounty is not 10 emeralds: " + player.getInventory().countItem(EMERALD));
        h.assertTrue(player.getInventory().countItem(ModItems.MAGICIAN_TOKEN.get()) == 1, "no badge came with it");
        // the badge in hand: eight more, and it is used up
        player.getInventory().removeItem(new net.minecraft.world.item.ItemStack(ModItems.MAGICIAN_TOKEN.get()));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.MAGICIAN_TOKEN.get()));
        board.getBlock().use(board, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(player.getInventory().countItem(EMERALD) == 18, "a badge did not fetch eight emeralds: " + player.getInventory().countItem(EMERALD));
        h.assertTrue(player.getMainHandItem().isEmpty() || !player.getMainHandItem().is(ModItems.MAGICIAN_TOKEN.get()), "the badge was not used up");
        h.succeed();
    }

    @GameTest(batch = "t080", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void theBoardIsFourBlocksAndFallsWholeWhenOneIsBroken(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 10.5)));
        var result = ModItems.BOUNTY_BOARD.get().useOn(new net.minecraft.world.item.context.UseOnContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModItems.BOUNTY_BOARD.get()), hitOn(h, new BlockPos(6, 0, 6), net.minecraft.core.Direction.UP)));
        h.assertTrue(result.consumesAction(), "the board was not put up: " + result);
        int blocks = 0;
        for (int x = 5; x <= 7; x++) {
            for (int y = 1; y <= 3; y++) {
                if (h.getBlockState(new BlockPos(x, y, 6)).is(ModBlocks.BOUNTY_BOARD.get())) {
                    blocks++;
                }
            }
        }
        h.assertTrue(blocks == 4, "the board is " + blocks + " blocks, not 4");
        // find one of the top blocks and break it
        BlockPos top = null;
        for (int x = 5; x <= 7; x++) {
            var s = h.getBlockState(new BlockPos(x, 2, 6));
            if (s.is(ModBlocks.BOUNTY_BOARD.get()) && s.getValue(BountyBoardBlock.ROW) == BountyBoardBlock.Row.TOP) {
                top = new BlockPos(x, 2, 6);
            }
        }
        h.assertTrue(top != null, "no top row");
        h.setBlock(top, Blocks.AIR);
        BlockPos gone = top;
        h.runAfterDelay(5, () -> {
            for (int x = 5; x <= 7; x++) {
                for (int y = 1; y <= 3; y++) {
                    h.assertTrue(!h.getBlockState(new BlockPos(x, y, 6)).is(ModBlocks.BOUNTY_BOARD.get()), "part of the board was left standing at " + x + ", " + y + " after " + gone.toShortString() + " was broken");
                }
            }
            h.succeed();
        });
    }

    @GameTest(batch = "t081", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void theBoardWillNotBePutUpWhereItDoesNotFit(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(7, 2, 6), Blocks.STONE);        // one of the four places is taken
        h.setBlock(new BlockPos(5, 2, 6), Blocks.STONE);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 10.5)));
        ModItems.BOUNTY_BOARD.get().useOn(new net.minecraft.world.item.context.UseOnContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ModItems.BOUNTY_BOARD.get()), hitOn(h, new BlockPos(6, 0, 6), net.minecraft.core.Direction.UP)));
        h.assertTrue(!h.getBlockState(new BlockPos(6, 1, 6)).is(ModBlocks.BOUNTY_BOARD.get()), "put up where there was not room");
        h.succeed();
    }

    @GameTest(batch = "t082", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void theGuideIsGivenOncePerPlayer(GameTestHelper h) {
        var player = h.makeMockPlayer();
        h.assertTrue(GuideBook.giveOnce(player), "the first time, no guide was given");
        h.assertTrue(!GuideBook.giveOnce(player), "the guide was given twice");
        h.assertTrue(player.getInventory().countItem(ModItems.THIEF_GUIDE.get()) == 1, "the player does not have exactly one guide");
        h.succeed();
    }

    @GameTest(batch = "t083", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void everyLookInTheGuideMakesAFigure(GameTestHelper h) {
        for (String look : new String[]{"thief", "robber", "lookout", "sign", "magician", "copy", "led", "posted", "hung", "racked"}) {
            ThiefEntity t = ThiefEntity.forGuide(h.getLevel(), look);
            h.assertTrue(t != null, "no figure for " + look);
            switch (look) {
                case "magician", "copy" -> h.assertTrue(t.looksLikeMagician(), look + " is not dressed as the magician");
                case "posted" -> h.assertTrue(t.isPosted() && t.isRopeTied(), "posted is not posted");
                case "hung" -> h.assertTrue(t.isHung() && t.hangRopeLength() > 0.0F, "hung is not hung");
                case "racked" -> h.assertTrue(t.isRacked(), "racked is not on the rack");
                case "led" -> h.assertTrue(t.isRopeTied(), "led is not tied");
                case "lookout" -> h.assertTrue(t.getMainHandItem().is(net.minecraft.world.item.Items.SPYGLASS), "the lookout has no spyglass");
                case "sign" -> h.assertTrue(t.getMainHandItem().is(net.minecraft.world.item.Items.OAK_SIGN), "the one with the sign has no sign");
                default -> {
                }
            }
        }
        h.succeed();
    }

    // ------------------------------------------------ the magician: worn down first, loose by itself; chance in what resentment does ------------------------------------------------

    @GameTest(batch = "t084", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aMagicianCanOnlyBeTiedWhenItHasBeenWornDownAndAnyOtherThiefAnyTime(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 9.5)));
        ThiefEntity strong = magicianAt(h, new BlockPos(6, 1, 6));
        var rope = new net.minecraft.world.item.ItemStack(ModItems.ROPE.get(), 2);
        var result = ModItems.ROPE.get().interactLivingEntity(rope, player, strong, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(result.consumesAction() && !strong.isRopeTied() && !strong.tiedUp() && rope.getCount() == 2, "a magician in full health was tied, or the rope was used up");
        strong.setHealth(strong.getMaxHealth() * 0.5F);
        ModItems.ROPE.get().interactLivingEntity(rope, player, strong, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(!strong.tiedUp(), "a magician at half health was tied");
        strong.setHealth(strong.getMaxHealth() * 0.3F);
        ModItems.ROPE.get().interactLivingEntity(rope, player, strong, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(strong.tiedUp() && rope.getCount() == 1, "a magician worn down to under a third could not be tied");
        // any other thief, in full health, at once
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(8, 1, 6));
        ModItems.ROPE.get().interactLivingEntity(rope, player, thief, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(thief.tiedUp(), "an ordinary thief could not be tied at once");
        h.succeed();
    }

    @GameTest(batch = "t085", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aMagicianTiedToAPostOrHungOrOnARackGetsLooseButNotOneInAPlayersHand(GameTestHelper h) {
        floor(h);
        ThiefEntity.escapeRollOverride = 0.0D;        // the chance comes out as getting loose, as soon as there is any
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        h.setBlock(new BlockPos(9, 6, 9), Blocks.STONE);
        RackBlock.build(h.getLevel(), h.absolutePos(new BlockPos(11, 1, 4)), net.minecraft.core.Direction.SOUTH);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 12.5)));
        ThiefEntity posted = magicianAt(h, new BlockPos(4, 1, 8));
        ThiefEntity hung = magicianAt(h, new BlockPos(8, 1, 9));
        ThiefEntity racked = magicianAt(h, new BlockPos(11, 1, 8));
        ThiefEntity held = magicianAt(h, new BlockPos(6, 1, 11));
        for (ThiefEntity m : new ThiefEntity[]{posted, hung, racked, held}) {
            m.rescueProofForTest = true;
        }
        posted.bindToPost(h.absolutePos(new BlockPos(3, 1, 6)), net.minecraft.core.Direction.EAST);
        hung.hangFrom(h.absolutePos(new BlockPos(9, 6, 9)));
        racked.bindToRack(h.absolutePos(new BlockPos(11, 1, 4)), net.minecraft.core.Direction.SOUTH);
        held.tieWithRope(player);
        h.runAfterDelay(100, () -> {
            ThiefEntity.escapeRollOverride = null;
            h.assertTrue(!posted.isPosted() && !posted.tiedUp(), "the magician tied to a post did not get loose");
            h.assertTrue(!hung.isHung() && !hung.tiedUp(), "the hung magician did not get loose");
            h.assertTrue(!racked.isRacked() && !racked.tiedUp(), "the magician on the rack did not get loose");
            h.assertTrue(held.tiedUp(), "a magician in a player's hand got loose");
            h.assertItemEntityPresent(ModItems.ROPE.get(), new BlockPos(4, 1, 8), 8.0D);
            h.assertTrue(!posted.decoys().isEmpty(), "no copies came to cover its getting loose");
            h.succeed();
        });
    }

    @GameTest(batch = "t086", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void anOrdinaryThiefTiedToAPostDoesNotGetLoose(GameTestHelper h) {
        floor(h);
        ThiefEntity.escapeRollOverride = 0.0D;
        h.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_LOG);
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(5, 1, 8));
        thief.rescueProofForTest = true;
        thief.bindToPost(h.absolutePos(new BlockPos(3, 1, 6)), net.minecraft.core.Direction.EAST);
        h.runAfterDelay(150, () -> {
            ThiefEntity.escapeRollOverride = null;
            h.assertTrue(thief.isPosted(), "an ordinary thief got loose");
            h.succeed();
        });
    }

    @GameTest(batch = "t087", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void withALotOfResentmentItIsAChanceWhetherItAttacksOrRunsFaster(GameTestHelper h) {
        floor(h);
        var player = h.makeMockPlayer();
        player.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 11.5)));
        int attacked = 0;
        int ran = 0;
        for (int i = 0; i < 40; i++) {
            ThiefEntity t = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(2 + i % 10, 1, 3 + i / 10));
            t.tieWithRope(player);
            t.addGrudge(100.0F);
            t.dropLeash(true, false);
            if (t.isVengeful()) {
                attacked++;
            } else if (t.isHurried()) {
                ran++;
            }
        }
        // at full resentment it is three in four: with forty, both outcomes come up, and nothing else does
        h.assertTrue(attacked + ran == 40, "some did neither: attacked " + attacked + ", ran " + ran);
        h.assertTrue(attacked > 0 && ran > 0, "it is not by chance: attacked " + attacked + ", ran " + ran);
        h.assertTrue(attacked > ran, "at full resentment the attack should be the likelier: attacked " + attacked + ", ran " + ran);
        h.succeed();
    }

    @GameTest(batch = "t088", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aBadgeIsUsedUpAtTheBoardInCreativeModeToo(GameTestHelper h) {
        floor(h);
        var board = ModBlocks.BOUNTY_BOARD.get().defaultBlockState();
        h.setBlock(new BlockPos(6, 1, 6), board);
        var player = h.makeMockPlayer();
        player.getAbilities().instabuild = true;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ModItems.MAGICIAN_TOKEN.get(), 3));
        var hit = hitOn(h, new BlockPos(6, 1, 6), net.minecraft.core.Direction.SOUTH);
        board.getBlock().use(board, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(player.getMainHandItem().getCount() == 2, "a badge was not used up in creative mode: " + player.getMainHandItem().getCount() + " left of 3");
        board.getBlock().use(board, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        board.getBlock().use(board, h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(player.getMainHandItem().isEmpty() && player.getInventory().countItem(EMERALD) == 24, "three badges did not make 24 emeralds and nothing left: "
                + player.getInventory().countItem(EMERALD) + " emeralds, " + player.getMainHandItem().getCount() + " badges");
        h.succeed();
    }

    @GameTest(batch = "t089", template = "field", setupTicks = 20, timeoutTicks = 100)
    public static void aCopyLastsTenSecondsAndIsGoneWhenItsTimeIsUp(GameTestHelper h) {
        floor(h);
        ThiefEntity m = magicianAt(h, new BlockPos(6, 1, 6));
        m.castIllusion();
        var copy = m.decoys().get(0);
        h.assertTrue(copy.decoyTicksLeft() == ThiefConfig.MAGICIAN_COPY_SECONDS.get() * 20 && copy.decoyTicksLeft() == 200, "a copy does not last ten seconds: " + copy.decoyTicksLeft() + " ticks");
        h.succeed();
    }

    // ------------------------------------------------ wolves and dogs ------------------------------------------------

    private static void wolfTest(GameTestHelper h, boolean tamed, boolean magician, boolean crew) {
        floor(h);
        wallsAround(h);
        StealGoal.requirePlayerNearby = false;
        var wolf = h.spawn(net.minecraft.world.entity.EntityType.WOLF, new BlockPos(6, 1, 6));
        if (tamed) {
            wolf.setTame(true);
        }
        wolf.setNoAi(true);                         // it only stands there: it is the thief's fear of it that is looked at
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(8, 1, 6));
        if (magician) {
            thief.becomeMagician();
            thief.potionsForTest(0);                 // it would be drinking, not running
        }
        if (crew) {
            thief.joinCrew(java.util.UUID.randomUUID(), false);
        }
        boolean[] ran = new boolean[1];
        for (int tick = 2; tick <= 100; tick += 2) {
            h.runAfterDelay(tick, () -> ran[0] |= thief.runningGoals().contains("AvoidEntityGoal"));
        }
        h.runAfterDelay(104, () -> {
            h.assertTrue(ran[0], "the thief did not run from the " + (tamed ? "tamed dog" : "wolf") + ": " + thief.runningGoals());
            h.succeed();
        });
    }

    @GameTest(batch = "t990", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aThiefRunsFromAWildWolf(GameTestHelper h) {
        wolfTest(h, false, false, false);
    }

    @GameTest(batch = "t991", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aThiefRunsFromATamedDog(GameTestHelper h) {
        wolfTest(h, true, false, false);
    }

    @GameTest(batch = "t992", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void theMagicianRunsFromADog(GameTestHelper h) {
        wolfTest(h, true, true, false);
    }

    @GameTest(batch = "t993", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aCrewMemberRunsFromAWolf(GameTestHelper h) {
        wolfTest(h, false, false, true);
    }

    @GameTest(batch = "t994", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aThiefDoesNotRunFromACowAndStaysWhereItWas(GameTestHelper h) {
        floor(h);
        wallsAround(h);
        StealGoal.requirePlayerNearby = false;
        var cow = h.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(6, 1, 6));
        cow.setNoAi(true);
        ThiefEntity thief = h.spawn(ModEntities.THIEF.get(), new BlockPos(8, 1, 6));
        thief.clearGoalsForTest();
        thief.giveGoalsBackForTest();
        h.runAfterDelay(100, () -> {
            // it is not fleeing from a cow: no avoid-goal is among what it runs
            h.assertTrue(!thief.runningGoals().contains("AvoidEntityGoal"), "a thief avoids a cow: " + thief.runningGoals());
            h.succeed();
        });
    }

    // ------------------------------------------------ the magician's tricks ------------------------------------------------

    private static net.minecraft.world.Container chestWith(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, Blocks.CHEST);
        var chest = (net.minecraft.world.Container) h.getBlockEntity(pos);
        for (int i = 0; i < 6; i++) {
            chest.setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 10 + i));
        }
        return chest;
    }

    private static int countOf(net.minecraft.world.Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    @GameTest(batch = "t981", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void theMagicianReachesForAChestFromWhereItStandsAndTakesThreeStacks(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        var chest = chestWith(h, new BlockPos(11, 1, 11));
        ThiefEntity m = magicianAt(h, new BlockPos(4, 1, 4));
        m.clearGoalsForTest();
        m.tricks.swapRollOverride = 1.0D;                 // no props in this one
        m.tricks.startGrab(h.getLevel());
        h.assertTrue(m.tricks.grabbing(), "it did not begin to reach");
        h.runAfterDelay(140, () -> {
            int left = countOf(chest, net.minecraft.world.item.Items.IRON_INGOT);
            h.assertTrue(left < 75 && m.hasLoot() && m.lootCount() >= 1, "nothing was taken: " + left + " left, loot " + m.lootCount());
            int props = countOf(chest, ModItems.MAGIC_PROP.get());
            h.assertTrue(props == 0, "a prop was left though the roll was against it");
            h.assertTrue(!m.tricks.grabbing(), "it is still reaching after three stacks");
            h.succeed();
        });
    }

    @GameTest(batch = "t982", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aStackTakenFromAfarCanBeReplacedByAProp(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        var chest = chestWith(h, new BlockPos(11, 1, 11));
        ThiefEntity m = magicianAt(h, new BlockPos(4, 1, 4));
        m.clearGoalsForTest();
        m.tricks.swapRollOverride = 0.0D;
        m.tricks.startGrab(h.getLevel());
        h.runAfterDelay(140, () -> {
            int props = countOf(chest, ModItems.MAGIC_PROP.get());
            h.assertTrue(props > 0, "no prop in the chest");
            int stolen = 0;
            for (var s : m.lootForTest()) {
                stolen += s.getCount();
            }
            h.assertTrue(props <= stolen, "more props (" + props + ") than what was taken (" + stolen + ")");   // a stack taken in part leaves what is left in the slot, and no prop
            h.succeed();
        });
    }

    @GameTest(batch = "t983", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aBlowStopsTheMagicianReaching(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        var chest = chestWith(h, new BlockPos(11, 1, 11));
        ThiefEntity m = magicianAt(h, new BlockPos(4, 1, 4));
        m.clearGoalsForTest();
        m.tricks.startGrab(h.getLevel());
        h.runAfterDelay(10, () -> m.hurt(h.getLevel().damageSources().generic(), 1.0F));
        h.runAfterDelay(160, () -> {
            h.assertTrue(!m.tricks.grabbing(), "it went on reaching after the blow");
            h.assertTrue(countOf(chest, net.minecraft.world.item.Items.IRON_INGOT) == 75 && !m.hasLoot(), "it took something after the blow");
            h.succeed();
        });
    }

    @GameTest(batch = "t984", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aThrownCardHurtsAPlayerAndAShieldStopsIt(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        ThiefEntity m = magicianAt(h, new BlockPos(4, 1, 6));
        m.clearGoalsForTest();
        var player = h.makeMockPlayer();
        player.moveTo(h.absolutePos(new BlockPos(9, 1, 6)).getX() + 0.5D, h.absolutePos(new BlockPos(9, 1, 6)).getY(), h.absolutePos(new BlockPos(9, 1, 6)).getZ() + 0.5D);
        m.tricks.extraTargets.add(player);
        float before = player.getHealth();
        m.tricks.throwCard(h.getLevel(), player);
        h.assertTrue(m.tricks.cardsInFlight() == 1, "no card in the air");
        h.runAfterDelay(40, () -> {
            h.assertTrue(player.getHealth() < before, "the card did not hurt: health " + player.getHealth());
            h.assertTrue(m.tricks.cardsInFlight() == 0, "the card never landed");
            h.succeed();
        });
    }

    @GameTest(batch = "t985", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void aCardStopsAtAWall(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        for (int y = 1; y <= 3; y++) {
            for (int z = 3; z <= 9; z++) {
                h.setBlock(new BlockPos(7, y, z), Blocks.STONE);
            }
        }
        ThiefEntity m = magicianAt(h, new BlockPos(4, 1, 6));
        m.clearGoalsForTest();
        var player = h.makeMockPlayer();
        player.moveTo(h.absolutePos(new BlockPos(10, 1, 6)).getX() + 0.5D, h.absolutePos(new BlockPos(10, 1, 6)).getY(), h.absolutePos(new BlockPos(10, 1, 6)).getZ() + 0.5D);
        m.tricks.extraTargets.add(player);
        float before = player.getHealth();
        m.tricks.throwCard(h.getLevel(), player);
        h.runAfterDelay(40, () -> {
            h.assertTrue(player.getHealth() == before, "a card went through a wall");
            h.succeed();
        });
    }

    @GameTest(batch = "t986", template = "field", setupTicks = 20, timeoutTicks = 300)
    public static void theSmokeBombMakesACloudAtThePlayerAndTheMagicianChangesPlaces(GameTestHelper h) {
        floor(h);
        StealGoal.requirePlayerNearby = false;
        ThiefEntity m = magicianAt(h, new BlockPos(4, 1, 4));
        m.clearGoalsForTest();
        var player = h.makeMockPlayer();
        var at = h.absolutePos(new BlockPos(8, 1, 8));
        player.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        var from = m.position();
        m.tricks.throwSmoke(h.getLevel(), player);
        var clouds = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.AreaEffectCloud.class, new net.minecraft.world.phys.AABB(at).inflate(3.0D));
        h.assertTrue(clouds.size() == 1, "no cloud at the player: " + clouds.size());
        h.assertTrue(m.position().distanceTo(from) > 2.0D, "the magician did not move");
        h.succeed();
    }

    @GameTest(batch = "t987", template = "field", setupTicks = 20, timeoutTicks = 200)
    public static void aBlockHoldsOnlyOneHangingThief(GameTestHelper h) {
        floor(h);
        BlockPos ceiling = new BlockPos(6, 7, 6);
        h.setBlock(ceiling, Blocks.STONE);
        h.setBlock(new BlockPos(9, 7, 9), Blocks.STONE);
        ThiefEntity thief = h.spawnWithNoFreeWill(ModEntities.THIEF.get(), new BlockPos(6, 1, 6));
        h.assertTrue(!Captives.takenHang(h.getLevel(), h.absolutePos(ceiling)), "taken before anyone hung there");
        thief.hangFrom(h.absolutePos(ceiling));
        h.assertTrue(Captives.takenHang(h.getLevel(), h.absolutePos(ceiling)), "not taken with a thief hanging there");
        h.assertTrue(!Captives.takenHang(h.getLevel(), h.absolutePos(new BlockPos(9, 7, 9))), "another block counts as taken");
        h.succeed();
    }
}
