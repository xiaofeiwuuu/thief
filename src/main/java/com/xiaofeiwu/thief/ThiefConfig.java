package com.xiaofeiwu.thief;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Settings, in {@code config/thief-common.toml}. They belong to the world or the server, whoever runs the game. */
public final class ThiefConfig {

    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.BooleanValue ENABLED;
    static final ForgeConfigSpec.DoubleValue NIGHTLY_CHANCE;
    static final ForgeConfigSpec.IntValue MAX_NEARBY;
    static final ForgeConfigSpec.IntValue SEARCH_RADIUS;
    static final ForgeConfigSpec.BooleanValue STEAL_CHESTS;
    static final ForgeConfigSpec.BooleanValue STEAL_CROPS;
    static final ForgeConfigSpec.IntValue STACKS_PER_THEFT;
    static final ForgeConfigSpec.IntValue MAX_ITEMS_PER_STACK;
    static final ForgeConfigSpec.IntValue CROPS_PER_THEFT;
    static final ForgeConfigSpec.IntValue BELL_RADIUS;
    static final ForgeConfigSpec.IntValue LIFETIME_TICKS;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_ITEMS;
    static final ForgeConfigSpec.BooleanValue RESCUE_ENABLED;
    static final ForgeConfigSpec.DoubleValue RESCUE_CHANCE;
    static final ForgeConfigSpec.IntValue RESCUE_DELAY_SECONDS;
    static final ForgeConfigSpec.DoubleValue CREW_CHANCE;
    static final ForgeConfigSpec.IntValue CREW_SIZE;
    static final ForgeConfigSpec.DoubleValue CREW_SPEED;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> BINDABLE_TYPES;
    static final ForgeConfigSpec.BooleanValue GRUDGE_ENABLED;
    static final ForgeConfigSpec.DoubleValue CONFESS_CHANCE;
    static final ForgeConfigSpec.IntValue BOUNTY_EMERALDS;
    static final ForgeConfigSpec.IntValue BOUNTY_CREW_BONUS;
    static final ForgeConfigSpec.BooleanValue RANSOM_ENABLED;
    static final ForgeConfigSpec.DoubleValue RANSOM_CHANCE;
    static final ForgeConfigSpec.DoubleValue RANSOM_SHARE;
    static final ForgeConfigSpec.IntValue RANSOM_SECONDS;
    static final ForgeConfigSpec.DoubleValue MAGICIAN_CHANCE;
    static final ForgeConfigSpec.IntValue BOUNTY_MAGICIAN;
    static final ForgeConfigSpec.IntValue TOKEN_EMERALDS;
    static final ForgeConfigSpec.IntValue MAGICIAN_COPY_SECONDS;
    static final ForgeConfigSpec.IntValue MAGICIAN_ILLUSION_SECONDS;
    static final ForgeConfigSpec.IntValue MAGICIAN_ESCAPE_SECONDS;
    static final ForgeConfigSpec.DoubleValue MAGICIAN_TIE_HEALTH;
    static final ForgeConfigSpec.IntValue MAGICIAN_GRAB_SECONDS;
    static final ForgeConfigSpec.IntValue MAGICIAN_CARD_SECONDS;
    static final ForgeConfigSpec.IntValue MAGICIAN_SMOKE_SECONDS;
    static final ForgeConfigSpec.DoubleValue MAGICIAN_CARD_DAMAGE;
    static final ForgeConfigSpec.DoubleValue MAGICIAN_SWAP_CHANCE;
    static final ForgeConfigSpec.BooleanValue GIVE_GUIDE;
    static final ForgeConfigSpec.BooleanValue DISGUISE_ENABLED;
    static final ForgeConfigSpec.IntValue POTIONS;
    static final ForgeConfigSpec.IntValue DISGUISE_SECONDS;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISGUISE_TYPES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Turn thieves off altogether (false: none visit, and the ones that exist steal nothing).").define("enabled", true);
        NIGHTLY_CHANCE = b.comment("The chance, each night and for each player, that a thief comes to rob their surroundings.").defineInRange("nightlyChance", 0.35, 0.0, 1.0);
        MAX_NEARBY = b.comment("No new thief comes while this many are already within 128 blocks of the player.").defineInRange("maxNearby", 2, 1, 10);
        SEARCH_RADIUS = b.comment("How far from itself a thief looks for something to steal, in blocks.").defineInRange("searchRadius", 40, 8, 96);
        STEAL_CHESTS = b.comment("Thieves rob chests and barrels (never trapped chests, shulker boxes or other mods' storage).").define("stealChests", true);
        STEAL_CROPS = b.comment("Thieves pick ripe crops, and plant the seedling again.").define("stealCrops", true);
        STACKS_PER_THEFT = b.comment("How many stacks a thief takes from a chest.").defineInRange("stacksPerTheft", 3, 1, 27);
        MAX_ITEMS_PER_STACK = b.comment("At most this many items from one stack.").defineInRange("maxItemsPerStack", 16, 1, 64);
        CROPS_PER_THEFT = b.comment("How many ripe crops a thief picks in one visit.").defineInRange("cropsPerTheft", 8, 1, 64);
        BELL_RADIUS = b.comment("A bell within this many blocks of what is robbed rings, shows the thief up, and limits the theft to one stack (two crops).").defineInRange("bellRadius", 5, 1, 12);
        LIFETIME_TICKS = b.comment("A thief that has stolen nothing leaves after this long (20 ticks = 1 second).").defineInRange("lifetimeTicks", 12000, 1200, 72000);
        PROTECTED_ITEMS = b.comment("Items a thief never takes, by id, for example \"minecraft:diamond\".").defineListAllowEmpty("protectedItems", List.of(), o -> o instanceof String);
        RESCUE_ENABLED = b.comment("Free thieves try to cut a tied-up thief loose, and a new thief may come to do it.").define("rescueEnabled", true);
        RESCUE_CHANCE = b.comment("The chance that, some time after one is caught, another thief comes to rescue it.").defineInRange("rescueChance", 0.6, 0.0, 1.0);
        RESCUE_DELAY_SECONDS = b.comment("About how long after the capture the rescuer comes.").defineInRange("rescueDelaySeconds", 45, 5, 600);
        CREW_CHANCE = b.comment("The chance that a night visit is a crew: robbers, and a lookout who whistles when a player comes near. They pass loot to each other and rescue each other.").defineInRange("crewChance", 0.25, 0.0, 1.0);
        CREW_SIZE = b.comment("How many in a crew. With three or more, the last one is a lookout.").defineInRange("crewSize", 3, 2, 6);
        CREW_SPEED = b.comment("The speed attribute of crew members; a lone thief has 0.24. A creature walks at about 43.17 x (attribute x speed factor)^2 blocks per second, "
                + "and the thief runs at a factor 1.15 when it flees: 0.24 -> 2.5 walking, 2.9 fleeing; 0.275 -> 3.3 walking, 4.3 fleeing. "
                + "A player walks at 4.3 and sprints at 5.6, so to stay behind a sprinting player keep the attribute under 0.31.").defineInRange("crewSpeed", 0.275, 0.1, 0.5);
        BINDABLE_TYPES = b.comment("The creatures a rope can tie up (thieves always can). Players never can. Another mod's creature can be added by id; a creature that is not shaped like a person "
                + "keeps its own pose when tied, and has the ropes drawn on it as boxes round its body.").defineListAllowEmpty("bindableTypes", List.of("minecraft:zombie", "minecraft:husk",
                "minecraft:drowned", "minecraft:zombie_villager", "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton", "minecraft:villager", "minecraft:wandering_trader",
                "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:illusioner", "minecraft:witch", "minecraft:piglin", "minecraft:piglin_brute",
                "minecraft:zombified_piglin"), o -> o instanceof String);
        GRUDGE_ENABLED = b.comment("A tied-up thief gets more resentful the longer it is tied (hung or on a rack twice as fast as at a post), and with every lash. Let go at 30 or more it runs faster for a minute "
                + "(but never faster than a sprinting player), or it attacks whoever tied it, for half a minute: which of the two is chosen by chance, and the more resentful, the likelier the attack (a quarter at 30, three quarters at 100).").define("grudgeEnabled", true);
        CONFESS_CHANCE = b.comment("The chance that a lash of the whip makes a tied-up thief tell where the other thieves are (once for each thief).").defineInRange("confessChance", 0.35, 0.0, 1.0);
        BOUNTY_EMERALDS = b.comment("Emeralds paid at the bounty board for a thief brought in alive (on a rope).").defineInRange("bountyEmeralds", 3, 0, 64);
        BOUNTY_CREW_BONUS = b.comment("Emeralds more for a member of a crew.").defineInRange("bountyCrewBonus", 2, 0, 64);
        RANSOM_ENABLED = b.comment("When a crew member is caught, one of the others holds up a sign and offers to buy it back with the loot the crew carries.").define("ransomEnabled", true);
        RANSOM_CHANCE = b.comment("The chance that the crew makes such an offer.").defineInRange("ransomChance", 0.7, 0.0, 1.0);
        RANSOM_SHARE = b.comment("The share of what the rest of the crew carries that is given back when the captive is let go.").defineInRange("ransomShare", 0.7, 0.0, 1.0);
        RANSOM_SECONDS = b.comment("How long the offer stands. While it does, the others do not try to free the captive.").defineInRange("ransomSeconds", 60, 10, 600);
        MAGICIAN_CHANCE = b.comment("The chance that a crew of three or more has a magician: a boss that does not steal, but makes copies of itself to confuse a player, and swaps places with one of them.")
                .defineInRange("magicianChance", 0.4, 0.0, 1.0);
        BOUNTY_MAGICIAN = b.comment("Emeralds paid at the bounty board for a magician brought in alive (and its badge besides).").defineInRange("bountyMagician", 10, 0, 64);
        TOKEN_EMERALDS = b.comment("Emeralds paid at the bounty board for a magician's badge.").defineInRange("tokenEmeralds", 8, 0, 64);
        MAGICIAN_COPY_SECONDS = b.comment("How long one of the magician's copies lasts.").defineInRange("magicianCopySeconds", 10, 2, 120);
        MAGICIAN_ILLUSION_SECONDS = b.comment("How long between the magician's castings (it makes copies up to three, and swaps places with one), while a player is within 24 blocks.")
                .defineInRange("magicianCastSeconds", 20, 3, 300);
        MAGICIAN_ESCAPE_SECONDS = b.comment("A magician that is hung up, tied to a tree or to a rack (not one led in a player's hand) gets loose by itself: the longer it is tied, the likelier. This is about how long it takes for the chance to have reached a half; a tenth of them are loose at about a third of it.")
                .defineInRange("magicianEscapeSeconds", 90, 20, 900);
        MAGICIAN_TIE_HEALTH = b.comment("A magician can only be tied with a rope when its health is at or under this share of its full health: it has to be worn down first. 1.0 means any time.")
                .defineInRange("magicianTieHealth", 0.35, 0.0, 1.0);
        MAGICIAN_GRAB_SECONDS = b.comment("How long between the times the magician reaches for a chest from where it stands (up to three stacks, one a second, while a player is within 32 blocks). A blow stops it.")
                .defineInRange("magicianGrabSeconds", 30, 5, 600);
        MAGICIAN_SWAP_CHANCE = b.comment("The chance that a stack the magician takes is replaced by a prop (Magician's Prop), so that the chest looks as full as before.")
                .defineInRange("magicianSwapChance", 0.5, 0.0, 1.0);
        MAGICIAN_CARD_SECONDS = b.comment("How long between the cards the magician throws at a player it can see within 16 blocks.").defineInRange("magicianCardSeconds", 6, 2, 120);
        MAGICIAN_CARD_DAMAGE = b.comment("Health a thrown card takes off (2 is one heart). A shield stops it. 0 for none.").defineInRange("magicianCardDamage", 2.0, 0.0, 20.0);
        MAGICIAN_SMOKE_SECONDS = b.comment("How long between the smoke bombs the magician throws at a player within 10 blocks (blind and slow for three seconds); it changes places while the smoke is up.")
                .defineInRange("magicianSmokeSeconds", 16, 5, 300);
        GIVE_GUIDE = b.comment("Give a player the guide book the first time they come into a world with this mod.").define("giveGuide", true);
        DISGUISE_ENABLED = b.comment("The magician (only the magician: ordinary thieves do not) drinks a potion and looks like a creature near it, to fool players.").define("disguiseEnabled", true);
        POTIONS = b.comment("How many potions a thief has: one to get close, one to get away.").defineInRange("potions", 2, 0, 5);
        DISGUISE_SECONDS = b.comment("How long a potion lasts.").defineInRange("disguiseSeconds", 60, 5, 600);
        DISGUISE_TYPES = b.comment("What a thief can look like, if one is within 24 blocks of it. Only creatures that are really there are chosen. Another mod's creature can be added by id, but its picture is drawn by that mod, and one that draws badly is given up after the first error.")
                .defineListAllowEmpty("disguiseAs", List.of("minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken", "minecraft:villager",
                        "minecraft:rabbit", "minecraft:wolf", "minecraft:cat", "minecraft:horse", "minecraft:zombie", "minecraft:zombie_villager", "minecraft:husk",
                        "minecraft:skeleton", "minecraft:stray", "minecraft:pillager", "minecraft:vindicator", "minecraft:wandering_trader", "minecraft:witch",
                        "minecraft:piglin"), o -> o instanceof String);
        SPEC = b.build();
    }

    private ThiefConfig() {
    }
}
