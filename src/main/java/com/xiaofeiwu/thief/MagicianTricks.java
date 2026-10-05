package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * What the magician does besides making copies, all of it while a player is near, none of it while it is tied:
 * it reaches for a chest from where it stands (up to three stacks, one a second, a line of sparks to show it, and a blow stops it), and may leave
 * props in the slots so that the chest looks as full as before; it throws a card at a player it can see (a shield stops it); and it throws a smoke
 * bomb at a player's feet and, in the confusion, changes places.
 */
final class MagicianTricks {

    static final double GRAB_RANGE = 14.0D;
    private static final double CARD_RANGE = 16.0D;
    private static final double CARD_SPEED = 0.8D;      // blocks a tick
    private static final int CARD_LIFE = 24;

    private final ThiefEntity magician;
    private final List<Card> cards = new ArrayList<>();
    private long nextGrab;
    private long nextCard;
    private long nextSmoke;
    private BlockPos grabPos;
    private int grabLeft;
    private int grabTimer;
    private boolean swapped;

    /** Test hooks. */
    Double swapRollOverride;
    final List<Player> extraTargets = new ArrayList<>();

    private static final class Card {
        Vec3 pos;
        final Vec3 velocity;
        int age;

        Card(Vec3 pos, Vec3 velocity) {
            this.pos = pos;
            this.velocity = velocity;
        }
    }

    MagicianTricks(ThiefEntity magician) {
        this.magician = magician;
    }

    boolean grabbing() {
        return grabPos != null;
    }

    /** A blow stops what it is doing, and it waits a while before it tries again. */
    void interrupt() {
        if (grabPos != null) {
            grabPos = null;
            swapped = false;
            if (magician.level() instanceof ServerLevel server) {
                nextGrab = server.getGameTime() + 200;
            }
        }
    }

    void clear() {
        grabPos = null;
        cards.clear();
    }

    private boolean playerNear(ServerLevel server) {
        return !StealGoal.requirePlayerNearby || server.getNearestPlayer(magician, 32.0D) != null;
    }

    void tick(ServerLevel server, long now) {
        tickCards(server);
        if (!playerNear(server)) {
            return;
        }
        if (nextGrab == 0) {
            nextGrab = now + 200;
            nextCard = now + 100;
            nextSmoke = now + 240;
        }
        if (grabPos != null) {
            tickGrab(server);
            return;
        }
        if (now >= nextGrab) {
            nextGrab = now + ThiefConfig.MAGICIAN_GRAB_SECONDS.get() * 20L;
            startGrab(server);
            if (grabPos != null) {
                return;
            }
        }
        if (now >= nextSmoke) {
            nextSmoke = now + ThiefConfig.MAGICIAN_SMOKE_SECONDS.get() * 20L;
            Player target = visiblePlayer(server, 10.0D);
            if (target != null) {
                throwSmoke(server, target);
                return;
            }
        }
        if (now >= nextCard) {
            nextCard = now + ThiefConfig.MAGICIAN_CARD_SECONDS.get() * 20L;
            Player target = visiblePlayer(server, CARD_RANGE);
            if (target != null) {
                throwCard(server, target);
            }
        }
    }

