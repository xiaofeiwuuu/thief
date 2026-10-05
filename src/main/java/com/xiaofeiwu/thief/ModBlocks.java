package com.xiaofeiwu.thief;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ThiefMod.MODID);

    public static final RegistryObject<Block> RACK = BLOCKS.register("rack",
            () -> new RackBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final RegistryObject<Block> BOUNTY_BOARD = BLOCKS.register("bounty_board",
            () -> new BountyBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()));

    private ModBlocks() {
    }
}
