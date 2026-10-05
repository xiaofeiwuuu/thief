package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Tying up other creatures than thieves: zombies, villagers and the rest of the humanoids in the list in the settings. A thief carries what
 * it takes to be tied up in its own fields; a creature of the game cannot, so this is kept in the data that Forge saves with every entity,
 * and told to the clients (who draw the ropes) by a packet. A tied creature has its AI switched off, so that it neither bites nor walks away,
 * and gets it back when it is let go.
 */
@Mod.EventBusSubscriber(modid = ThiefMod.MODID)
public final class Captives {

    /** LED: held on the rope of whoever leads it. POST: back to a tree trunk or a post. RACK: spread on a rack. HUNG: by the wrists from a block. */
    public enum Mode { NONE, LED, POST, RACK, HUNG }

    /** {@code holderId}: the entity number of the one who leads it, when it is led (-1 otherwise). */
    public record State(Mode mode, BlockPos pos, Direction dir, double gap, int holderId) {
        public static final State NONE = new State(Mode.NONE, BlockPos.ZERO, Direction.SOUTH, 0.0D, -1);

        public State(Mode mode, BlockPos pos, Direction dir, double gap) {
            this(mode, pos, dir, gap, -1);
        }
    }

    /** Who leads each creature that is led, as an object, so that it is known without a search (which a player that is not in the world, as in the tests, would not pass). */
    private static final java.util.Map<Mob, Player> HOLDERS = new java.util.WeakHashMap<>();

    private static final String KEY = "ThiefBinding";

    /** Set by the automatic tests, where the sky is not open: true is bright sun. Null in the game. */
    static Boolean forceSun = null;

    private Captives() {
    }

    // ---------------------------------------------------------------- who, and the state ----------------------------------------------------------------

    /** A creature a rope can tie: one in the list in the settings (never a player, never a thief, which has its own way). */
    public static boolean bindable(Entity e) {
        if (!(e instanceof Mob) || e instanceof ThiefEntity) {
            return false;
        }
        var key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return key != null && ThiefConfig.BINDABLE_TYPES.get().contains(key.toString());
    }

