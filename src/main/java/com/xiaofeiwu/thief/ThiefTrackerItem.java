package com.xiaofeiwu.thief;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Points at the nearest thief that still carries loot: an arrow, how far it is, and where it was last seen. Held in either hand it
 * keeps showing it above the hotbar; used (right click) it also says it in the chat. The place is the one in the ledger, which is
 * kept up to date while the thief is in loaded chunks, so far away it can be a little old.
 */
public class ThiefTrackerItem extends Item {

    public ThiefTrackerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            report(sp, false);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp) || level.getGameTime() % 20 != 0) {
            return;
        }
        if (selected || sp.getOffhandItem() == stack) {
            report(sp, true);
        }
    }

    private static void report(ServerPlayer player, boolean actionBar) {
        ServerLevel level = player.serverLevel();
        String dim = level.dimension().location().toString();
        ThiefLedger.Record nearest = null;
        double best = Double.MAX_VALUE;
        for (ThiefLedger.Record r : ThiefLedger.get(level).newest(30)) {
            if (r.defeated() || r.recovered() || !r.dim().equals(dim)) {
                continue;
            }
            double d = player.distanceToSqr(r.last().getX() + 0.5D, player.getY(), r.last().getZ() + 0.5D);
            if (d < best) {
                best = d;
                nearest = r;
            }
        }
        Component message;
        if (nearest == null) {
            message = Component.translatable("message.thief.tracker_none");
        } else {
            double dx = nearest.last().getX() + 0.5D - player.getX(), dz = nearest.last().getZ() + 0.5D - player.getZ();
            int dist = Bearing.distance(dx, dz);
            String arrow = dist < 4 ? "●" : Bearing.ARROWS[Bearing.arrow(dx, dz, player.getYRot())];
            message = Component.translatable("message.thief.tracker", arrow, dist, nearest.last().getX() + ", " + nearest.last().getY() + ", " + nearest.last().getZ());
        }
        player.displayClientMessage(message, actionBar);
    }
}
