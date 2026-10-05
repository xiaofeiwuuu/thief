package com.xiaofeiwu.thief;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What a whip does to anything it hits: the crack and the line of sparks, always. A thief it does not kill, and anything that is tied up it
 * does not kill either, and a lash shakes something loose from what is tied up.
 */
@Mod.EventBusSubscriber(modid = ThiefMod.MODID)
public final class WhipEvents {

    private WhipEvents() {
    }

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || !(player.getMainHandItem().getItem() instanceof WhipItem) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = event.getEntity();
        WhipItem.crack(level, player, target);
        boolean thief = target instanceof ThiefEntity;
        boolean bound = Captives.isBound(target);
        if (thief || bound) {
            event.setAmount(Math.min(event.getAmount(), Math.max(0.0F, target.getHealth() - 1.0F)));       // a whip does not kill them
        }
        if (target instanceof ThiefEntity t && t.tiedUp()) {
            t.shakeLoot();
            t.lashed(player);
        } else if (bound && target instanceof Mob mob) {
            Captives.shake(mob);
        }
    }
}
