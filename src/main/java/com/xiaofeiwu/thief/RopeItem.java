package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * A rope to tie someone up with: a thief, or any of the creatures in the list in the settings (zombies, villagers and the like), never a
 * player. Used on one it ties its hands, it stops running or biting, and follows whoever holds the rope. Used then on a tree trunk, a fence,
 * a wall or a pillar it ties it there, back against it; on a rack it spreads it on the frame; on the underside of a block it hangs it by the
 * wrists. Taking the rope off (an empty hand on it) gives the rope back.
 */
public class RopeItem extends Item {

    /** What the last use on a rack decided, for finding out why a test failed. */
    static String lastDecision = "";

    public RopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        boolean tied;
        if (target instanceof ThiefEntity thief) {
            if (thief.isDecoy()) {
                if (!player.level().isClientSide) {
                    thief.vanish();        // it was a copy, and the rope is not used up
                }
                return InteractionResult.sidedSuccess(player.level().isClientSide);
            }
            if (!thief.canBeLeashed(player)) {
                return InteractionResult.PASS;
            }
            if (!thief.canBeTied()) {
                if (!player.level().isClientSide) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.thief.magician_too_strong"), true);
                    if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
                        server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, thief.getX(), thief.getY() + 1.0D, thief.getZ(), 8, 0.3D, 0.4D, 0.3D, 0.02D);
                    }
                }
                return InteractionResult.sidedSuccess(player.level().isClientSide);        // the rope is not used up
            }
            if (!player.level().isClientSide) {
                thief.tieWithRope(player);
            }
            tied = true;
        } else if (target instanceof Mob mob && Captives.bindable(mob) && !Captives.isBound(mob) && !mob.isLeashed()) {
            if (!player.level().isClientSide) {
                Captives.tieLed(mob, player);
            }
            tied = true;
        } else {
            return InteractionResult.PASS;
        }
        if (tied && !player.level().isClientSide && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** Someone the player leads on a rope, whatever it is. */
    private interface Led {
        void rack(BlockPos anchor, Direction facing);

        void post(BlockPos post, Direction side);

        void hang(BlockPos anchor);
    }

    private static List<Led> ledBy(Level level, Player player, AABB box) {
        List<Led> out = new ArrayList<>();
        for (ThiefEntity t : level.getEntitiesOfClass(ThiefEntity.class, box, t -> t.tiedUp() && !t.isRacked() && !t.isPosted() && !t.isHung() && t.getLeashHolder() == player)) {
            out.add(new Led() {
                @Override
                public void rack(BlockPos anchor, Direction facing) {
                    t.bindToRack(anchor, facing);
                }

                @Override
                public void post(BlockPos post, Direction side) {
                    t.bindToPost(post, side);
                }

                @Override
                public void hang(BlockPos anchor) {
                    t.hangFrom(anchor);
                }
            });
        }
        for (Mob m : Captives.ledBy(level, player, box)) {
            out.add(new Led() {
                @Override
                public void rack(BlockPos anchor, Direction facing) {
                    Captives.bindToRack(m, anchor, facing);
                }

                @Override
                public void post(BlockPos post, Direction side) {
                    Captives.bindToPost(m, post, side);
                }

                @Override
                public void hang(BlockPos anchor) {
                    Captives.hang(m, anchor);
                }
            });
        }
        return out;
    }

    private static InteractionResult say(Level level, Player player, String key) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(key), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockState clicked = level.getBlockState(pos);
        // a rack: tie them to it, hand and foot
        if (clicked.getBlock() instanceof RackBlock) {
            BlockPos anchor = RackBlock.anchorPos(clicked, pos);
            List<Led> held = ledBy(level, player, new AABB(anchor).inflate(7.0D));
            if (held.isEmpty()) {
                return InteractionResult.PASS;
            }
            List<ThiefEntity> onIt = level.getEntitiesOfClass(ThiefEntity.class, new AABB(anchor).inflate(2.0D), t -> anchor.equals(t.rackPos()));
            boolean taken = !onIt.isEmpty() || Captives.takenRack(level, anchor);
            lastDecision = "rack at " + anchor.toShortString() + ": " + held.size() + " led, taken " + taken + " (" + onIt.size() + " thieves " + onIt.stream().map(t -> t.getUUID() + "@" + t.position()).toList() + ")";
            if (taken) {
                return say(level, player, "message.thief.rack_taken");
            }
            if (!level.isClientSide) {
                held.get(0).rack(anchor, level.getBlockState(anchor).getValue(RackBlock.FACING));
                level.playSound(null, anchor, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(GameEvent.BLOCK_ATTACH, anchor, GameEvent.Context.of(player));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // the underside of a block: hang them there
        if (context.getClickedFace() == Direction.DOWN && clicked.isFaceSturdy(level, pos, Direction.DOWN)) {
            List<Led> held = ledBy(level, player, new AABB(pos).inflate(7.0D));
            if (!held.isEmpty()) {
                if (ThiefEntity.hangGap(level, pos) < 0) {
                    return say(level, player, "message.thief.no_room");
                }
                if (Captives.takenHang(level, pos)) {
                    return say(level, player, "message.thief.hang_taken");
                }
                if (!level.isClientSide) {
                    held.get(0).hang(pos);
                    level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (!RopeKnotEntity.canTieTo(clicked)) {
            return InteractionResult.PASS;
        }
        List<Led> held = ledBy(level, player, new AABB(pos).inflate(7.0D));
        if (held.isEmpty()) {
            return InteractionResult.PASS;
        }
        // tied all round to a tree trunk, a fence post, a wall or a pillar, back against it, on the side that was clicked
        // (the top or bottom of it: on the side facing the player)
        Direction side = context.getClickedFace().getAxis().isHorizontal() ? context.getClickedFace()
                : Direction.getNearest(player.getX() - (pos.getX() + 0.5D), 0.0D, player.getZ() - (pos.getZ() + 0.5D));
        if (!ThiefEntity.hasRoomAtPost(level, pos, side)) {
            return say(level, player, "message.thief.post_no_room");
        }
        List<ThiefEntity> onIt = level.getEntitiesOfClass(ThiefEntity.class, new AABB(pos.relative(side)).inflate(1.5D), t -> pos.equals(t.postPos()) && side == t.postDir());
        boolean taken = !onIt.isEmpty() || Captives.takenPost(level, pos, side);
        lastDecision = "post at " + pos.toShortString() + " side " + side + ": " + held.size() + " led, taken " + taken + " (" + onIt.size() + " thieves " + onIt.stream().map(t -> t.getUUID() + "@" + t.position()).toList() + ")";
        if (taken) {
            return say(level, player, "message.thief.post_taken");
        }
        if (!level.isClientSide) {
            held.get(0).post(pos, side);
            level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
