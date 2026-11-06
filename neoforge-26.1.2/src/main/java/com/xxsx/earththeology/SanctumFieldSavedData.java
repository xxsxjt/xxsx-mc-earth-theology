package com.xxsx.earththeology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xxsx.earthlore.api.LoreApi;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * T-FIELD-01 earth_theology:sanctum_field per-dimension SavedData.
 * Tracks active shrine_altars and per-chunk desecration, plus T-TEMPLE-01 temple formation checks.
 */
public final class SanctumFieldSavedData extends SavedData {
    public static final class AltarRecord {
        public static final Codec<AltarRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(AltarRecord::posLong),
                Codec.INT.optionalFieldOf("value", 0).forGetter(AltarRecord::value),
                Codec.BOOL.optionalFieldOf("temple", false).forGetter(AltarRecord::temple),
                Codec.LONG.optionalFieldOf("last_scan", 0L).forGetter(AltarRecord::lastScan),
                Codec.LONG.optionalFieldOf("blessed_until", 0L).forGetter(AltarRecord::blessedUntil),
                UUIDUtil.CODEC.optionalFieldOf("owner_uuid").forGetter(r -> Optional.ofNullable(r.ownerUuid))
        ).apply(instance, (pos, val, tmpl, scan, blessed, owner) ->
                new AltarRecord(pos, val, tmpl, scan, blessed, owner.orElse(null))));

        private final long posLong;
        private int value;
        private boolean temple;
        private long lastScan;
        private long blessedUntil;
        private UUID ownerUuid;

        public AltarRecord(long posLong, int value, boolean temple, long lastScan, long blessedUntil, UUID ownerUuid) {
            this.posLong = posLong;
            this.value = Math.max(0, Math.min(TheologyMath.SANCTUM_MAX, value));
            this.temple = temple;
            this.lastScan = lastScan;
            this.blessedUntil = blessedUntil;
            this.ownerUuid = ownerUuid;
        }

        public long posLong() {
            return posLong;
        }

        public BlockPos pos() {
            return BlockPos.of(posLong);
        }

        public int value() {
            return value;
        }

        public void setValue(int value) {
            this.value = Math.max(0, Math.min(TheologyMath.SANCTUM_MAX, value));
        }

        public boolean temple() {
            return temple;
        }

        public void setTemple(boolean temple) {
            this.temple = temple;
        }

        public long lastScan() {
            return lastScan;
        }

        public void setLastScan(long lastScan) {
            this.lastScan = lastScan;
        }

        public long blessedUntil() {
            return blessedUntil;
        }

        public void setBlessedUntil(long blessedUntil) {
            this.blessedUntil = blessedUntil;
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public void setOwnerUuid(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
        }
    }

    public static final class DesecrationState {
        public static final Codec<DesecrationState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("chunk").forGetter(DesecrationState::chunkLong),
                Codec.DOUBLE.optionalFieldOf("value", 0.0D).forGetter(DesecrationState::value),
                Codec.LONG.optionalFieldOf("last_update", 0L).forGetter(DesecrationState::lastUpdate)
        ).apply(instance, DesecrationState::new));

        private final long chunkLong;
        private double value;
        private long lastUpdate;

        public DesecrationState(long chunkLong, double value, long lastUpdate) {
            this.chunkLong = chunkLong;
            this.value = TheologyMath.clampDesecration(value);
            this.lastUpdate = lastUpdate;
        }

        public long chunkLong() {
            return chunkLong;
        }

        public double value() {
            return value;
        }

        public long lastUpdate() {
            return lastUpdate;
        }

        public double current(long nowGameTime) {
            if (nowGameTime > lastUpdate) {
                value = TheologyMath.recoverDesecration(value, nowGameTime - lastUpdate);
                lastUpdate = nowGameTime;
            }
            return value;
        }

        public void add(double delta, long nowGameTime) {
            current(nowGameTime);
            value = TheologyMath.clampDesecration(value + delta);
            lastUpdate = nowGameTime;
        }
    }

    public static final Codec<SanctumFieldSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AltarRecord.CODEC.listOf().optionalFieldOf("altars", List.of()).forGetter(SanctumFieldSavedData::altarList),
            DesecrationState.CODEC.listOf().optionalFieldOf("desecration", List.of()).forGetter(SanctumFieldSavedData::desecrationList)
    ).apply(instance, SanctumFieldSavedData::new));

    public static final SavedDataType<SanctumFieldSavedData> TYPE = new SavedDataType<>(
            EarthTheology.id("sanctum_field"), SanctumFieldSavedData::new, CODEC);

    private final Map<Long, AltarRecord> altars = new LinkedHashMap<>();
    private final Map<Long, DesecrationState> desecration = new LinkedHashMap<>();

    public SanctumFieldSavedData() {
    }

    private SanctumFieldSavedData(List<AltarRecord> altarRecords, List<DesecrationState> desecrationRecords) {
        for (AltarRecord record : altarRecords) {
            altars.put(record.posLong(), record);
        }
        for (DesecrationState state : desecrationRecords) {
            if (state.value() > 0.0D) {
                desecration.put(state.chunkLong(), state);
            }
        }
    }

    public static SanctumFieldSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<AltarRecord> altarList() {
        return new ArrayList<>(altars.values());
    }

    private List<DesecrationState> desecrationList() {
        return new ArrayList<>(desecration.values());
    }

    public Optional<AltarRecord> getAltar(BlockPos pos) {
        return Optional.ofNullable(altars.get(pos.asLong()));
    }

    public Map<Long, AltarRecord> allAltars() {
        return altars;
    }

    /** Returns the nearest registered altar within maxReach (Euclidean or Chebyshev <= maxReach). */
    public Optional<AltarRecord> nearestAltar(BlockPos origin, int maxReach) {
        double bestDistSq = (double) maxReach * (double) maxReach;
        AltarRecord best = null;
        for (AltarRecord record : altars.values()) {
            BlockPos p = record.pos();
            double distSq = p.distSqr(origin);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = record;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Rule 8: second altar within 8 blocks of an existing altar does not create an AltarRecord. */
    public boolean tryRegisterAltar(ServerLevel level, BlockPos pos, UUID ownerUuid) {
        if (altars.containsKey(pos.asLong())) {
            return true;
        }
        if (nearestAltar(pos, TheologyMath.ALTAR_REACH).isPresent()) {
            return false;
        }
        AltarRecord record = new AltarRecord(pos.asLong(), 15, false, level.getGameTime(), 0L, ownerUuid);
        altars.put(pos.asLong(), record);
        rescanAltar(level, record);
        setDirty();
        return true;
    }

    public void removeAltar(BlockPos pos) {
        if (altars.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public double getChunkDesecration(ServerLevel level, BlockPos pos) {
        long key = ChunkPos.pack(pos);
        DesecrationState state = desecration.get(key);
        if (state == null) {
            return 0.0D;
        }
        double cur = state.current(level.getGameTime());
        if (cur <= 0.0D) {
            desecration.remove(key);
            setDirty();
            return 0.0D;
        }
        return cur;
    }

    public void addDesecrationNearAltar(ServerLevel level, BlockPos eventPos, double delta) {
        Optional<AltarRecord> near = nearestAltar(eventPos, 16);
        if (near.isEmpty()) {
            return;
        }
        BlockPos altarPos = near.get().pos();
        long key = ChunkPos.pack(altarPos);
        long now = level.getGameTime();
        DesecrationState state = desecration.computeIfAbsent(key, k -> new DesecrationState(k, 0.0D, now));
        state.add(delta, now);
        rescanAltar(level, near.get());
        setDirty();
    }

    public void clearDesecrationAtAltar(ServerLevel level, BlockPos altarPos) {
        long key = ChunkPos.pack(altarPos);
        if (desecration.remove(key) != null) {
            setDirty();
        }
        getAltar(altarPos).ifPresent(r -> rescanAltar(level, r));
    }

    public int getSanctumAt(ServerLevel level, BlockPos pos) {
        Optional<AltarRecord> near = nearestAltar(pos, TheologyMath.ALTAR_REACH);
        if (near.isEmpty()) {
            return 0;
        }
        AltarRecord record = near.get();
        if (level.getGameTime() - record.lastScan() >= TheologyMath.SANCTUM_REFRESH_TICKS) {
            rescanAltar(level, record);
        }
        return record.value();
    }

    public void rescanNearbyAltars(ServerLevel level, BlockPos changedPos) {
        for (AltarRecord record : altars.values()) {
            BlockPos ap = record.pos();
            if (Math.abs(ap.getX() - changedPos.getX()) <= TheologyMath.SANCTUM_SCAN_XZ
                    && Math.abs(ap.getY() - changedPos.getY()) <= TheologyMath.SANCTUM_SCAN_Y
                    && Math.abs(ap.getZ() - changedPos.getZ()) <= TheologyMath.SANCTUM_SCAN_XZ) {
                rescanAltar(level, record);
            }
        }
    }

    public record ScanBreakdown(
            int litCandles,
            int litCensers,
            int scrollLecterns,
            int pews,
            int bells,
            int filledReliquaries,
            int activeWardStones,
            boolean blessed,
            boolean templeFormed,
            boolean templeBonusApplies,
            double desecration,
            int totalWithoutWard,
            int total
    ) {}

    public ScanBreakdown rescanAltar(ServerLevel level, AltarRecord record) {
        BlockPos center = record.pos();
        if (!level.isLoaded(center)) {
            return new ScanBreakdown(0, 0, 0, 0, 0, 0, 0, false, record.temple(), false, 0.0D, record.value(), record.value());
        }
        int litCandles = 0;
        int litCensers = 0;
        int scrollLecterns = 0;
        int pews = 0;
        int bells = 0;
        int filledReliquaries = 0;
        BlockPos nearestWardPos = null;
        double nearestWardDist = Double.MAX_VALUE;

        for (int dx = -TheologyMath.SANCTUM_SCAN_XZ; dx <= TheologyMath.SANCTUM_SCAN_XZ; dx++) {
            for (int dy = -TheologyMath.SANCTUM_SCAN_Y; dy <= TheologyMath.SANCTUM_SCAN_Y; dy++) {
                for (int dz = -TheologyMath.SANCTUM_SCAN_XZ; dz <= TheologyMath.SANCTUM_SCAN_XZ; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(p);
                    if (state.is(EarthTheology.PRAYER_CANDLE.get()) && state.getValue(TheologyBlocks.PrayerCandleBlock.LIT)) {
                        litCandles++;
                    } else if (state.is(EarthTheology.CENSER.get()) && state.getValue(TheologyBlocks.CenserBlock.LIT)) {
                        litCensers++;
                    } else if (state.is(EarthTheology.SCRIPTURE_LECTERN.get()) && state.getValue(TheologyBlocks.ScriptureLecternBlock.HAS_SCROLL)) {
                        scrollLecterns++;
                    } else if (state.is(EarthTheology.PEW.get())) {
                        pews++;
                    } else if (state.is(Blocks.BELL) || state.getBlock() instanceof BellBlock) {
                        bells++;
                    } else if (state.is(EarthTheology.RELIQUARY.get()) && state.getValue(TheologyBlocks.ReliquaryBlock.OCCUPIED)) {
                        filledReliquaries++;
                    } else if (state.is(EarthTheology.WARD_STONE.get())) {
                        double d = p.distSqr(center);
                        if (d <= 64.0D && d < nearestWardDist) {
                            nearestWardDist = d;
                            nearestWardPos = p.immutable();
                        }
                    }
                }
            }
        }

        List<String> templeFailures = checkTempleFormation(level, center);
        boolean templeFormed = templeFailures.isEmpty();
        record.setTemple(templeFormed);
        boolean ownerKnowsTemple = record.ownerUuid() != null
                && LoreApi.knows(record.ownerUuid(), TheologyLoreIntegration.NODE_TEMPLE);
        boolean templeBonusApplies = templeFormed && ownerKnowsTemple;
        boolean blessed = record.blessedUntil() > level.getGameTime();
        double desec = getChunkDesecration(level, center);

        int valueWithoutWard = TheologyMath.computeSanctumValue(
                1, litCandles, litCensers, scrollLecterns, pews, bells, filledReliquaries, 0, blessed, templeBonusApplies, desec);

        int activeWard = 0;
        if (nearestWardPos != null) {
            BlockEntity be = level.getBlockEntity(nearestWardPos);
            if (be instanceof TheologyBlockEntities.WardStoneBlockEntity wardBe) {
                boolean shouldActive = wardBe.water() >= 1 && valueWithoutWard >= 40;
                wardBe.updateActiveState(level, nearestWardPos, shouldActive);
                if (shouldActive) {
                    activeWard = 1;
                }
            }
        }

        int total = TheologyMath.computeSanctumValue(
                1, litCandles, litCensers, scrollLecterns, pews, bells, filledReliquaries, activeWard, blessed, templeBonusApplies, desec);
        record.setValue(total);
        record.setLastScan(level.getGameTime());
        setDirty();
        return new ScanBreakdown(litCandles, litCensers, scrollLecterns, pews, bells, filledReliquaries,
                activeWard, blessed, templeFormed, templeBonusApplies, desec, valueWithoutWard, total);
    }

    /**
     * T-TEMPLE-01 temple formation check.
     * Returns empty list when formed, or list of failure codes in order.
     */
    public List<String> checkTempleFormation(ServerLevel level, BlockPos altarPos) {
        List<String> fails = new ArrayList<>();
        boolean hasKeystone = false;
        for (int dx = -8; dx <= 8 && !hasKeystone; dx++) {
            for (int dy = -4; dy <= 4 && !hasKeystone; dy++) {
                for (int dz = -8; dz <= 8 && !hasKeystone; dz++) {
                    BlockPos p = altarPos.offset(dx, dy, dz);
                    if (p.distSqr(altarPos) <= 64.0D && level.isLoaded(p) && level.getBlockState(p).is(EarthTheology.TEMPLE_KEYSTONE.get())) {
                        hasKeystone = true;
                    }
                }
            }
        }
        if (!hasKeystone) {
            fails.add("no_keystone");
        }

        boolean hasRoof = false;
        for (int dy = 4; dy <= 12; dy++) {
            BlockPos p = altarPos.above(dy);
            if (level.isLoaded(p)) {
                BlockState st = level.getBlockState(p);
                if (!st.isAir() && !st.getCollisionShape(level, p).isEmpty()) {
                    hasRoof = true;
                    break;
                }
            }
        }
        if (!hasRoof) {
            fails.add("no_roof");
        }

        // Flood-fill air starting at altarPos.above() within 33x17x33 (+/-16 XZ, -8..+8 Y around start)
        BlockPos start = altarPos.above();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(start);
        visited.add(start.asLong());
        boolean leaked = false;
        int bellsInRoom = 0;
        int pewsInRoom = 0;
        int candlesInRoom = 0;
        Set<Long> inspectedFixtures = new HashSet<>();

        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            int rx = cur.getX() - start.getX();
            int ry = cur.getY() - start.getY();
            int rz = cur.getZ() - start.getZ();
            if (Math.abs(rx) >= 16 || Math.abs(ry) >= 8 || Math.abs(rz) >= 16) {
                leaked = true;
                break;
            }
            if (visited.size() > 4096) {
                break;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = cur.relative(dir);
                if (!level.isLoaded(next)) {
                    leaked = true;
                    break;
                }
                BlockState st = level.getBlockState(next);
                if (inspectedFixtures.add(next.asLong())) {
                    if (st.is(Blocks.BELL) || st.getBlock() instanceof BellBlock) {
                        bellsInRoom++;
                    } else if (st.is(EarthTheology.PEW.get())) {
                        pewsInRoom++;
                    } else if (st.is(EarthTheology.PRAYER_CANDLE.get()) && st.getValue(TheologyBlocks.PrayerCandleBlock.LIT)) {
                        candlesInRoom++;
                    }
                }
                if (isEnclosureWall(level, next, st)) {
                    continue;
                }
                if (visited.add(next.asLong())) {
                    queue.add(next);
                }
            }
            if (leaked) {
                break;
            }
        }

        if (leaked) {
            fails.add("not_enclosed");
        } else {
            int volume = visited.size();
            if (volume < 150) {
                fails.add("too_small");
            } else if (volume > 4096) {
                fails.add("too_large");
            }
            if (bellsInRoom < 1) {
                fails.add("missing_bell");
            }
            if (pewsInRoom < 4) {
                fails.add("missing_pews");
            }
            if (candlesInRoom < 2) {
                fails.add("missing_candles");
            }
        }
        return fails;
    }

    private static boolean isEnclosureWall(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) {
            return true;
        }
        if (state.is(EarthTheology.PRAYER_CANDLE.get())
                || state.is(EarthTheology.PEW.get())
                || state.is(EarthTheology.SHRINE_ALTAR.get())
                || state.is(EarthTheology.SCRIPTURE_LECTERN.get())
                || state.is(EarthTheology.OFFERING_TRAY.get())
                || state.is(EarthTheology.CENSER.get())
                || state.is(EarthTheology.RELIQUARY.get())
                || state.is(EarthTheology.WARD_STONE.get())
                || state.is(Blocks.BELL)
                || state.getBlock() instanceof BellBlock) {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }
}

