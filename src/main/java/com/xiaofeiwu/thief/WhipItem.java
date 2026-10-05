package com.xiaofeiwu.thief;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A whip. A weak weapon against anything else, but a thief it will not kill: it takes the lash down to one heart point and no further.
 * Lashing a thief that is tied up or hung makes it spill some of what it stole (see {@link ThiefEntity#lash}).
 */
public class WhipItem extends Item {

    public WhipItem(Properties properties) {
        super(properties);
    }

    /** The crack of the whip and a line of sparks from the hand to what it hit. */
    static void crack(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player player, LivingEntity target) {
        level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.LEASH_KNOT_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 1.6F);
        net.minecraft.world.phys.Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6D)).subtract(0.0D, 0.3D, 0.0D);
        net.minecraft.world.phys.Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        for (int i = 0; i <= 8; i++) {
            net.minecraft.world.phys.Vec3 p = from.lerp(to, i / 8.0D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) {
            return super.getDefaultAttributeModifiers(slot);
        }
        return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", 1.0D, AttributeModifier.Operation.ADDITION))
                .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.0D, AttributeModifier.Operation.ADDITION))
                .build();
    }
}