    public static State state(Entity e) {
        if (e.level().isClientSide) {
            return ClientCaptives.get(e.getId());
        }
        CompoundTag tag = e.getPersistentData().getCompound(KEY);
        if (tag.isEmpty()) {
            return State.NONE;
        }
        Direction dir = Direction.byName(tag.getString("Dir"));
        return new State(Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, tag.getInt("Mode")))], BlockPos.of(tag.getLong("Pos")),
                dir != null ? dir : Direction.SOUTH, tag.getDouble("Gap"), e instanceof Mob m && holder(m) != null ? holder(m).getId() : -1);
    }

    public static boolean isBound(Entity e) {
        return state(e).mode() != Mode.NONE;
    }

    private static void write(Mob mob, State s) {
        CompoundTag all = mob.getPersistentData();
        CompoundTag tag = all.getCompound(KEY);
        tag.putInt("Mode", s.mode().ordinal());
        tag.putLong("Pos", s.pos().asLong());
        tag.putString("Dir", s.dir().getName());
        tag.putDouble("Gap", s.gap());
        all.put(KEY, tag);
        sync(mob);
    }

    /** The one who leads it (server), or null. */
    static Player holder(Mob mob) {
        if (mob.level().isClientSide) {
            return null;
        }
        Player p = HOLDERS.get(mob);
        CompoundTag tag = mob.getPersistentData().getCompound(KEY);
        if (p == null && tag.hasUUID("Holder")) {
            p = mob.level().getPlayerByUUID(tag.getUUID("Holder"));      // after a reload
            if (p != null) {
                HOLDERS.put(mob, p);
            }
        }
        return p;
    }

    private static CaptivePacket packet(Mob mob) {
        State s = state(mob);
        return new CaptivePacket(mob.getId(), s.mode().ordinal(), s.pos().asLong(), s.dir().get2DDataValue(), s.gap(), s.holderId());
    }

    private static void sync(Mob mob) {
        if (mob.level().isClientSide) {
            return;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), packet(mob));
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Mob mob && event.getEntity() instanceof ServerPlayer player && isBound(mob)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet(mob));
        }
    }

    // ---------------------------------------------------------------- tying ----------------------------------------------------------------

    /** Whether its AI was on before it was tied is written down once, before anything is changed, to be given back when it is let go. */
    private static void secure(Mob mob) {
        CompoundTag all = mob.getPersistentData();
        if (!all.contains(KEY)) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("PrevNoAi", mob.isNoAi());
            all.put(KEY, tag);
        }
        mob.setNoAi(true);                       // it does not bite, does not walk off
        mob.setTarget(null);
        mob.setPersistenceRequired();
        mob.getNavigation().stop();
    }

    /**
     * Tied by the hands and led on the rope held by the player. Not on the game's lead, which ties to the head of whatever it is on and cannot
     * be moved: it is made to follow, and the client draws the rope from the player's hand to its hands.
     */
    public static void tieLed(Mob mob, Player holder) {
        secure(mob);
        mob.getPersistentData().getCompound(KEY).putUUID("Holder", holder.getUUID());
        HOLDERS.put(mob, holder);
        write(mob, new State(Mode.LED, BlockPos.ZERO, Direction.SOUTH, 0.0D));
    }

    public static void bindToPost(Mob mob, BlockPos post, Direction side) {
        secure(mob);
        letGoOfTheLead(mob);
        mob.setNoGravity(true);
        write(mob, new State(Mode.POST, post.immutable(), side, 0.0D));
        positionPost(mob, post, side);
    }

    public static void bindToRack(Mob mob, BlockPos anchor, Direction facing) {
        secure(mob);
        letGoOfTheLead(mob);
        mob.setNoGravity(true);
        write(mob, new State(Mode.RACK, anchor.immutable(), facing, 0.0D));
        positionRack(mob, anchor, facing);
    }

    public static void hang(Mob mob, BlockPos anchor) {
        double gap = ThiefEntity.hangGap(mob.level(), anchor);
        secure(mob);
        letGoOfTheLead(mob);
        mob.setNoGravity(true);
        write(mob, new State(Mode.HUNG, anchor.immutable(), Direction.SOUTH, gap < 0 ? 0.5D : gap));
        positionHung(mob, anchor, gap < 0 ? 0.5D : gap);
    }

    private static void letGoOfTheLead(Mob mob) {
        if (mob.isLeashed()) {
            mob.dropLeash(true, false);          // the game would drop a lead: the rope stays tied to it
        }
    }

    /** Let go: its AI back as it was, its feet on the ground, and the rope that held it as an item. */
    public static void release(Mob mob, boolean dropRope) {
        CompoundTag all = mob.getPersistentData();
        CompoundTag tag = all.getCompound(KEY);
        if (tag.isEmpty()) {
            return;
        }
        letGoOfTheLead(mob);
        mob.setNoAi(tag.getBoolean("PrevNoAi"));
        mob.setNoGravity(false);
        HOLDERS.remove(mob);
        all.remove(KEY);
        sync(mob);
        if (dropRope && !mob.level().isClientSide) {
            mob.spawnAtLocation(ModItems.ROPE.get());
        }
    }

    /** The bindable creatures this player leads on a rope, near a place. */
    public static List<Mob> ledBy(Level level, Player player, AABB box) {
        return level.getEntitiesOfClass(Mob.class, box, m -> bindable(m) && state(m).mode() == Mode.LED && holder(m) == player);
    }

    public static boolean takenPost(Level level, BlockPos post, Direction side) {
        return !level.getEntitiesOfClass(Mob.class, new AABB(post.relative(side)).inflate(1.5D), m -> {
            State s = state(m);
            return s.mode() == Mode.POST && s.pos().equals(post) && s.dir() == side;
        }).isEmpty();
    }

    /** Whether something already hangs from the underside of this block. */
    public static boolean takenHang(Level level, BlockPos anchor) {
        if (!level.getEntitiesOfClass(ThiefEntity.class, new AABB(anchor).inflate(6.0D), t -> t.isHung() && anchor.equals(t.hangPos())).isEmpty()) {
            return true;
        }
        return !level.getEntitiesOfClass(Mob.class, new AABB(anchor).inflate(6.0D), m -> {
            State s = state(m);
            return s.mode() == Mode.HUNG && s.pos().equals(anchor);
        }).isEmpty();
    }

    public static boolean takenRack(Level level, BlockPos anchor) {
        return !level.getEntitiesOfClass(Mob.class, new AABB(anchor).inflate(2.0D), m -> {
            State s = state(m);
            return s.mode() == Mode.RACK && s.pos().equals(anchor);
        }).isEmpty();
    }

    /** The undead that the game burns in sunlight. (Not husks, strays, wither skeletons, drowned or piglins: they do not burn in the game either.) */
    private static boolean burnsInSun(Mob mob) {
        var type = mob.getType();
        return type == net.minecraft.world.entity.EntityType.ZOMBIE || type == net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER || type == net.minecraft.world.entity.EntityType.SKELETON;
    }

    private static boolean sunlit(Mob mob) {
        if (forceSun != null) {
            return forceSun;
        }
        Level level = mob.level();
        return level.isDay() && !mob.isInWaterRainOrBubble() && mob.getLightLevelDependentMagicValue() > 0.5F
                && level.canSeeSky(BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ()));
    }

    /**
     * What the game does to a zombie or a skeleton in the sun (a chance each tick, never with a helmet, which wears instead), done here as well
     * for the ones that are tied up, so that they all burn alike whatever the game's own check does with a creature whose AI is off.
     */
    private static void burnInTheSun(Mob mob) {
        if (burnsInSun(mob) && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !mob.isOnFire() && sunlit(mob)
                && mob.getRandom().nextFloat() * 30.0F < (forceSun != null ? 1.0F : (mob.getLightLevelDependentMagicValue() - 0.4F) * 2.0F)) {
            mob.setSecondsOnFire(8);
        }
    }

    /**
     * It follows whoever leads it: walks up to about three blocks from them, facing the way it goes, jumping up a block that is in the way, and
     * stands still when it is there. A creature whose AI is off does not move by itself, nor fall, nor jump (the game skips its movement
     * altogether), so all of it is done here: a step, gravity, and the jump the game's own walking would have made.
     */
    private static void follow(Mob mob, Entity holder) {
        double dx = holder.getX() - mob.getX(), dz = holder.getZ() - mob.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double vy = mob.getDeltaMovement().y;
        if (mob.isInWater() || mob.isInLava()) {
            vy = 0.1D;                                         // swims up to the surface and along
        } else if (mob.onGround()) {
            vy = -0.01D;
            if (d > 3.0D && mob.horizontalCollision) {
                vy = 0.42D;                                    // something in the way: up it jumps, as far as the game's own jump goes (a block)
            }
        } else {
            vy = Math.max((vy - 0.08D) * 0.98D, -1.0D);
        }
        double vx = 0.0D, vz = 0.0D;
        if (d > 3.0D) {
            double speed = Math.min(0.22D, 0.1D + 0.03D * (d - 3.0D));
            vx = dx / d * speed;
            vz = dz / d * speed;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            mob.setYRot(yaw);
            mob.setYHeadRot(yaw);
            mob.yBodyRot = yaw;
        }
        mob.setDeltaMovement(vx, vy, vz);
        mob.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(vx, vy, vz));
    }

    // ---------------------------------------------------------------- where it stands ----------------------------------------------------------------

    private static void place(Mob mob, double x, double y, double z, float yaw) {
        mob.moveTo(x, y, z, yaw, 0.0F);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        mob.fallDistance = 0.0F;
    }

    static void positionPost(Mob mob, BlockPos post, Direction side) {
        VoxelShape shape = mob.level().getBlockState(post).getShape(mob.level(), post);
        // its back on the face, with its eyes (a box four fifths of its width) clear of the block
        double gap = Math.max(0.15D, mob.getBbWidth() * 0.4D + 0.02D);
        double x = post.getX() + 0.5D, z = post.getZ() + 0.5D;
        switch (side) {
            case EAST -> x = post.getX() + (shape.isEmpty() ? 1.0D : shape.max(Direction.Axis.X)) + gap;
            case WEST -> x = post.getX() + (shape.isEmpty() ? 0.0D : shape.min(Direction.Axis.X)) - gap;
            case SOUTH -> z = post.getZ() + (shape.isEmpty() ? 1.0D : shape.max(Direction.Axis.Z)) + gap;
            default -> z = post.getZ() + (shape.isEmpty() ? 0.0D : shape.min(Direction.Axis.Z)) - gap;
        }
        BlockPos below = post.relative(side).below();
        VoxelShape ground = mob.level().getBlockState(below).getCollisionShape(mob.level(), below);
        double y = ground.isEmpty() ? post.getY() : below.getY() + ground.max(Direction.Axis.Y);
        place(mob, x, y, z, side.toYRot());
    }

    static void positionRack(Mob mob, BlockPos anchor, Direction facing) {
        place(mob, anchor.getX() + 0.5D + facing.getStepX() / 16.0D, anchor.getY() + RackBlock.LIFT, anchor.getZ() + 0.5D + facing.getStepZ() / 16.0D, facing.toYRot());
    }

    static void positionHung(Mob mob, BlockPos anchor, double gap) {
        double handsUp = ThiefEntity.HANDS_UP * mob.getBbHeight() / 1.95D;
        place(mob, anchor.getX() + 0.5D, anchor.getY() - gap - handsUp, anchor.getZ() + 0.5D, mob.getYRot());
    }

    // ---------------------------------------------------------------- every tick, and what is clicked ----------------------------------------------------------------

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || mob instanceof ThiefEntity || !mob.getPersistentData().contains(KEY)) {
            return;
        }
        State s = state(mob);
        Level level = mob.level();
        burnInTheSun(mob);
        switch (s.mode()) {
            case LED -> {
                Entity holder = holder(mob);
                if (holder == null || !holder.isAlive() || mob.distanceTo(holder) > 9.5F) {
                    release(mob, true);          // the rope held by nobody, or stretched too far: it comes off, and is not lost
                } else {
                    mob.setTarget(null);
                    follow(mob, holder);
                }
            }
            case POST -> {
                BlockPos below = s.pos().relative(s.dir()).below();
                if (RopeKnotEntity.canTieTo(level.getBlockState(s.pos())) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    mob.setTarget(null);
                    positionPost(mob, s.pos(), s.dir());
                } else {
                    release(mob, true);
                }
            }
            case RACK -> {
                if (RackBlock.isAnchor(level.getBlockState(s.pos()))) {
                    mob.setTarget(null);
                    positionRack(mob, s.pos(), level.getBlockState(s.pos()).getValue(RackBlock.FACING));
                } else {
                    release(mob, true);
                }
            }
            case HUNG -> {
                if (level.getBlockState(s.pos()).isFaceSturdy(level, s.pos(), Direction.DOWN)) {
                    mob.setTarget(null);
                    positionHung(mob, s.pos(), s.gap());
                } else {
                    release(mob, true);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Clicking a tied creature: sneaking searches it, an empty hand lets it go. Done here, before the game does its own (a lead being taken
     * off a creature would drop a lead, not the rope).
     */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Mob mob) || mob instanceof ThiefEntity || !isBound(mob)) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean client = player.level().isClientSide;
        if (player.isShiftKeyDown() && ThiefEntity.canSearchWith(held)) {
            if (!client) {
                search(mob, player);
            }
        } else if (held.isEmpty() && !player.isShiftKeyDown()) {
            if (!client) {
                release(mob, !player.getAbilities().instabuild);
            }
        } else {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(client));
    }

    // ---------------------------------------------------------------- searching, the whip ----------------------------------------------------------------

    /** What it wears, holds and (a villager) carries. */
    private static List<Runnable> pockets(Mob mob, List<ItemStack> into) {
        List<Runnable> removers = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = mob.getItemBySlot(slot);
            if (!s.isEmpty()) {
                into.add(s.copy());
                removers.add(() -> mob.setItemSlot(slot, ItemStack.EMPTY));
            }
        }
        if (mob instanceof Villager villager) {
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                ItemStack s = villager.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    into.add(s.copy());
                    int slot = i;
                    removers.add(() -> villager.getInventory().setItem(slot, ItemStack.EMPTY));
                }
            }
        }
        return removers;
    }

    /** Takes one thing from what it wears, holds or carries and gives it to the player. */
    static void search(Mob mob, Player player) {
        List<ItemStack> found = new ArrayList<>();
        List<Runnable> removers = pockets(mob, found);
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.thief.search_empty"), true);
            return;
        }
        ItemStack stack = found.get(0);
        removers.get(0).run();
        Component name = stack.getHoverName();
        ThiefEntity.give(player, stack);
        mob.level().playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.thief.search_found", name), true);
    }

    /** A lash shakes one thing loose from a tied creature. */
    static void shake(Mob mob) {
        List<ItemStack> found = new ArrayList<>();
        List<Runnable> removers = pockets(mob, found);
        if (found.isEmpty()) {
            return;
        }
        ItemStack stack = found.get(mob.getRandom().nextInt(found.size()));
        int index = found.indexOf(stack);
        removers.get(index).run();
        var drop = mob.spawnAtLocation(stack);
        if (drop != null) {
            drop.setDeltaMovement((mob.getRandom().nextDouble() - 0.5D) * 0.3D, 0.25D, (mob.getRandom().nextDouble() - 0.5D) * 0.3D);
        }
    }
}
