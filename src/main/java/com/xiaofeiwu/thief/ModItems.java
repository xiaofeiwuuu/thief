package com.xiaofeiwu.thief;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ThiefMod.MODID);

    public static final RegistryObject<Item> THIEF_SPAWN_EGG = ITEMS.register("thief_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.THIEF, 0x2B2B33, 0xD9D9D9, new Item.Properties()));

    public static final RegistryObject<Item> THIEF_TRACKER = ITEMS.register("thief_tracker",
            () -> new ThiefTrackerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ROPE = ITEMS.register("rope", () -> new RopeItem(new Item.Properties()));

    public static final RegistryObject<Item> WHIP = ITEMS.register("whip", () -> new WhipItem(new Item.Properties().durability(200)));

    public static final RegistryObject<Item> RACK = ITEMS.register("rack", () -> new net.minecraft.world.item.BlockItem(ModBlocks.RACK.get(), new Item.Properties()));

    public static final RegistryObject<Item> BOUNTY_BOARD = ITEMS.register("bounty_board", () -> new net.minecraft.world.item.BlockItem(ModBlocks.BOUNTY_BOARD.get(), new Item.Properties()));

    public static final RegistryObject<Item> THIEF_GUIDE = ITEMS.register("thief_guide", () -> new ThiefGuideItem(new Item.Properties().stacksTo(1)));

    /** What a magician leaves, and what the bounty board pays for. */
    public static final RegistryObject<Item> MAGICIAN_TOKEN = ITEMS.register("magician_token", () -> new Item(new Item.Properties()));

    /** What the magician leaves in a chest in place of what it took. Worth nothing. */
    public static final RegistryObject<Item> MAGIC_PROP = ITEMS.register("magic_prop", () -> new Item(new Item.Properties()) {
        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level, java.util.List<net.minecraft.network.chat.Component> tip, net.minecraft.world.item.TooltipFlag flag) {
            tip.add(net.minecraft.network.chat.Component.translatable("item.thief.magic_prop.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });

    private ModItems() {
    }
}
