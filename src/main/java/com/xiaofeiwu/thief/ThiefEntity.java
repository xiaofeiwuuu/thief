package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * A thief: slips in at night, robs a chest or a field, and runs. It keeps what it took until it is defeated, then drops all of it.
 * It never despawns while it carries loot, and the ledger says where it was last seen.
 */
public class ThiefEntity extends PathfinderMob {

    /** What the thief looks like, as an entity type id; empty when it looks like itself. */
    private static final EntityDataAccessor<String> DISGUISE = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.STRING);
    /** Hands tied with a rope, and hung up: told to the client, which draws the arms bound or raised. */
    private static final EntityDataAccessor<Boolean> TIED = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HUNG = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RACKED = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.BOOLEAN);
    /** How much bare rope shows between the underside of the block and the hands of a thief that is hung up: the client draws it. */
    private static final EntityDataAccessor<Float> HANG_ROPE = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.FLOAT);
    /** The magician's looks: the boss and its copies are dressed alike. */
    private static final EntityDataAccessor<Boolean> MAGICIAN_LOOK = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> POSTED = SynchedEntityData.defineId(ThiefEntity.class, EntityDataSerializers.BOOLEAN);

    /** How high the hands reach above the feet with the arms stretched up. The rope is tied there. */
    public static final double HANDS_UP = 2.125D;
    private static final double MAX_GAP = 1.5D;
    private static final double MIN_GAP = 0.5D;

    private final List<ItemStack> loot = new ArrayList<>();
    /** Gets in the way of stealing again: a thief robs once, then runs. */
    private boolean hasStolen;
    private UUID crewId;                       // the crew it belongs to, if any
    private boolean lookout;                   // the one who watches and whistles, and does not steal
    private int alertTicks;                    // after the lookout's whistle: draws back instead of stealing
    private long nextPass;                     // not before this game time does it hand loot over again
    private int lastHurtTick = -1000;
    private int rescueTimer = -1;              // while tied up: ticks until a rescuer may be sent; -1 not counting
    /** What this thief's last look for a captive to rescue saw: for finding out why a rescue did not happen (shown by {@code /thief debug}). */
    String rescueLog = "";
    /** The magician: a boss that does not steal, and makes copies of itself. */
    private boolean magician;
    private UUID decoyOf;                      // when this is one of the magician's copies: whose
    private int decoyTicks;
    private long nextIllusion;
    private int boundTicks;                    // how long the magician has been tied up other than in a player's hand
    private final net.minecraft.server.level.ServerBossEvent bossBar = new net.minecraft.server.level.ServerBossEvent(Component.translatable("entity.thief.magician"),
            net.minecraft.world.BossEvent.BossBarColor.PURPLE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
    /** Resentment, 0 to 100: it grows while the thief is tied up and with every lash, and decides what it does when it is let go. */
    private float grudge;
    private UUID captor;                       // who tied or lashed it last
    private Player captorRef;                  // the same, as an object (a player that is not in the world, as in the tests, cannot be found by its number)
    private boolean confessed;                 // it has told what it knew
    private long vengefulUntil;                // out for revenge until then
    private long hurriedUntil;                 // running faster until then
    private long ransomUntil;                  // the offer to buy it back stands until then
    private int ransomTimer = -1;
    private boolean ransomWilling;             // decided when it is caught: whether the crew will make an offer at all
    /** For the tests: the others do not try to free it, whatever thieves of other tests are about. */
    boolean rescueProofForTest;
    private int ransomTries;                   // how many times the crew has looked for something to offer
    private UUID negotiatingFor;               // when it holds up the sign: the captive it is offering to buy back
    private long negotiatingUntil;
    private boolean transferring;              // being moved from a lead to a post, a rack or a rope: not let go
    private static final UUID HURRY_ID = UUID.fromString("5d1c1c4e-39c4-4a4e-9a5b-7a9d2f8f4b11");
    /** Set by the automatic tests. */
    static Double confessChanceOverride = null;
    static Double ransomShareOverride = null;
    static Double ransomChanceOverride = null;
    static Double magicianChanceOverride = null;
    /** Off in the automatic tests: a caught thief does not call up a rescuer by itself, which would wander about the next test's area. A test that wants one asks for it. */
    static boolean autoRescuers = true;
    static Double grudgeRollOverride = null;
    static Double escapeRollOverride = null;
    /** How far a thief that talks knows of the others: 128 blocks in the game, less in the automatic tests, where the areas are close together. */
    static double confessRange = 128.0D;
    private BlockPos postPos;                  // the tree trunk, post or pillar it is tied to, back against it
    private net.minecraft.core.Direction postDir = net.minecraft.core.Direction.SOUTH;      // the side of it the thief stands on, which way it faces
    private BlockPos rackPos;                  // the bottom block of the rack it is tied to
    private net.minecraft.core.Direction rackFacing = net.minecraft.core.Direction.SOUTH;
    private double hangGap = MAX_GAP;          // the bare rope between the underside of the block and the hands
    private EntityType<?> disguiseType;        // the entity data resolved, kept here because the size is asked for early on
    private int disguiseTicks;
    private int potions;
    private long nextDrink;
    private BlockPos hungFrom;                // the block it hangs under, when it is hung up
    private Entity disguiseDummy;              // client: a stand-in of the creature it looks like, that is drawn instead of the thief

    public ThiefEntity(EntityType<? extends ThiefEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.potions = ThiefConfig.POTIONS.get();
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);       // a wooden door does not stop it, an iron one does
        }
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)      // a little slower than a running player, so it can be caught
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);          // for when it is out for revenge
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.3D));
        goalSelector.addGoal(2, new OpenDoorGoal(this, false));
        goalSelector.addGoal(2, new NegotiateGoal(this));
        goalSelector.addGoal(2, new RevengeGoal(this));
        goalSelector.addGoal(3, new PassLootGoal(this));
        goalSelector.addGoal(3, new DrinkPotionGoal(this));
        goalSelector.addGoal(4, new FleePlayerGoal(this));
        // a wolf, wild or a tamed dog, frightens a thief off as a player does (the magician too, and its copies): from 12 blocks, a little faster than it walks
        goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(this, net.minecraft.world.entity.animal.Wolf.class, 12.0F, 1.0D, 1.15D,
                wolf -> !tiedUp() && !isNegotiating() && !isVengeful()));
        goalSelector.addGoal(5, new RescueGoal(this));
        goalSelector.addGoal(6, new StealGoal(this));
        goalSelector.addGoal(7, new FollowCrewGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    // ---------------------------------------------------- the potion and the disguise ----------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DISGUISE, "");
        entityData.define(TIED, false);
        entityData.define(HUNG, false);
        entityData.define(RACKED, false);
        entityData.define(POSTED, false);
        entityData.define(MAGICIAN_LOOK, false);
        entityData.define(HANG_ROPE, 0.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DISGUISE.equals(key)) {
            disguiseType = resolve(entityData.get(DISGUISE));
            disguiseDummy = null;
            refreshDimensions();
        }
    }

    private static EntityType<?> resolve(String id) {
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && ForgeRegistries.ENTITY_TYPES.containsKey(key) ? ForgeRegistries.ENTITY_TYPES.getValue(key) : null;
    }

    /** As big as what it looks like, or a cow with a thief's height would give it away. */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return disguiseType != null ? disguiseType.getDimensions() : super.getDimensions(pose);
    }

    public boolean isDisguised() {
        return disguiseType != null;
    }

    public EntityType<?> disguiseType() {
        return disguiseType;
    }

    void potionsForTest(int n) {
        potions = n;
    }

    int potions() {
        return potions;
    }

    long nextDrink() {
        return nextDrink;
    }

    int disguiseTicks() {
        return disguiseTicks;
    }

    /** One of the creatures within 24 blocks that it may look like, picked at random (so the common ones come up more), or null. */
    EntityType<?> pickDisguise() {
        List<? extends String> allowed = ThiefConfig.DISGUISE_TYPES.get();
        List<EntityType<?>> found = new ArrayList<>();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(24.0D), x -> x != this && !(x instanceof Player) && !(x instanceof ThiefEntity))) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            if (key != null && allowed.contains(key.toString())) {
                found.add(e.getType());
            }
        }
        return found.isEmpty() ? null : found.get(random.nextInt(found.size()));
    }

    void holdPotion() {
        setItemSlot(EquipmentSlot.MAINHAND, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.INVISIBILITY));
    }

    void disguiseAs(EntityType<?> type) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key == null || level().isClientSide) {
            return;
        }
        potions--;
        disguiseTicks = ThiefConfig.DISGUISE_SECONDS.get() * 20;
        disguiseType = type;
        disguiseDummy = null;
        entityData.set(DISGUISE, key.toString());
        refreshDimensions();
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.3D, 0.4D, 0.3D, 0.02D);
            server.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 1.2F);
        }
    }

    /** Back to looking like a thief: when it is hit, starts to steal, is tied, or the potion wears off. */
    public void reveal() {
        if (disguiseType == null || level().isClientSide) {
            return;
        }
        disguiseTicks = 0;
        disguiseType = null;
        disguiseDummy = null;
        entityData.set(DISGUISE, "");
        refreshDimensions();
        nextDrink = level().getGameTime() + 200;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.3D, 0.4D, 0.3D, 0.02D);
        }
    }

    /** Client: the stand-in that is drawn instead of the thief. Made when first needed, never added to the world. */
    public Entity disguiseDummy() {
        if (disguiseDummy == null && disguiseType != null) {
            disguiseDummy = disguiseType.create(level());
        }
        return disguiseDummy;
    }

    /** Client, every tick: the stand-in goes where the thief goes, so that legs move when it walks. */
    private void updateDummy() {
        Entity d = disguiseDummy();
        if (d == null) {
            return;
        }
        d.xo = d.getX();
        d.yo = d.getY();
        d.zo = d.getZ();
        d.setPos(getX(), getY(), getZ());
        d.setOnGround(onGround());
        d.tickCount = tickCount;
        if (d instanceof LivingEntity living) {
            living.calculateEntityAnimation(false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && disguiseType != null) {
            updateDummy();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (decoyOf != null) {
            if (!level().isClientSide) {
                vanish();        // a blow, of anything, and a copy is gone
            }
            return true;
        }
        lastHurtTick = tickCount;
        if (magician) {
            tricks.interrupt();
        }
        if (isNegotiating()) {
            stopNegotiating();        // a blow ends the talk
        }
        reveal();
        return super.hurt(source, amount);
    }

    /** Blocks per second this thief covers at this speed factor on level ground, from what was measured in the game: 43.17 x (attribute x factor)^2. */
    double estimatedSpeed(double factor) {
        double s = getAttributeValue(Attributes.MOVEMENT_SPEED) * factor;
        return 43.17D * s * s;
    }

    // ---------------------------------------------------- resentment, the whip's questions, ransom ----------------------------------------------------

    float grudge() {
        return grudge;
    }

    void addGrudge(float amount) {
        if (ThiefConfig.GRUDGE_ENABLED.get()) {
            grudge = Math.min(100.0F, grudge + amount);
        }
    }

    boolean isVengeful() {
        return level().getGameTime() < vengefulUntil;
    }

    boolean isHurried() {
        return level().getGameTime() < hurriedUntil;
    }

    boolean isNegotiating() {
        return negotiatingFor != null;
    }

    UUID negotiatingFor() {
        return negotiatingFor;
    }

    /** An offer to buy this captive back stands. The others do not try to free it meanwhile. */
    boolean ransomActive() {
        return level().getGameTime() < ransomUntil;
    }

    void rememberCaptor(Player player) {
        captor = player.getUUID();
        captorRef = player;
    }

    /** A lash on a thief that is tied up: it resents it, and may tell what it knows. */
    void lashed(Player player) {
        rememberCaptor(player);
        addGrudge(12.0F);
        double chance = confessChanceOverride != null ? confessChanceOverride : ThiefConfig.CONFESS_CHANCE.get();
        if (!confessed && random.nextDouble() < chance) {
            confess(player);
        }
    }

    /**
     * It tells where the other thieves that are free are, within 128 blocks, and they shine for half a minute, so that they can be seen through walls.
     * It can tell it once. A thief that knows of none says so, and may be asked again.
     */
    void confess(Player player) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        java.util.List<ThiefEntity> others = server.getEntitiesOfClass(ThiefEntity.class, getBoundingBox().inflate(confessRange), t -> t != this && t.isAlive() && !t.tiedUp());        // not the ones that are tied up: where they are is no secret
        if (others.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.thief.confess_nothing"), false);
            return;
        }
        StringBuilder where = new StringBuilder();
        for (ThiefEntity t : others) {
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
            where.append(where.length() == 0 ? "" : "; ").append(t.blockPosition().toShortString());
        }
        confessed = true;
        player.displayClientMessage(Component.translatable("message.thief.confess", others.size(), where.toString()), false);
    }

    /** Every second: resentment grows; the offer to buy it back is made, runs out; the one that holds the sign stops when it should; revenge and hurry end. */
    private void tickCaptivity(ServerLevel server) {
        long now = server.getGameTime();
        if (tiedUp()) {
            if (tickCount % 20 == 0) {
                addGrudge(isRacked() || isHung() ? 2.0F : isPosted() ? 1.0F : 0.5F);       // shown off, it resents more
            }
            if (grudge >= 30.0F && tickCount % 40 == 0) {
                server.sendParticles(grudge >= 60.0F ? ParticleTypes.ANGRY_VILLAGER : ParticleTypes.SMOKE, getX(), getY() + getBbHeight() + 0.3D, getZ(), 2, 0.2D, 0.1D, 0.2D, 0.0D);
            }
            if (ransomTimer > 0) {
                ransomTimer--;
            } else if (ransomTimer == 0) {
                // The offer needs a free mate that carries something. If there is none yet (a crew that has not stolen anything), look again in five
                // seconds, for up to five minutes: the mates may steal in the meantime.
                if (ransomWilling && crewId != null && offerRansom()) {
                    ransomTimer = -2;
                } else if (ransomWilling && crewId != null && ++ransomTries < 60) {
                    ransomTimer = 100;
                } else {
                    ransomTimer = -2;
                }
            }
        } else {
            if (ransomTimer != -1) {
                ransomTimer = -1;
            }
            ransomUntil = 0;
        }
        if (ransomUntil != 0 && now >= ransomUntil) {
            ransomUntil = 0;
        }
        if (negotiatingFor != null) {
            boolean over = now >= negotiatingUntil;
            if (!over) {
                Entity c = server.getEntity(negotiatingFor);
                over = !(c instanceof ThiefEntity captive) || !captive.isAlive() || !captive.tiedUp();
            }
            if (over) {
                stopNegotiating();
            }
        }
        if (vengefulUntil != 0 && (now >= vengefulUntil || getTarget() == null || !getTarget().isAlive())) {
            vengefulUntil = 0;
            setTarget(null);
        }
        if (hurriedUntil != 0 && now >= hurriedUntil) {
            hurriedUntil = 0;
            getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(HURRY_ID);
        }
    }

    /** Let go, by whoever: with 60 or more resentment it goes for whoever tied it, with 30 or more it runs faster. */
    private void afterFreed() {
        if (level().isClientSide || transferring) {
            return;
        }
        float g = grudge;
        grudge = 0.0F;
        ransomUntil = 0;
        if (!ThiefConfig.GRUDGE_ENABLED.get() || !(level() instanceof ServerLevel server)) {
            return;
        }
        Player target = captorRef != null && captorRef.isAlive() ? captorRef : captor != null ? server.getPlayerByUUID(captor) : null;
        if (target == null) {
            target = server.getNearestPlayer(this, 32.0D);
        }
        long now = server.getGameTime();
        // from 30 resentment it either goes for whoever tied it or runs: by chance, and the more resentful, the likelier the attack (a quarter at 30, three quarters at 100)
        double attackChance = 0.25D + 0.5D * Math.min(1.0D, (g - 30.0D) / 70.0D);
        double roll = grudgeRollOverride != null ? grudgeRollOverride : random.nextDouble();
        if (g >= 30.0F && target != null && roll < attackChance) {
            vengefulUntil = now + 600;
            setTarget(target);
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.3D, getZ(), 6, 0.3D, 0.2D, 0.3D, 0.0D);
        } else if (g >= 30.0F) {
            // faster, but a sprinting player still gains on it: the attribute is capped so that fleeing (a factor 1.15) is under 5.6 blocks a second
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            speed.removeModifier(HURRY_ID);
            double base = speed.getBaseValue();
            double faster = Math.min(base * 1.25D, 0.30D);
            if (faster > base) {
                speed.addTransientModifier(new AttributeModifier(HURRY_ID, "thief hurried", faster - base, AttributeModifier.Operation.ADDITION));
            }
            hurriedUntil = now + 1200;
        }
    }

    /**
     * A crew member that has been caught: one of the others holds up a sign and offers to buy it back with the loot the crew carries.
     * @return whether an offer was made (there has to be a free mate, and something carried)
     */
    boolean offerRansom() {
        if (!(level() instanceof ServerLevel server) || !ThiefConfig.RANSOM_ENABLED.get() || crewId == null) {
            return false;
        }
        java.util.List<ThiefEntity> mates = crewMates(64.0D).stream().filter(m -> !m.tiedUp() && !m.isHung() && !m.isNegotiating()).toList();
        int stacks = 0;
        for (ThiefEntity m : mates) {
            stacks += m.loot.size();
        }
        if (mates.isEmpty() || stacks == 0) {
            return false;
        }
        ThiefEntity negotiator = mates.stream().filter(ThiefEntity::isLookout).findFirst().orElse(mates.get(0));
        int seconds = ThiefConfig.RANSOM_SECONDS.get();
        ransomUntil = server.getGameTime() + seconds * 20L;
        negotiator.startNegotiating(getUUID(), ransomUntil);
        for (ServerPlayer p : server.players()) {
            if (p.distanceToSqr(this) <= 64.0D * 64.0D) {
                p.displayClientMessage(Component.translatable("message.thief.ransom_offer", stacks, negotiator.blockPosition().toShortString(), seconds), false);
            }
        }
        return true;
    }

    /** Where the offer to buy this captive back stands, in words, for {@code /thief debug}. */
    String ransomState() {
        if (!tiedUp()) {
            return "没被绑住（被放开或被解救了）";
        }
        if (crewId == null) {
            return "不是魔盗团的，没有赎金";
        }
        if (!ransomWilling) {
            return "同伙不打算出价（被抓时已决定）";
        }
        if (ransomTimer > 0) {
            return "还有 " + ransomTimer / 20 + " 秒才会出价";
        }
        if (ransomTimer == -2) {
            return ransomTries >= 60 ? "等了五分钟同伙都没有赃物，放弃了" : "已经出过价，或已结束";
        }
        return "准备出价";
    }

    void startNegotiating(UUID captiveId, long until) {
        negotiatingFor = captiveId;
        negotiatingUntil = until;
        getNavigation().stop();
        showLoot();
        addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) Math.max(20L, until - level().getGameTime()), 0, false, false));       // it is to be found
    }

    /** The talk is over (a blow, the time, the captive gone): the sign goes away, and the captive is no longer being bargained for, so the others may rescue it. */
    void stopNegotiating() {
        UUID captiveId = negotiatingFor;
        negotiatingFor = null;
        negotiatingUntil = 0;
        showLoot();
        if (captiveId != null && level() instanceof ServerLevel server && server.getEntity(captiveId) instanceof ThiefEntity captive) {
            captive.ransomUntil = 0;
        }
    }

    /**
     * The captive is let go while the offer stands: the others hand over a share of what they carry (the loot they stole, to be given back),
     * and the one that held the sign puts it away.
     */
    void settleRansom(Player player) {
        if (!(level() instanceof ServerLevel server) || !ransomActive()) {
            return;
        }
        ransomUntil = 0;
        double share = ransomShareOverride != null ? ransomShareOverride : ThiefConfig.RANSOM_SHARE.get();
        int returned = 0;
        for (ThiefEntity mate : crewMates(64.0D)) {
            if (mate.tiedUp()) {
                continue;
            }
            java.util.Iterator<ItemStack> it = mate.loot.iterator();
            while (it.hasNext()) {
                ItemStack stack = it.next();
                if (random.nextDouble() < share) {
                    give(player, stack.copy());
                    it.remove();
                    returned++;
                }
            }
            mate.showLoot();
            if (mate.loot.isEmpty()) {
                ThiefLedger.get(server).recovered(mate.getUUID());
            }
            if (mate.isNegotiating()) {
                mate.negotiatingFor = null;
                mate.negotiatingUntil = 0;
                mate.showLoot();
                mate.swing(InteractionHand.MAIN_HAND);
            }
        }
        player.displayClientMessage(Component.translatable("message.thief.ransom_paid", returned), false);
    }

    /**
     * Brought to the bounty board alive, on a rope: what it still carries goes to the player with the reward (emeralds and experience) and the
     * rope, and the thief is taken away.
     */
    void handIn(Player player, int emeralds) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        for (ItemStack s : loot) {
            give(player, s.copy());
        }
        loot.clear();
        if (emeralds > 0) {
            give(player, new ItemStack(Items.EMERALD, emeralds));
        }
        give(player, new ItemStack(ModItems.ROPE.get()));
        if (magician) {
            give(player, new ItemStack(ModItems.MAGICIAN_TOKEN.get()));
        }
        net.minecraft.world.entity.ExperienceOrb.award(server, position(), magician ? 60 : 20);
        ThiefLedger.get(server).recovered(getUUID());
        transferring = true;
        if (isLeashed()) {
            transferring = true;
            dropLeash(true, false);
            transferring = false;
        }
        transferring = false;
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0D, getZ(), 12, 0.4D, 0.5D, 0.4D, 0.0D);
        server.playSound(null, blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        discard();
    }

    /**
     * A thief put together for the picture in the guide: not in the world, only drawn. {@code look} is one of thief, robber, lookout, sign, magician,
     * copy, led, posted, hung, racked.
     */
    public static ThiefEntity forGuide(Level level, String look) {
        ThiefEntity t = new ThiefEntity(ModEntities.THIEF.get(), level);
        switch (look) {
            case "robber" -> t.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_INGOT));
            case "lookout" -> t.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SPYGLASS));
            case "sign" -> t.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_SIGN));
            case "magician" -> {
                t.entityData.set(MAGICIAN_LOOK, true);
                t.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            }
            case "copy" -> t.entityData.set(MAGICIAN_LOOK, true);
            case "led" -> t.entityData.set(TIED, true);
            case "posted" -> {
                t.entityData.set(TIED, true);
                t.entityData.set(POSTED, true);
            }
            case "hung" -> {
                t.entityData.set(TIED, true);
                t.entityData.set(HUNG, true);
                t.entityData.set(HANG_ROPE, 0.8F);
            }
            case "racked" -> {
                t.entityData.set(TIED, true);
                t.entityData.set(RACKED, true);
            }
            default -> {
            }
        }
        return t;
    }

    // ---------------------------------------------------- the crew, rescue ----------------------------------------------------

    UUID crewId() {
        return crewId;
    }

    boolean isLookout() {
        return lookout;
    }

    int alertTicks() {
        return alertTicks;
    }

    long nextPass() {
        return nextPass;
    }

    boolean recentlyHurt() {
        return tickCount - lastHurtTick < 100;
    }

    void joinCrew(UUID crew, boolean asLookout) {
        crewId = crew;
        lookout = asLookout;
        // a crew moves faster than a lone thief, but not faster than a player: see the comment on the setting
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ThiefConfig.CREW_SPEED.get());
    }

    /** The other living members of the crew within a distance. */
    java.util.List<ThiefEntity> crewMates(double range) {
        if (crewId == null) {
            return java.util.List.of();
        }
        return level().getEntitiesOfClass(ThiefEntity.class, getBoundingBox().inflate(range), e -> e != this && e.isAlive() && crewId.equals(e.crewId));
    }

    /** A lookout with robbers to watch for does not steal itself. One that has been left alone does. */
    boolean isIdleLookout() {
        // the magician and its copies never steal; a lookout does not while there are robbers to watch for
        return magician || decoyOf != null || (lookout && crewMates(48.0D).stream().anyMatch(m -> !m.lookout && !m.magician));
    }

    // ---------------------------------------------------- the magician ----------------------------------------------------

    public boolean isMagician() {
        return magician;
    }

    public boolean isDecoy() {
        return decoyOf != null;
    }

    /** Whether it is dressed as the magician: the boss, and its copies. */
    public boolean looksLikeMagician() {
        return entityData.get(MAGICIAN_LOOK);
    }

    /** Made the magician: forty health, a boss bar, a name. */
    void becomeMagician() {
        magician = true;
        entityData.set(MAGICIAN_LOOK, true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
        setHealth(40.0F);
        setCustomName(Component.translatable("entity.thief.magician"));
        xpReward = 30;
        setPersistenceRequired();
    }

    /** Made a copy of the magician: it looks the same, has no more than a puff of smoke in it, and does not last. */
    void becomeDecoy(ThiefEntity master) {
        decoyOf = master.getUUID();
        decoyTicks = ThiefConfig.MAGICIAN_COPY_SECONDS.get() * 20;
        entityData.set(MAGICIAN_LOOK, true);
        xpReward = 0;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0D);
        setHealth(1.0F);
    }

    /** A copy ends in a puff of smoke. */
    void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), 20, 0.3D, 0.4D, 0.3D, 0.03D);
            server.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 1.5F);
        }
        discard();
    }

    int decoyTicksLeft() {
        return decoyTicks;
    }

    java.util.List<ThiefEntity> decoys() {
        return level().getEntitiesOfClass(ThiefEntity.class, getBoundingBox().inflate(48.0D), t -> t.decoyOf != null && getUUID().equals(t.decoyOf) && t.isAlive());
    }

    /**
     * Makes copies of itself until there are three, then changes places with one of them, so that whichever one the player has been watching is
     * not necessarily the real one.
     * @return how many copies there are
     */
    int castIllusion() {
        if (!(level() instanceof ServerLevel server) || !magician) {
            return 0;
        }
        java.util.List<ThiefEntity> mine = new ArrayList<>(decoys());
        for (int made = mine.size(); made < 3; made++) {
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = 3.0D + random.nextDouble() * 3.0D;
                BlockPos spot = NightVisits.groundNear(server, (int) Math.floor(getX() + Math.cos(angle) * dist), (int) Math.floor(getZ() + Math.sin(angle) * dist), blockPosition().getY());
                if (spot == null) {
                    continue;
                }
                ThiefEntity copy = ModEntities.THIEF.get().create(server);
                if (copy == null) {
                    break;
                }
                copy.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                copy.becomeDecoy(this);
                server.addFreshEntity(copy);
                server.sendParticles(ParticleTypes.POOF, copy.getX(), copy.getY() + 1.0D, copy.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.02D);
                mine.add(copy);
                break;
            }
        }
        if (!mine.isEmpty()) {
            ThiefEntity other = mine.get(random.nextInt(mine.size()));
            net.minecraft.world.phys.Vec3 a = position(), b = other.position();
            server.sendParticles(ParticleTypes.POOF, a.x, a.y + 1.0D, a.z, 12, 0.3D, 0.4D, 0.3D, 0.02D);
            server.sendParticles(ParticleTypes.POOF, b.x, b.y + 1.0D, b.z, 12, 0.3D, 0.4D, 0.3D, 0.02D);
            moveTo(b.x, b.y, b.z, getYRot(), 0.0F);
            other.moveTo(a.x, a.y, a.z, other.getYRot(), 0.0F);
            getNavigation().stop();
            other.getNavigation().stop();
            server.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        return mine.size();
    }

    /** It slips its bonds: a puff of smoke, the rope on the ground, and the copies come at once to cover it. */
    private void escape(ServerLevel server) {
        if (isHung()) {
            releaseFromHang(true);
        } else if (isRacked()) {
            releaseFromRack(true);
        } else if (isPosted()) {
            releaseFromPost(true);
        } else {
            dropLeash(true, true);
        }
        server.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0D, getZ(), 30, 0.4D, 0.6D, 0.4D, 0.05D);
        for (ServerPlayer p : server.players()) {
            if (p.distanceToSqr(this) <= 64.0D * 64.0D) {
                p.displayClientMessage(Component.translatable("message.thief.magician_escaped", blockPosition().toShortString()), false);
            }
        }
        boundTicks = 0;
        castIllusion();
        nextIllusion = server.getGameTime() + ThiefConfig.MAGICIAN_ILLUSION_SECONDS.get() * 20L;
    }

    /** A magician can only be tied when it has been worn down; any other thief, any time. */
    boolean canBeTied() {
        return !magician || getHealth() <= getMaxHealth() * ThiefConfig.MAGICIAN_TIE_HEALTH.get();
    }

    /** Every tick: the boss bar follows its health; it makes its copies when a player is near; a copy ends when it should. */
    final MagicianTricks tricks = new MagicianTricks(this);

    /** The ledger takes note of what the magician reached for from afar. */
    void recordTheft(BlockPos from) {
        if (level() instanceof ServerLevel server && hasLoot()) {
            ThiefLedger.get(server).add(getUUID(), server.getGameTime(), level().dimension().location().toString(), from, summary());
        }
    }

    private void tickMagician(ServerLevel server) {
        long now = server.getGameTime();
        if (decoyOf != null) {
            Entity master = server.getEntity(decoyOf);
            if (--decoyTicks <= 0 || !(master instanceof ThiefEntity m) || !m.isAlive() || m.tiedUp()) {
                vanish();
            }
            return;
        }
        if (!magician) {
            return;
        }
        bossBar.setProgress(Math.max(0.0F, getHealth() / getMaxHealth()));
        // tied up other than in a player's hand, it gets loose by itself, and the sooner the longer it has been tied
        boolean inHands = isLeashed() && getLeashHolder() instanceof Player;
        if (tiedUp() && !inHands) {
            boundTicks++;
            if (boundTicks % 20 == 0) {
                double median = ThiefConfig.MAGICIAN_ESCAPE_SECONDS.get();
                double perSecond = Math.min(0.1D, 2.0D * Math.log(2.0D) / (median * median) * (boundTicks / 20.0D));
                double escapeRoll = escapeRollOverride != null ? escapeRollOverride : random.nextDouble();
                if (escapeRoll < perSecond) {
                    escape(server);
                    return;
                }
            }
        } else {
            boundTicks = 0;
        }
        if (tiedUp()) {
            for (ThiefEntity d : decoys()) {
                d.vanish();        // caught, it can make no more, and what it made is gone
            }
            tricks.clear();
            return;
        }
        tricks.tick(server, now);
        if (nextIllusion == 0) {
            nextIllusion = now + 60;
        }
        if (now >= nextIllusion && (!StealGoal.requirePlayerNearby || server.getNearestPlayer(this, 24.0D) != null)) {
            castIllusion();
            nextIllusion = now + ThiefConfig.MAGICIAN_ILLUSION_SECONDS.get() * 20L;
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (magician) {
            bossBar.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        bossBar.removeAllPlayers();
    }

    /** The lookout's whistle: the whole crew nearby draws back for ten seconds instead of stealing. */
    void alertCrew() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        server.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.HOSTILE, 3.0F, 1.9F);
        alertTicks = 200;
        for (ThiefEntity mate : crewMates(32.0D)) {
            mate.alertTicks = 200;
        }
    }

    /** All the loot goes to a mate, who runs with it; the record of the theft follows, and this one keeps shining as a decoy. */
    void passLootTo(ThiefEntity mate) {
        if (!(level() instanceof ServerLevel server) || loot.isEmpty()) {
            return;
        }
        for (ItemStack s : loot) {
            mate.addLoot(s);
        }
        loot.clear();
        mate.hasStolen = true;
        mate.setPersistenceRequired();
        mate.showLoot();
        showLoot();
        long cooldown = server.getGameTime() + 400;
        nextPass = cooldown;
        mate.nextPass = cooldown;
        ThiefLedger.get(server).handedOver(getUUID(), mate.getUUID());
        swing(InteractionHand.MAIN_HAND);
        server.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 1.0F, 1.2F);
        server.sendParticles(ParticleTypes.POOF, mate.getX(), mate.getY() + 1.0D, mate.getZ(), 8, 0.2D, 0.3D, 0.2D, 0.01D);
    }

    /** Once tied up, a rescue may come after a while. */
    private void becameCaptive() {
        if (ransomTimer == -1 && crewId != null) {
            ransomTimer = 200 + random.nextInt(200);        // ten to twenty seconds, then the others may make an offer
            ransomTries = 0;
            ransomWilling = random.nextDouble() < (ransomChanceOverride != null ? ransomChanceOverride : ThiefConfig.RANSOM_CHANCE.get());
        }
        if (rescueTimer == -1) {
            rescueTimer = ThiefConfig.RESCUE_DELAY_SECONDS.get() * 20 / 2 + random.nextInt(Math.max(1, ThiefConfig.RESCUE_DELAY_SECONDS.get() * 20));
        }
    }

    /** Cut loose by another thief: off the post, rack or rope, and the rope that tied it falls to the ground. */
    void freedBy(ThiefEntity rescuer) {
        if (level().isClientSide) {
            return;
        }
        if (isHung()) {
            releaseFromHang(true);
        } else if (isRacked()) {
            releaseFromRack(true);
        } else if (isPosted()) {
            releaseFromPost(true);
        } else {
            dropLeash(true, true);
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.0D, getZ(), 8, 0.3D, 0.5D, 0.3D, 0.0D);
        }
    }

    /** Sends another thief to rescue this one, from some blocks away. @return the thief, or null if there was no room for one */
    ThiefEntity sendRescuer(int minDist, int maxDist) {
        if (!(level() instanceof ServerLevel server)) {
            return null;
        }
        return NightVisits.spawnNear(server, blockPosition(), minDist, maxDist, null, new java.util.Random(random.nextLong()));
    }

    /** For the tests that measure how fast it walks: nothing else may give it orders. */
    void clearGoalsForTest() {
        goalSelector.removeAllGoals(g -> true);
    }

    /** The goals that are running now, for {@code /thief debug}. */
    String runningGoals() {
        return goalSelector.getRunningGoals().map(w -> w.getGoal().getClass().getSimpleName()).collect(java.util.stream.Collectors.joining(", "));
    }

    /** The thief's hands are tied and the rope is held by someone or something. */
    public boolean tiedUp() {
        return isRopeTied() && (isLeashed() || isRacked() || isPosted() || isHung());
    }

    /** Hands tied with a rope (and not just led on the game's own lead): it gives up running and stealing, and the rope is given back. */
    public boolean isRopeTied() {
        return entityData.get(TIED);
    }

    private void setRopeTied(boolean tied) {
        entityData.set(TIED, tied);
    }

    void tieWithRope(net.minecraft.world.entity.Entity holder) {
        if (holder instanceof Player p) {
            rememberCaptor(p);
        }
        reveal();
        setRopeTied(true);
        setPersistenceRequired();
        getNavigation().stop();
        showLoot();
        setLeashedTo(holder, true);
        becameCaptive();
    }

    @Override
    public void dropLeash(boolean sendPacket, boolean dropLead) {
        boolean wasTied = isRopeTied() && isLeashed();
        super.dropLeash(sendPacket, dropLead && !wasTied);       // the game would drop a lead: it was a rope
        if (wasTied) {
            setRopeTied(false);
            showLoot();
            afterFreed();
            if (dropLead && !level().isClientSide) {
                spawnAtLocation(ModItems.ROPE.get());
            }
        }
    }

    // ---------------------------------------------------- searching, the whip, hanging ----------------------------------------------------

    /** Hands tied, and either hung up or held on a rope. Only then can it be searched. */
    public boolean isHung() {
        return entityData.get(HUNG);
    }

    /**
     * How much bare rope to leave between the underside of a block and the hands of a thief hung under it: the rope shows, the thief
     * hangs clear of the ground. It needs three free blocks below the block to hang at all; more room gives a longer rope, up to 1.5.
     * @return the length, or -1 if there is no room
     */
    static double hangGap(net.minecraft.world.level.Level level, BlockPos anchor) {
        int free = 0;
        while (free < 5 && !level.getBlockState(anchor.below(free + 1)).blocksMotion()) {
            free++;
        }
        return free < 3 ? -1.0D : Math.max(MIN_GAP, Math.min(MAX_GAP, free - 2.5D));
    }

    /** Tied hand and foot to a rack: arms out to the upper left and right, legs out to the lower left and right. */
    public boolean isRacked() {
        return entityData.get(RACKED);
    }

    /** The block this thief hangs from, or null. */
    BlockPos hangPos() {
        return hungFrom;
    }

    /** The bottom block of the rack this thief is tied to, or null. */
    BlockPos rackPos() {
        return rackPos;
    }

    /** Tied to a rack, standing in front of it facing out. The lead it was brought on is let go; the rack's straps hold it now. */
    /** For finding out why a test failed: who bound whom to which rack. */
    static final java.util.Map<BlockPos, String> RACKED_BY = new java.util.concurrent.ConcurrentHashMap<>();

    void bindToRack(BlockPos bottom, net.minecraft.core.Direction facing) {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (e.getClassName().endsWith("ThiefGameTests")) {
                RACKED_BY.put(bottom.immutable(), e.getMethodName() + " at " + getUUID());
                break;
            }
        }
        reveal();
        if (isLeashed()) {
            transferring = true;
            dropLeash(true, false);
            transferring = false;
        }
        hungFrom = null;
        entityData.set(HUNG, false);
        setRopeTied(true);
        rackPos = bottom.immutable();
        rackFacing = facing;
        entityData.set(RACKED, true);
        setNoGravity(true);
        setPersistenceRequired();
        getNavigation().stop();
        showLoot();
        positionRack();
        becameCaptive();
    }

    private void positionRack() {
        if (rackPos != null) {
            moveTo(rackPos.getX() + 0.5D + rackFacing.getStepX() / 16.0D, rackPos.getY() + RackBlock.LIFT, rackPos.getZ() + 0.5D + rackFacing.getStepZ() / 16.0D, rackFacing.toYRot(), 0.0F);
            setYHeadRot(rackFacing.toYRot());
            yBodyRot = rackFacing.toYRot();
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    /** Let down from the rack; the rope that tied it comes back as an item. */
    void releaseFromRack(boolean dropRope) {
        if (rackPos == null) {
            return;
        }
        rackPos = null;
        entityData.set(RACKED, false);
        setRopeTied(false);
        setNoGravity(false);
        showLoot();
        afterFreed();
        if (dropRope && !level().isClientSide) {
            spawnAtLocation(ModItems.ROPE.get());
        }
    }

    /** Tied all round to a tree trunk, a post or a pillar, back against it, arms pulled back, ropes wound round the body. */
    public boolean isPosted() {
        return entityData.get(POSTED);
    }

    BlockPos postPos() {
        return postPos;
    }

    net.minecraft.core.Direction postDir() {
        return postDir;
    }

    /** How far from the face of what it is tied to the middle of the thief stands: its back is almost on it, but its eyes are not in the block. */
    private static final double POST_GAP = 0.26D;

    /** Is there room to stand at this side of the post: ground to stand on, two blocks of air? */
    static boolean hasRoomAtPost(net.minecraft.world.level.Level level, BlockPos post, net.minecraft.core.Direction side) {
        BlockPos at = post.relative(side);
        return !level.getBlockState(at).blocksMotion() && !level.getBlockState(at.above()).blocksMotion() && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
    }

    /** Tied to the post: standing on the given side of it with its back to it, facing out. The lead it was brought on is let go. */
    void bindToPost(BlockPos post, net.minecraft.core.Direction side) {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (e.getClassName().endsWith("ThiefGameTests")) {
                RACKED_BY.put(post.immutable(), e.getMethodName() + " at " + getUUID() + " side " + side);
                break;
            }
        }
        reveal();
        if (isLeashed()) {
            transferring = true;
            dropLeash(true, false);
            transferring = false;
        }
        hungFrom = null;
        entityData.set(HUNG, false);
        setRopeTied(true);
        postPos = post.immutable();
        postDir = side;
        entityData.set(POSTED, true);
        setNoGravity(true);
        setPersistenceRequired();
        getNavigation().stop();
        showLoot();
        positionPost();
        becameCaptive();
    }

    private void positionPost() {
        if (postPos == null) {
            return;
        }
        net.minecraft.world.phys.shapes.VoxelShape shape = level().getBlockState(postPos).getShape(level(), postPos);
        double x = postPos.getX() + 0.5D, z = postPos.getZ() + 0.5D;
        // from the face of the shape, not of the block: a fence post is thin
        switch (postDir) {
            case EAST -> x = postPos.getX() + (shape.isEmpty() ? 1.0D : shape.max(net.minecraft.core.Direction.Axis.X)) + POST_GAP;
            case WEST -> x = postPos.getX() + (shape.isEmpty() ? 0.0D : shape.min(net.minecraft.core.Direction.Axis.X)) - POST_GAP;
            case SOUTH -> z = postPos.getZ() + (shape.isEmpty() ? 1.0D : shape.max(net.minecraft.core.Direction.Axis.Z)) + POST_GAP;
            default -> z = postPos.getZ() + (shape.isEmpty() ? 0.0D : shape.min(net.minecraft.core.Direction.Axis.Z)) - POST_GAP;
        }
        BlockPos below = postPos.relative(postDir).below();
        net.minecraft.world.phys.shapes.VoxelShape ground = level().getBlockState(below).getCollisionShape(level(), below);
        double y = ground.isEmpty() ? postPos.getY() : below.getY() + ground.max(net.minecraft.core.Direction.Axis.Y);
        moveTo(x, y, z, postDir.toYRot(), 0.0F);
        setYHeadRot(postDir.toYRot());
        yBodyRot = postDir.toYRot();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    /** Untied from the post; the rope comes back as an item. */
    void releaseFromPost(boolean dropRope) {
        if (postPos == null) {
            return;
        }
        postPos = null;
        entityData.set(POSTED, false);
        setRopeTied(false);
        setNoGravity(false);
        showLoot();
        afterFreed();
        if (dropRope && !level().isClientSide) {
            spawnAtLocation(ModItems.ROPE.get());
        }
    }

    /** Hung by the wrists from the underside of a block, a length of rope showing: off the ground, unable to move, until it is let down. */
    void hangFrom(BlockPos anchor) {
        double gap = hangGap(level(), anchor);
        reveal();
        if (isLeashed()) {
            transferring = true;
            dropLeash(true, false);        // the lead it was brought on: the rope that hangs it is drawn by the client, not a lead
            transferring = false;
        }
        setRopeTied(true);
        entityData.set(HUNG, true);
        hangGap = gap < 0 ? MIN_GAP : gap;
        entityData.set(HANG_ROPE, (float) hangGap);
        hungFrom = anchor.immutable();
        setNoGravity(true);
        showLoot();
        getNavigation().stop();
        setPersistenceRequired();
        positionHung();
        becameCaptive();
    }

    /** The length of rope showing above the hands of a thief that is hung up. */
    public float hangRopeLength() {
        return entityData.get(HANG_ROPE);
    }

    /** Let down from where it hangs; the rope that held it comes back as an item. */
    void releaseFromHang(boolean dropRope) {
        if (hungFrom == null) {
            return;
        }
        hungFrom = null;
        entityData.set(HUNG, false);
        setRopeTied(false);
        setNoGravity(false);
        showLoot();
        afterFreed();
        if (dropRope && !level().isClientSide) {
            spawnAtLocation(ModItems.ROPE.get());
        }
    }

    private void positionHung() {
        if (hungFrom != null) {
            // the hands, raised, are at the end of the rope: the feet are the length of the arms and of the body below that
            moveTo(hungFrom.getX() + 0.5D, hungFrom.getY() - hangGap - HANDS_UP, hungFrom.getZ() + 0.5D, getYRot(), 0.0F);
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }

    /** The lead is tied to the hands, which are bound in front of it at the waist: not to the head, where the game ties a lead. */
    @Override
    protected net.minecraft.world.phys.Vec3 getLeashOffset() {
        return isRopeTied() && isLeashed() ? new net.minecraft.world.phys.Vec3(0.0D, 0.95D, 0.55D) : super.getLeashOffset();
    }

    @Override
    protected boolean isImmobile() {
        return hungFrom != null || rackPos != null || postPos != null || super.isImmobile();
    }

    /**
     * Hands a thing to the player without putting it in the slot of the hand: the hand stays free, so that a thief can be searched again
     * and let down with an empty hand. It joins a stack of the same kind, or goes to an empty slot, or else wherever there is room, or is dropped.
     */
    static void give(Player player, ItemStack stack) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int hand = inv.selected;
        for (int i = 0; i < inv.items.size() && !stack.isEmpty(); i++) {
            ItemStack have = inv.items.get(i);
            if (i != hand && !have.isEmpty() && ItemStack.isSameItemSameTags(have, stack) && have.getCount() < have.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), have.getMaxStackSize() - have.getCount());
                have.grow(n);
                have.setPopTime(5);
                stack.shrink(n);
            }
        }
        for (int i = 0; i < inv.items.size() && !stack.isEmpty(); i++) {
            if (i != hand && inv.items.get(i).isEmpty()) {
                inv.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        if (!stack.isEmpty() && !inv.add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Takes one thing out of its pockets and gives it to the player; the last one marks the theft as put right. */
    private InteractionResult search(Player player) {
        if (!level().isClientSide) {
            if (loot.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.thief.search_empty"), true);
            } else {
                ItemStack found = loot.remove(0);
                Component name = found.getHoverName();
                rememberCaptor(player);
                addGrudge(3.0F);        // being gone through is not nice
                give(player, found);
                level().playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 1.0F, 1.0F);
                showLoot();
                if (loot.isEmpty() && level() instanceof ServerLevel server) {
                    ThiefLedger.get(server).recovered(getUUID());
                }
                player.displayClientMessage(Component.translatable(loot.isEmpty() ? "message.thief.search_done" : "message.thief.search_found", name), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /**
     * What an empty hand does to a thief that is tied up: sneaking searches it, otherwise it is let down from its post or its hanging
     * (when the rope is held by the player, the game itself lets it go). This is the first thing a click on it reaches.
     */
    @Override
    public InteractionResult interactAt(Player player, net.minecraft.world.phys.Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && tiedUp()) {
            ItemStack held = player.getItemInHand(hand);
            if (player.isShiftKeyDown() && canSearchWith(held)) {
                return search(player);
            }
            if (held.isEmpty() && !player.isShiftKeyDown() && (isRacked() || isPosted() || isHung() || getLeashHolder() == player)) {
                if (!level().isClientSide) {
                    settleRansom(player);        // a captive that is let go while an offer stands is paid for
                }
            }
            if (held.isEmpty() && !player.isShiftKeyDown() && (isRacked() || isPosted() || isHung() || getLeashHolder() != player)) {
                if (!level().isClientSide) {
                    if (isHung()) {
                        releaseFromHang(!player.getAbilities().instabuild);
                    } else if (isRacked()) {
                        releaseFromRack(!player.getAbilities().instabuild);
                    } else if (isPosted()) {
                        releaseFromPost(!player.getAbilities().instabuild);
                    } else {
                        dropLeash(true, !player.getAbilities().instabuild);
                    }
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.interactAt(player, hit, hand);
    }

    /**
     * Sneaking and clicking searches, with an empty hand or with anything that has no use of its own on a thief. What is found goes into
     * the hand's slot, so the hand cannot be required to stay empty.
     */
    static boolean canSearchWith(ItemStack held) {
        return held.isEmpty() || !(held.getItem() instanceof RopeItem || held.getItem() instanceof WhipItem || held.is(Items.LEAD) || held.is(Items.NAME_TAG));
    }

    /** A lash on a thief that is tied up shakes one of the things it stole loose. */
    void shakeLoot() {
        if (!(level() instanceof ServerLevel server) || loot.isEmpty()) {
            return;
        }
        ItemStack spilled = loot.remove(random.nextInt(loot.size()));
        ItemEntity drop = spawnAtLocation(spilled);
        if (drop != null) {
            drop.setDeltaMovement((random.nextDouble() - 0.5D) * 0.3D, 0.25D, (random.nextDouble() - 0.5D) * 0.3D);
        }
        showLoot();
        if (loot.isEmpty()) {
            ThiefLedger.get(server).recovered(getUUID());
        }
    }


    /** For the tests: the goals that a thief spawned without a mind of its own (which clears them) had, put back. */
    void giveGoalsBackForTest() {
        registerGoals();
    }

    /** For the tests: it carries nothing. */
    void clearLootForTest() {
        loot.clear();
    }

    java.util.List<ItemStack> lootForTest() {
        return loot;
    }

    int lootCount() {
        return loot.size();
    }

    public boolean hasLoot() {
        return !loot.isEmpty();
    }

    boolean hasStolen() {
        return hasStolen;
    }

    /** Put away what was taken; the first thing is shown in the thief's hand. */
    void addLoot(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (ItemStack have : loot) {
            if (ItemStack.isSameItemSameTags(have, stack) && have.getCount() + stack.getCount() <= have.getMaxStackSize()) {
                have.grow(stack.getCount());
                return;
            }
        }
        loot.add(stack.copy());
    }

    /** Called when the stealing is over: it will run, and the ledger takes note. */
    void finishedStealing(BlockPos from) {
        hasStolen = true;
        setPersistenceRequired();
        showLoot();
        addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));       // 20 seconds in which it can be seen from afar
        if (level() instanceof ServerLevel server && hasLoot()) {
            ThiefLedger.get(server).add(getUUID(), server.getGameTime(), level().dimension().location().toString(), from, summary());
        }
    }

    private List<String> summary() {
        List<String> out = new ArrayList<>();
        for (ItemStack s : loot) {
            var key = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (key != null) {
                out.add(key + "*" + s.getCount());
            }
        }
        return out;
    }

    void showLoot() {
        if (isNegotiating()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_SIGN));        // the sign of the one that offers to buy a captive back
            return;
        }
        setItemSlot(EquipmentSlot.MAINHAND, loot.isEmpty() || isRopeTied() ? ItemStack.EMPTY : loot.get(0).copyWithCount(1));        // tied hands hold nothing
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server) {
            if (hasLoot() && tickCount % 20 == 0) {
                // a thin column of smoke over a thief that carries loot, to be seen from a distance
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 2.0D, getZ(), 1, 0.1D, 0.0D, 0.1D, 0.01D);
            }
            if (alertTicks > 0) {
                alertTicks--;
            }
            tickCaptivity(server);
            tickMagician(server);
            if (lookout && tickCount % 20 == 0 && alertTicks <= 0 && !tiedUp() && server.getNearestPlayer(this, 24.0D) != null && !crewMates(32.0D).isEmpty()) {
                alertCrew();
            }
            if (tiedUp()) {
                if (rescueTimer > 0) {
                    rescueTimer--;
                } else if (rescueTimer == 0) {
                    rescueTimer = -2;        // once per capture
                    boolean someoneFree = !server.getEntitiesOfClass(ThiefEntity.class, getBoundingBox().inflate(48.0D), c -> c != this && c.isAlive() && !c.tiedUp()).isEmpty();
                    if (autoRescuers && ThiefConfig.RESCUE_ENABLED.get() && !someoneFree && random.nextDouble() < ThiefConfig.RESCUE_CHANCE.get()
                            && (!StealGoal.requirePlayerNearby || server.getNearestPlayer(this, 128.0D) != null)) {
                        sendRescuer(18, 28);
                    }
                }
            } else if (rescueTimer != -1) {
                rescueTimer = -1;
            }
            if (postPos != null) {
                BlockPos below = postPos.relative(postDir).below();
                if (RopeKnotEntity.canTieTo(level().getBlockState(postPos)) && !level().getBlockState(below).getCollisionShape(level(), below).isEmpty()) {
                    positionPost();
                } else {
                    releaseFromPost(true);        // what it was tied to, or the ground it stood on, is gone
                }
            }
            if (rackPos != null) {
                BlockState rack = level().getBlockState(rackPos);
                if (RackBlock.isAnchor(rack)) {
                    rackFacing = rack.getValue(RackBlock.FACING);
                    positionRack();
                } else {
                    releaseFromRack(true);        // the rack is gone
                }
            }
            if (hungFrom != null) {
                if (level().getBlockState(hungFrom).isFaceSturdy(level(), hungFrom, net.minecraft.core.Direction.DOWN)) {
                    positionHung();
                } else {
                    releaseFromHang(true);        // what it hung from is gone
                }
            }
            if (disguiseType != null) {
                if (--disguiseTicks <= 0) {
                    reveal();
                }
            }
            if (hasLoot() && tickCount % 100 == 0) {
                ThiefLedger.get(server).moved(getUUID(), blockPosition());
            }
            // one that has taken nothing and has been about for long enough goes home, unless someone is watching it
            if (!hasLoot() && !isLeashed() && tickCount > ThiefConfig.LIFETIME_TICKS.get() && server.getNearestPlayer(this, 16.0D) == null) {
                discard();
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !hasLoot();
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (level() instanceof ServerLevel server) {
            ThiefLedger.get(server).defeated(getUUID(), blockPosition());
        }
        for (ItemStack s : loot) {
            spawnAtLocation(s);
        }
        loot.clear();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (magician) {
            spawnAtLocation(new ItemStack(ModItems.MAGICIAN_TOKEN.get()));
            spawnAtLocation(new ItemStack(Items.EMERALD, 3));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (ItemStack s : loot) {
            list.add(s.save(new CompoundTag()));
        }
        tag.put("Loot", list);
        tag.putBoolean("HasStolen", hasStolen);
        tag.putBoolean("RopeTied", isRopeTied());
        tag.putDouble("HangGap", hangGap);
        if (crewId != null) {
            tag.putUUID("CrewId", crewId);
        }
        tag.putBoolean("Lookout", lookout);
        tag.putFloat("Grudge", grudge);
        tag.putBoolean("Magician", magician);
        if (decoyOf != null) {
            tag.putUUID("DecoyOf", decoyOf);
            tag.putInt("DecoyTicks", decoyTicks);
        }
        tag.putBoolean("Confessed", confessed);
        if (captor != null) {
            tag.putUUID("Captor", captor);
        }
        if (postPos != null) {
            tag.putLong("PostPos", postPos.asLong());
            tag.putString("PostDir", postDir.getName());
        }
        if (rackPos != null) {
            tag.putLong("RackPos", rackPos.asLong());
            tag.putString("RackFacing", rackFacing.getName());
        }
        if (hungFrom != null) {
            tag.putLong("HungFrom", hungFrom.asLong());
        }
        tag.putString("Disguise", entityData.get(DISGUISE));
        tag.putInt("DisguiseTicks", disguiseTicks);
        tag.putInt("Potions", potions);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loot.clear();
        for (Tag t : tag.getList("Loot", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.of((CompoundTag) t);
            if (!s.isEmpty()) {
                loot.add(s);
            }
        }
        hasStolen = tag.getBoolean("HasStolen");
        setRopeTied(tag.getBoolean("RopeTied"));
        hangGap = tag.contains("HangGap") ? tag.getDouble("HangGap") : MAX_GAP;
        crewId = tag.hasUUID("CrewId") ? tag.getUUID("CrewId") : null;
        lookout = tag.getBoolean("Lookout");
        grudge = tag.getFloat("Grudge");
        if (tag.getBoolean("Magician")) {
            becomeMagician();
        }
        if (tag.hasUUID("DecoyOf")) {
            decoyOf = tag.getUUID("DecoyOf");
            decoyTicks = tag.getInt("DecoyTicks");
            entityData.set(MAGICIAN_LOOK, true);
        }
        confessed = tag.getBoolean("Confessed");
        captor = tag.hasUUID("Captor") ? tag.getUUID("Captor") : null;
        postPos = tag.contains("PostPos") ? BlockPos.of(tag.getLong("PostPos")) : null;
        net.minecraft.core.Direction savedSide = net.minecraft.core.Direction.byName(tag.getString("PostDir"));
        postDir = savedSide != null && savedSide.getAxis().isHorizontal() ? savedSide : net.minecraft.core.Direction.SOUTH;
        entityData.set(POSTED, postPos != null);
        if (postPos != null) {
            setNoGravity(true);
        }
        rackPos = tag.contains("RackPos") ? BlockPos.of(tag.getLong("RackPos")) : null;
        net.minecraft.core.Direction savedFacing = net.minecraft.core.Direction.byName(tag.getString("RackFacing"));
        rackFacing = savedFacing != null ? savedFacing : net.minecraft.core.Direction.SOUTH;
        entityData.set(RACKED, rackPos != null);
        if (rackPos != null) {
            setNoGravity(true);
        }
        hungFrom = tag.contains("HungFrom") ? BlockPos.of(tag.getLong("HungFrom")) : null;
        entityData.set(HUNG, hungFrom != null);
        entityData.set(HANG_ROPE, (float) hangGap);
        if (hungFrom != null) {
            setNoGravity(true);
        }
        if (tag.contains("Potions")) {
            potions = tag.getInt("Potions");
        }
        disguiseTicks = tag.getInt("DisguiseTicks");
        EntityType<?> saved = disguiseTicks > 0 ? resolve(tag.getString("Disguise")) : null;
        disguiseType = saved;
        entityData.set(DISGUISE, saved == null ? "" : tag.getString("Disguise"));
        showLoot();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }
}
