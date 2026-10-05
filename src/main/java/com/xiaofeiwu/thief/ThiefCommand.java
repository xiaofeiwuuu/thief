package com.xiaofeiwu.thief;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * {@code /thief ledger}: what was stolen lately and where the thieves went (everyone).
 * {@code /thief visit} and {@code /thief spawn}: for trying it out, operators only.
 */
@Mod.EventBusSubscriber(modid = ThiefMod.MODID)
public final class ThiefCommand {

    private ThiefCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("thief")
                .then(Commands.literal("ledger").executes(c -> ledger(c.getSource())))
                .then(Commands.literal("visit").requires(s -> s.hasPermission(2)).executes(c -> visit(c.getSource())))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2)).executes(c -> debug(c.getSource())))
                .then(Commands.literal("speed").requires(s -> s.hasPermission(2)).executes(c -> speed(c.getSource())))
                .then(Commands.literal("magician").requires(s -> s.hasPermission(2)).executes(c -> magician(c.getSource())))
                .then(Commands.literal("crew").requires(s -> s.hasPermission(2)).executes(c -> crew(c.getSource())))
                .then(Commands.literal("spawn").requires(s -> s.hasPermission(2)).executes(c -> spawn(c.getSource()))));
    }

    private static int ledger(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<ThiefLedger.Record> records = ThiefLedger.get(level).newest(8);
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.thief.ledger_empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.thief.ledger_title"), false);
        long now = level.getServer().overworld().getGameTime();
        for (ThiefLedger.Record r : records) {
            MutableComponent what = Component.empty();
            for (int i = 0; i < r.items().size(); i++) {
                String entry = r.items().get(i);
                int star = entry.lastIndexOf('*');
                var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(star < 0 ? entry : entry.substring(0, star)));
                if (i > 0) {
                    what.append(Component.literal("、"));
                }
                what.append(item == null ? Component.literal(entry) : new ItemStack(item).getHoverName()).append(" ×" + (star < 0 ? "?" : entry.substring(star + 1)));
            }
            long minutes = Math.max(0, (now - r.time()) / 1200L);
            BlockPos2 from = BlockPos2.of(r.from()), last = BlockPos2.of(r.last());
            Component line = r.recovered()
                    ? Component.translatable("command.thief.ledger_recovered", minutes, what, from.text())
                    : r.defeated()
                    ? Component.translatable("command.thief.ledger_defeated", minutes, what, from.text(), last.text())
                    : Component.translatable("command.thief.ledger_loose", minutes, what, from.text(), last.text());
            source.sendSuccess(() -> line, false);
        }
        return records.size();
    }

    /** Says why the nearest thief does what it does: what it can see to steal, whether it can walk there, and what it is doing now. */
    private static int debug(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        net.minecraft.world.phys.Vec3 at = source.getPosition();
        ThiefEntity thief = level.getEntitiesOfClass(ThiefEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(64.0D)).stream()
                .min(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(at))).orElse(null);
        if (thief == null) {
            source.sendSuccess(() -> Component.literal("64 格内没有小偷。"), false);
            return 0;
        }
        List<String> out = new java.util.ArrayList<>();
        out.add("小偷在 " + thief.blockPosition().toShortString() + "，离你 " + Math.round(Math.sqrt(thief.distanceToSqr(at))) + " 格");
        out.add("带着赃物：" + thief.hasLoot() + "；已经偷过：" + thief.hasStolen() + "；正在做：" + (thief.runningGoals().isEmpty() ? "没有行为在运行" : thief.runningGoals()));
        out.add("魔盗团：" + (thief.crewId() == null ? "无" : (thief.isLookout() ? "望风" : "行窃") + "，同伙 " + thief.crewMates(64.0D).size() + " 个") + "；戒备 " + thief.alertTicks() / 20 + " 秒");
        if (thief.crewId() != null) {
            int carriers = (int) thief.crewMates(64.0D).stream().filter(m -> !m.tiedUp() && m.hasLoot()).count();
            int free = (int) thief.crewMates(64.0D).stream().filter(m -> !m.tiedUp()).count();
            out.add("赎金：" + (thief.ransomActive() ? "出价中" : thief.tiedUp() ? thief.ransomState() : "（没被绑住）") + "；自由的同伙 " + free + " 个，其中带着赃物的 " + carriers + " 个");
        }
        out.add("怨恨：" + Math.round(thief.grudge()) + "/100" + (thief.isVengeful() ? "，正在报复" : thief.isHurried() ? "，跑得更快" : "") + (thief.isNegotiating() ? "，举着牌子谈判" : ""));
        out.add("解救：" + (thief.rescueLog.isEmpty() ? "还没找过" : thief.rescueLog));
        out.add("魔术师：" + (thief.isMagician() ? "是，分身 " + thief.decoys().size() + " 个" : thief.isDecoy() ? "这是个分身" : "不是"));
        out.add("被绑住：" + thief.tiedUp() + "；吊着：" + thief.isHung());
        out.add("伪装：" + (thief.isDisguised() ? thief.disguiseType().getDescriptionId() + "，还剩 " + thief.disguiseTicks() / 20 + " 秒" : "无") + "；药水剩 " + thief.potions() + " 瓶");
        out.add("模组开启：" + ThiefConfig.ENABLED.get() + "；mobGriefing：" + net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(level, thief)
                + "；128 格内有玩家：" + (level.getNearestPlayer(thief, 128.0D) != null));
        int radius = ThiefConfig.SEARCH_RADIUS.get();
        List<net.minecraft.core.BlockPos> chests = Targets.containersNear(level, thief.blockPosition(), radius);
        int[] crops = Targets.cropCounts(level, thief.blockPosition(), Math.min(radius, 16));
        out.add("附近 " + radius + " 格内能偷的箱子：" + chests.size() + " 个；16 格内庄稼：" + crops[1] + " 棵，其中成熟的 " + crops[0] + " 棵（只偷成熟的）");
        for (int i = 0; i < Math.min(3, chests.size()); i++) {
            out.add("  箱子 " + chests.get(i).toShortString() + "：" + describe(Targets.reach(thief, chests.get(i))));
        }
        List<net.minecraft.core.BlockPos> ripe = Targets.ripeCropsNear(level, thief.blockPosition(), Math.min(radius, 16), 3);
        for (net.minecraft.core.BlockPos p : ripe) {
            out.add("  成熟庄稼 " + p.toShortString() + "：" + describe(Targets.reach(thief, p, false)));
        }
        for (String line : out) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static String describe(Targets.Reach r) {
        return switch (r) {
            case REACHES -> "走得到";
            case STOPS_SHORT -> "走不到（路在半途被挡住，常见原因：关着的铁门、栅栏门、活板门、封死的房间）";
            case NO_PATH -> "完全没有路";
        };
    }

    /** How fast the nearest thief goes, next to how fast a player does. */
    private static int speed(CommandSourceStack source) {
        net.minecraft.world.phys.Vec3 at = source.getPosition();
        ThiefEntity thief = source.getLevel().getEntitiesOfClass(ThiefEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(64.0D)).stream()
                .min(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(at))).orElse(null);
        if (thief == null) {
            source.sendSuccess(() -> Component.literal("64 格内没有小偷。"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format("最近的小偷（%s）：速度属性 %.3f。走路约 %.1f 格/秒，逃跑约 %.1f 格/秒，被打慌了约 %.1f 格/秒。",
                thief.crewId() == null ? "单独行动" : thief.isLookout() ? "魔盗团·望风" : "魔盗团·行窃", thief.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),
                thief.estimatedSpeed(1.0D), thief.estimatedSpeed(1.15D), thief.estimatedSpeed(1.3D))), false);
        source.sendSuccess(() -> Component.literal("对照：玩家走路 4.3 格/秒，疾跑 5.6 格/秒。"), false);
        return 1;
    }

    private static int magician(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ThiefEntity m = NightVisits.spawnNear(level, net.minecraft.core.BlockPos.containing(source.getPosition()), 12, 18, source.getPlayer(), new java.util.Random());
        if (m == null) {
            source.sendSuccess(() -> Component.literal("没找到能放下魔术师的地方。"), false);
            return 0;
        }
        m.becomeMagician();
        source.sendSuccess(() -> Component.literal("魔术师出现在 " + m.blockPosition().toShortString() + " 附近。"), false);
        return 1;
    }

    private static int crew(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ThiefEntity first = NightVisits.spawnNear(level, net.minecraft.core.BlockPos.containing(source.getPosition()), 14, 20, source.getPlayer(), new java.util.Random());
        if (first == null) {
            source.sendSuccess(() -> Component.literal("没找到能放下团伙的地方。"), false);
            return 0;
        }
        int n = NightVisits.formCrew(level, first, ThiefConfig.CREW_SIZE.get(), new java.util.Random());
        source.sendSuccess(() -> Component.literal("一个 " + n + " 人的魔盗团出现在 " + first.blockPosition().toShortString() + " 附近。"), false);
        return n;
    }

    private static int visit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        boolean ok = NightVisits.visit(player.serverLevel(), player);
        source.sendSuccess(() -> Component.translatable(ok ? "command.thief.visit_ok" : "command.thief.visit_none"), false);
        return ok ? 1 : 0;
    }

    private static int spawn(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ThiefEntity thief = ModEntities.THIEF.get().create(level);
        if (thief == null) {
            return 0;
        }
        thief.moveTo(source.getPosition());
        thief.finalizeSpawn(level, level.getCurrentDifficultyAt(thief.blockPosition()), MobSpawnType.COMMAND, null, null);
        level.addFreshEntity(thief);
        return 1;
    }

    /** A position as it is shown: three numbers. */
    private record BlockPos2(int x, int y, int z) {
        static BlockPos2 of(net.minecraft.core.BlockPos p) {
            return new BlockPos2(p.getX(), p.getY(), p.getZ());
        }

        Component text() {
            return Component.literal(x + ", " + y + ", " + z);
        }
    }
}
