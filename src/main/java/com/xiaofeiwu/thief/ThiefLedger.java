package com.xiaofeiwu.thief;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The record of what was stolen: when, what, where from, and where the thief was last seen. Nothing a thief takes is lost without a
 * trace: the loot stays on the thief until it is defeated, and this tells the victims where to look. Kept with the world.
 */
public final class ThiefLedger extends SavedData {

    private static final int KEEP = 30;

    public static final class Record {
        UUID thief;
        final long time;
        final String dim;
        final BlockPos from;
        final List<String> items;      // "namespace:item*count"
        BlockPos last;
        boolean defeated;
        boolean recovered;

        Record(UUID thief, long time, String dim, BlockPos from, List<String> items, BlockPos last, boolean defeated) {
            this.thief = thief;
            this.time = time;
            this.dim = dim;
            this.from = from;
            this.items = items;
            this.last = last;
            this.defeated = defeated;
        }

        public UUID thiefId() {
            return thief;
        }

        public long time() {
            return time;
        }

        public String dim() {
            return dim;
        }

        public BlockPos from() {
            return from;
        }

        public List<String> items() {
            return items;
        }

        public BlockPos last() {
            return last;
        }

        /** The loot was taken back by searching the thief, which is still about. */
        public boolean recovered() {
            return recovered;
        }

        public boolean defeated() {
            return defeated;
        }
    }

    private final List<Record> records = new ArrayList<>();

    public static ThiefLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(ThiefLedger::load, ThiefLedger::new, "thief_ledger");
    }

    void add(UUID thief, long time, String dim, BlockPos from, List<String> items) {
        records.add(new Record(thief, time, dim, from, new ArrayList<>(items), from, false));
        while (records.size() > KEEP) {
            records.remove(0);
        }
        setDirty();
    }

    void moved(UUID thief, BlockPos pos) {
        for (int i = records.size() - 1; i >= 0; i--) {
            Record r = records.get(i);
            if (r.thief.equals(thief) && !r.defeated && !r.recovered) {
                if (!pos.equals(r.last)) {
                    r.last = pos;
                    setDirty();
                }
                return;
            }
        }
    }

    /** The loot went from one thief to another: the record follows it, so that the tracker points at who has it now. */
    void handedOver(UUID from, UUID to) {
        for (Record r : records) {
            if (r.thief.equals(from) && !r.defeated && !r.recovered) {
                r.thief = to;
                setDirty();
            }
        }
    }

    void recovered(UUID thief) {
        for (Record r : records) {
            if (r.thief.equals(thief) && !r.defeated && !r.recovered) {
                r.recovered = true;
                setDirty();
            }
        }
    }

    void defeated(UUID thief, BlockPos pos) {
        for (Record r : records) {
            if (r.thief.equals(thief) && !r.defeated) {
                r.defeated = true;
                r.last = pos;
                setDirty();
            }
        }
    }

    /** Newest first. */
    public List<Record> newest(int max) {
        List<Record> out = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0 && out.size() < max; i--) {
            out.add(records.get(i));
        }
        return out;
    }

    static ThiefLedger load(CompoundTag tag) {
        ThiefLedger ledger = new ThiefLedger();
        for (Tag t : tag.getList("records", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            List<String> items = new ArrayList<>();
            for (Tag s : c.getList("items", Tag.TAG_STRING)) {
                items.add(s.getAsString());
            }
            Record loaded = new Record(c.getUUID("thief"), c.getLong("time"), c.getString("dim"), new BlockPos(c.getInt("fx"), c.getInt("fy"), c.getInt("fz")), items,
                    new BlockPos(c.getInt("lx"), c.getInt("ly"), c.getInt("lz")), c.getBoolean("defeated"));
            loaded.recovered = c.getBoolean("recovered");
            ledger.records.add(loaded);
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Record r : records) {
            CompoundTag c = new CompoundTag();
            c.putUUID("thief", r.thief);
            c.putLong("time", r.time);
            c.putString("dim", r.dim);
            c.putInt("fx", r.from.getX());
            c.putInt("fy", r.from.getY());
            c.putInt("fz", r.from.getZ());
            c.putInt("lx", r.last.getX());
            c.putInt("ly", r.last.getY());
            c.putInt("lz", r.last.getZ());
            c.putBoolean("defeated", r.defeated);
            c.putBoolean("recovered", r.recovered);
            ListTag items = new ListTag();
            for (String s : r.items) {
                items.add(StringTag.valueOf(s));
            }
            c.put("items", items);
            list.add(c);
        }
        tag.put("records", list);
        return tag;
    }
}
