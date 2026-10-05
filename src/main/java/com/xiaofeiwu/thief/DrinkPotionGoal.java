package com.xiaofeiwu.thief;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Takes the potion out and drinks it, standing still for about a second and a half with it in hand, and turns into one of the creatures
 * around. Only when a player is near enough to be fooled, when there is a creature to look like, and not while it shines from a theft.
 */
final class DrinkPotionGoal extends Goal {

    private static final int DRINK_TICKS = 32;

    private final ThiefEntity thief;
    private EntityType<?> chosen;
    private int ticks;

    DrinkPotionGoal(ThiefEntity thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ThiefConfig.DISGUISE_ENABLED.get() || !thief.isMagician() || thief.potions() <= 0 || thief.isDecoy() || thief.isDisguised() || thief.tiedUp() || thief.isNegotiating() || thief.isVengeful() || thief.hasEffect(MobEffects.GLOWING)
                || !(thief.level() instanceof ServerLevel level) || level.getGameTime() < thief.nextDrink()) {
            return false;
        }
        if (StealGoal.requirePlayerNearby && level.getNearestPlayer(thief, 40.0D) == null) {
            return false;        // nobody to fool
        }
        chosen = thief.pickDisguise();
        return chosen != null;
    }

    @Override
    public void start() {
        ticks = DRINK_TICKS;
        thief.getNavigation().stop();
        thief.holdPotion();
        thief.level().playSound(null, thief.blockPosition(), SoundEvents.WITCH_DRINK, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    @Override
    public boolean canContinueToUse() {
        return ticks > 0 && !thief.tiedUp();
    }

    @Override
    public void tick() {
        ticks--;
    }

    @Override
    public void stop() {
        if (ticks <= 0 && chosen != null) {
            thief.disguiseAs(chosen);        // it drank it all
        }
        thief.showLoot();                    // the hand is as it was
        chosen = null;
    }
}