    private Player visiblePlayer(ServerLevel server, double range) {
        Player best = null;
        for (ServerPlayer p : server.players()) {
            if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(magician) <= range * range && magician.hasLineOfSight(p) && (best == null || p.distanceToSqr(magician) < best.distanceToSqr(magician))) {
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the reach ------------------------------------------------------------------

    /** Picks a chest in reach and begins. Whether it did is whether {@link #grabbing()}. */
    void startGrab(ServerLevel server) {
        if (!ForgeEventFactory.getMobGriefingEvent(server, magician) || !ThiefConfig.ENABLED.get()) {
            return;
        }
        List<BlockPos> chests = Targets.containersNear(server, magician.blockPosition(), (int) GRAB_RANGE);
        if (chests.isEmpty()) {
            return;
        }
        magician.reveal();
        grabPos = chests.get(magician.getRandom().nextInt(chests.size()));
        grabLeft = Targets.bellNear(server, grabPos) != null ? 1 : 3;
        grabTimer = 20;
        swapped = false;
        magician.getNavigation().stop();
        for (ServerPlayer p : server.players()) {
            if (p.distanceToSqr(grabPos.getX(), grabPos.getY(), grabPos.getZ()) <= 40.0D * 40.0D) {
                p.displayClientMessage(Component.translatable("message.thief.magician_reaching", grabPos.toShortString()), true);
            }
        }
    }

    void tickGrab(ServerLevel server) {
        Container container = grabPos == null ? null : Targets.containerAt(server, grabPos);
        if (container == null || !Targets.hasLoot(container) || magician.distanceToSqr(Vec3.atCenterOf(grabPos)) > (GRAB_RANGE + 4) * (GRAB_RANGE + 4)) {
            end(server);
            return;
        }
        magician.getNavigation().stop();
        Vec3 chest = Vec3.atCenterOf(grabPos);
        magician.getLookControl().setLookAt(chest.x, chest.y, chest.z);
        if (--grabTimer > 0) {
            if (grabTimer % 4 == 0) {
                spark(server, chest);
            }
            return;
        }
        grabTimer = 20;
        take(server, container);
        spark(server, chest);
        if (--grabLeft <= 0 || !Targets.hasLoot(container)) {
            end(server);
        }
    }

    private void spark(ServerLevel server, Vec3 chest) {
        Vec3 hand = magician.position().add(0.0D, 1.2D, 0.0D);
        for (int i = 0; i <= 8; i++) {
            Vec3 p = chest.lerp(hand, i / 8.0D);
            server.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    /** One stack from the chest to the magician, and perhaps a prop where it was. */
    private void take(ServerLevel server, Container container) {
        List<StealPlan.Slot> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            slots.add(new StealPlan.Slot(i, s.getCount(), Targets.isAllowed(s)));
        }
        for (StealPlan.Take take : StealPlan.plan(slots, 1, ThiefConfig.MAX_ITEMS_PER_STACK.get(), new Random(magician.getRandom().nextLong()))) {
            ItemStack taken = container.removeItem(take.index(), take.count());
            magician.addLoot(taken);
            double roll = swapRollOverride != null ? swapRollOverride : magician.getRandom().nextDouble();
            if (container.getItem(take.index()).isEmpty() && roll < ThiefConfig.MAGICIAN_SWAP_CHANCE.get()) {
                container.setItem(take.index(), new ItemStack(ModItems.MAGIC_PROP.get(), taken.getCount()));
                swapped = true;
            }
        }
        container.setChanged();
        server.playSound(null, grabPos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 0.8F, 1.6F);
    }

    private void end(ServerLevel server) {
        if (grabPos != null && magician.hasLoot()) {
            magician.recordTheft(grabPos);
            if (swapped) {
                for (ServerPlayer p : server.players()) {
                    if (p.distanceToSqr(grabPos.getX(), grabPos.getY(), grabPos.getZ()) <= 48.0D * 48.0D) {
                        p.displayClientMessage(Component.translatable("message.thief.magician_swapped", grabPos.toShortString()), false);
                    }
                }
            }
        }
        grabPos = null;
        swapped = false;
    }

    // ------------------------------------------------------------------ cards ------------------------------------------------------------------

    void throwCard(ServerLevel server, Player target) {
        magician.reveal();
        Vec3 from = magician.position().add(0.0D, 1.3D, 0.0D);
        Vec3 to = new Vec3(target.getX(), target.getY(0.6D), target.getZ());
        magician.getLookControl().setLookAt(target);
        cards.add(new Card(from, to.subtract(from).normalize().scale(CARD_SPEED)));
        server.playSound(null, magician.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 0.8F, 1.5F);
        magician.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    int cardsInFlight() {
        return cards.size();
    }

    private void tickCards(ServerLevel server) {
        Iterator<Card> it = cards.iterator();
        while (it.hasNext()) {
            Card c = it.next();
            Vec3 end = c.pos.add(c.velocity);
            boolean done = ++c.age > CARD_LIFE;
            HitResult block = server.clip(new ClipContext(c.pos, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, magician));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
                done = true;
            }
            List<Player> targets = new ArrayList<>(server.players());
            targets.addAll(extraTargets);
            for (Player p : targets) {
                if (p.isSpectator() || !p.isAlive() || p.distanceToSqr(c.pos.x, c.pos.y, c.pos.z) > 9.0D) {
                    continue;
                }
                AABB box = p.getBoundingBox().inflate(0.15D);
                if (box.contains(c.pos) || box.clip(c.pos, end).isPresent()) {
                    hit(server, p, c);
                    done = true;
                    break;
                }
            }
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), end.x, end.y, end.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            c.pos = end;
            if (done) {
                it.remove();
            }
        }
    }

    private void hit(ServerLevel server, Player p, Card c) {
        Vec3 view = p.getViewVector(1.0F);
        Vec3 toCard = c.pos.subtract(p.position());
        boolean blocked = p.isBlocking() && new Vec3(toCard.x, 0, toCard.z).normalize().dot(new Vec3(view.x, 0, view.z).normalize()) > 0.0D;
        if (blocked) {
            server.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        if (p.hurt(server.damageSources().mobAttack(magician), ThiefConfig.MAGICIAN_CARD_DAMAGE.get().floatValue())) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            server.playSound(null, p.blockPosition(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }

    // ------------------------------------------------------------------ smoke ------------------------------------------------------------------

    /** A cloud at the player's feet, and the magician is somewhere else by the time it clears. */
    void throwSmoke(ServerLevel server, Player target) {
        magician.reveal();
        AreaEffectCloud cloud = new AreaEffectCloud(server, target.getX(), target.getY(), target.getZ());
        cloud.setOwner(magician);
        cloud.setRadius(2.5F);
        cloud.setDuration(60);
        cloud.setRadiusPerTick(-0.02F);
        cloud.setParticle(ParticleTypes.LARGE_SMOKE);
        cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        server.addFreshEntity(cloud);
        server.playSound(null, magician.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.HOSTILE, 1.0F, 1.2F);
        magician.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = magician.getRandom().nextDouble() * Math.PI * 2;
            double dist = 5.0D + magician.getRandom().nextDouble() * 3.0D;
            BlockPos spot = NightVisits.groundNear(server, (int) Math.floor(magician.getX() + Math.cos(angle) * dist), (int) Math.floor(magician.getZ() + Math.sin(angle) * dist), magician.blockPosition().getY());
            if (spot != null) {
                server.sendParticles(ParticleTypes.POOF, magician.getX(), magician.getY() + 1.0D, magician.getZ(), 15, 0.3D, 0.5D, 0.3D, 0.02D);
                magician.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, magician.getYRot(), 0.0F);
                magician.getNavigation().stop();
                server.sendParticles(ParticleTypes.POOF, magician.getX(), magician.getY() + 1.0D, magician.getZ(), 15, 0.3D, 0.5D, 0.3D, 0.02D);
                break;
            }
        }
    }
}
