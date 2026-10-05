package com.xiaofeiwu.thief.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.DrownedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EvokerRenderer;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.IllusionerRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.StrayRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.VindicatorRenderer;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.entity.WitchRenderer;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * The game's own renderers for the creatures a rope can tie, each with one change: between the game working out the pose of the model and
 * drawing it (the {@code getRenderType} call), the limbs of a tied creature are put where they are tied. Everything else (textures, armour, what
 * it holds, size) is the game's. If another mod draws one of these creatures itself, whichever registers last wins.
 */
final class PosedRenderers {

    private PosedRenderers() {
    }

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.ZOMBIE, Posed.ZombieR::new);
        event.registerEntityRenderer(EntityType.HUSK, Posed.HuskR::new);
        event.registerEntityRenderer(EntityType.DROWNED, Posed.DrownedR::new);
        event.registerEntityRenderer(EntityType.ZOMBIE_VILLAGER, Posed.ZombieVillagerR::new);
        event.registerEntityRenderer(EntityType.SKELETON, Posed.SkeletonR::new);
        event.registerEntityRenderer(EntityType.STRAY, Posed.StrayR::new);
        event.registerEntityRenderer(EntityType.WITHER_SKELETON, Posed.WitherSkeletonR::new);
        event.registerEntityRenderer(EntityType.VILLAGER, Posed.VillagerR::new);
        event.registerEntityRenderer(EntityType.WANDERING_TRADER, Posed.TraderR::new);
        event.registerEntityRenderer(EntityType.PILLAGER, Posed.PillagerR::new);
        event.registerEntityRenderer(EntityType.VINDICATOR, Posed.VindicatorR::new);
        event.registerEntityRenderer(EntityType.EVOKER, Posed.EvokerR::new);
        event.registerEntityRenderer(EntityType.ILLUSIONER, Posed.IllusionerR::new);
        event.registerEntityRenderer(EntityType.WITCH, Posed.WitchR::new);
        event.registerEntityRenderer(EntityType.PIGLIN, ctx -> new Posed.PiglinR(ctx, ModelLayers.PIGLIN, ModelLayers.PIGLIN_INNER_ARMOR, ModelLayers.PIGLIN_OUTER_ARMOR, false));
        event.registerEntityRenderer(EntityType.PIGLIN_BRUTE, ctx -> new Posed.PiglinR(ctx, ModelLayers.PIGLIN_BRUTE, ModelLayers.PIGLIN_BRUTE_INNER_ARMOR, ModelLayers.PIGLIN_BRUTE_OUTER_ARMOR, false));
        event.registerEntityRenderer(EntityType.ZOMBIFIED_PIGLIN, ctx -> new Posed.PiglinR(ctx, ModelLayers.ZOMBIFIED_PIGLIN, ModelLayers.ZOMBIFIED_PIGLIN_INNER_ARMOR, ModelLayers.ZOMBIFIED_PIGLIN_OUTER_ARMOR, true));
    }

    /** One class for each, as the game's classes differ in what they are made of; the body of each is the same. */
    static final class Posed {

        private Posed() {
        }

        static final class ZombieR extends ZombieRenderer {
            ZombieR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Zombie e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class HuskR extends HuskRenderer {
            HuskR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Zombie e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class DrownedR extends DrownedRenderer {
            DrownedR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Drowned e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class ZombieVillagerR extends ZombieVillagerRenderer {
            ZombieVillagerR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(ZombieVillager e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class SkeletonR extends SkeletonRenderer {
            SkeletonR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(AbstractSkeleton e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class StrayR extends StrayRenderer {
            StrayR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(AbstractSkeleton e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class WitherSkeletonR extends WitherSkeletonRenderer {
            WitherSkeletonR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(AbstractSkeleton e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class VillagerR extends VillagerRenderer {
            VillagerR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Villager e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class TraderR extends WanderingTraderRenderer {
            TraderR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(WanderingTrader e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class PillagerR extends PillagerRenderer {
            PillagerR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Pillager e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class VindicatorR extends VindicatorRenderer {
            VindicatorR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Vindicator e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class EvokerR extends EvokerRenderer<Evoker> {
            EvokerR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Evoker e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class IllusionerR extends IllusionerRenderer {
            IllusionerR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Illusioner e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class WitchR extends WitchRenderer {
            WitchR(EntityRendererProvider.Context c) {
                super(c);
            }

            @Override
            protected RenderType getRenderType(Witch e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }

        static final class PiglinR extends PiglinRenderer {
            PiglinR(EntityRendererProvider.Context c, net.minecraft.client.model.geom.ModelLayerLocation main, net.minecraft.client.model.geom.ModelLayerLocation inner,
                    net.minecraft.client.model.geom.ModelLayerLocation outer, boolean zombified) {
                super(c, main, inner, outer, zombified);
            }

            @Override
            protected RenderType getRenderType(Mob e, boolean body, boolean translucent, boolean glowing) {
                CaptivePose.apply(e, (EntityModel<?>) model);
                return super.getRenderType(e, body, translucent, glowing);
            }
        }
    }
}
