package com.xiaofeiwu.thief;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ThiefMod.MODID);

    /** MISC: thieves never spawn by themselves in the dark, they only come with a night visit, so they do not touch the monster limits. */
    public static final RegistryObject<EntityType<ThiefEntity>> THIEF = ENTITIES.register("thief",
            () -> EntityType.Builder.of(ThiefEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(8).build("thief"));

    /** The knot a rope is tied to. */
    public static final RegistryObject<EntityType<RopeKnotEntity>> ROPE_KNOT = ENTITIES.register("rope_knot",
            () -> EntityType.Builder.<RopeKnotEntity>of(RopeKnotEntity::new, MobCategory.MISC).sized(0.375F, 0.5F).clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE).noSummon().noSave().build("rope_knot"));

    private ModEntities() {
    }
}
