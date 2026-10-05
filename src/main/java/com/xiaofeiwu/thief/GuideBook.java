package com.xiaofeiwu.thief;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The guide to the mod (the screen is {@code client/GuideScreen}): who gets one, and when. */
public final class GuideBook {

    private static final String GIVEN = "thief_guide_given";

    private GuideBook() {
    }

    /** Gives the guide to a player who has never had it from here: once for each player, kept through death. @return whether it was given */
    public static boolean giveOnce(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return false;
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ThiefEntity.give(player, new ItemStack(ModItems.THIEF_GUIDE.get()));
        return true;
    }
}
