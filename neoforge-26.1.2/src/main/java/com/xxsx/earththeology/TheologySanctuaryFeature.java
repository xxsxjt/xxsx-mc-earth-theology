package com.xxsx.earththeology;

import com.mojang.serialization.Codec;
import com.xxsx.earthlore.api.Grade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Worldgen feature for Earth: Theology wayside shrines and monastery sanctuaries.
 *
 * <p>Strictly validates slope and rejects any fluid/water surface so structures never
 * float over water or steep cliffs, fills a solid stone-brick foundation column beneath
 * every floor block down to firm ground, and never places uncontained water sources.</p>
 */
public final class TheologySanctuaryFeature extends Feature<NoneFeatureConfiguration> {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    public static final ResourceKey<LootTable> SANCTUARY_CHEST_LOOT =
            ResourceKey.create(Registries.LOOT_TABLE, EarthTheology.id("chests/sanctuary_chest"));

    public enum Kind {
        WAYSIDE_SHRINE("wayside_shrine", 4, false),
        MONASTERY_SANCTUARY("monastery_sanctuary", 5, true);

        private final String id;
        private final int radius;
        private final boolean monastery;

        Kind(String id, int radius, boolean monastery) {
            this.id = id;
            this.radius = radius;
            this.monastery = monastery;
        }

        public String id() {
            return id;
        }

        public int radius() {
            return radius;
        }

        public boolean monastery() {
            return monastery;
        }
    }

    private final Kind kind;

    public TheologySanctuaryFeature(Codec<NoneFeatureConfiguration> codec, Kind kind) {
        super(codec);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos base = scanTerrainAndValidate(level, context.origin(), kind.radius());
        if (base == null) {
            return false;
        }
        RandomSource random = context.random();
        if (kind.monastery()) {
            buildMonasterySanctuary(level, base, random);
        } else {
            buildWaysideShrine(level, base, random);
        }
        return true;
    }

    /**
     * Scans the entire footprint for surface height and fluid presence.
     * Returns null if any fluid/water is detected or if the slope exceeds MAX_SANCTUARY_SLOPE_DELTA.
     */
    private static BlockPos scanTerrainAndValidate(WorldGenLevel level, BlockPos origin, int radius) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int sumY = 0;
        int count = 0;
        boolean hasFluid = false;

        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                BlockPos col = new BlockPos(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                while ((level.isEmptyBlock(col) || isReplaceablePlantOrFoliage(level.getBlockState(col)))
                        && col.getY() > level.getMinY() + 4) {
                    if (!level.getFluidState(col).isEmpty()) {
                        return null;
                    }
                    col = col.below();
                }
                if (!level.getFluidState(col).isEmpty()
                        || !level.getFluidState(col.above()).isEmpty()
                        || !level.getFluidState(col.below()).isEmpty()) {
                    hasFluid = true;
                    break;
                }
                BlockState ground = level.getBlockState(col);
                if (ground.isAir() || ground.is(Blocks.ICE) || ground.is(Blocks.PACKED_ICE) || ground.is(Blocks.BLUE_ICE)) {
                    return null;
                }
                int surfaceY = col.getY() + 1;
                minY = Math.min(minY, surfaceY);
                maxY = Math.max(maxY, surfaceY);
                sumY += surfaceY;
                count++;
            }
            if (hasFluid) {
                break;
            }
        }

        if (count == 0 || !TheologyMath.isValidSanctuaryTerrain(minY, maxY, level.getSeaLevel(), hasFluid)) {
            return null;
        }
        int avgY = Math.max(minY, Math.min(maxY, Math.round((float) sumY / (float) count)));
        return new BlockPos(origin.getX(), avgY, origin.getZ());
    }

    private static boolean isReplaceablePlantOrFoliage(BlockState state) {
        return state.canBeReplaced()
                || state.is(Blocks.OAK_LEAVES)
                || state.is(Blocks.BIRCH_LEAVES)
                || state.is(Blocks.SPRUCE_LEAVES)
                || state.is(Blocks.DARK_OAK_LEAVES)
                || state.is(Blocks.ACACIA_LEAVES)
                || state.is(Blocks.JUNGLE_LEAVES)
                || state.is(Blocks.CHERRY_LEAVES)
                || state.is(Blocks.MANGROVE_LEAVES)
                || state.is(Blocks.AZALEA_LEAVES)
                || state.is(Blocks.FLOWERING_AZALEA_LEAVES)
                || state.is(Blocks.SNOW);
    }

    /**
     * Places the floor block at floorPos and fills straight down until firm solid ground
     * so the sanctuary never floats on slopes.
     */
    private static void fillFoundationDown(WorldGenLevel level, BlockPos floorPos, BlockState floorState, BlockState foundationState) {
        level.setBlock(floorPos, floorState, FLAGS);
        for (int dy = 1; dy <= 14; dy++) {
            BlockPos below = floorPos.below(dy);
            if (below.getY() <= level.getMinY() + 1) {
                break;
            }
            BlockState existing = level.getBlockState(below);
            boolean needsFill = existing.isAir()
                    || !level.getFluidState(below).isEmpty()
                    || isReplaceablePlantOrFoliage(existing)
                    || existing.getCollisionShape(level, below).isEmpty();
            if (!needsFill) {
                break;
            }
            level.setBlock(below, foundationState, FLAGS);
        }
    }

    private static void clearInteriorVolume(WorldGenLevel level, BlockPos center, int radius, int height) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= height; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
    }

    private static void buildWaysideShrine(WorldGenLevel level, BlockPos center, RandomSource random) {
        int r = Kind.WAYSIDE_SHRINE.radius();
        clearInteriorVolume(level, center, r, 6);
        BlockState foundation = Blocks.COBBLESTONE.defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean border = Math.abs(dx) == r || Math.abs(dz) == r;
                BlockState floor = border
                        ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState();
                fillFoundationDown(level, center.offset(dx, -1, dz), floor, foundation);
            }
        }

        // Corner pillars & canopy roof 4 blocks above altar
        for (int px : new int[]{-3, 3}) {
            for (int pz : new int[]{-3, 3}) {
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlock(center.offset(px, dy, pz), Blocks.STONE_BRICKS.defaultBlockState(), FLAGS);
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(center.offset(dx, 4, dz), Blocks.STONE_BRICKS.defaultBlockState(), FLAGS);
            }
        }

        // Central Shrine Altar
        level.setBlock(center, EarthTheology.SHRINE_ALTAR.get().defaultBlockState(), FLAGS);
        if (level.getBlockEntity(center) instanceof TheologyBlockEntities.ShrineAltarBlockEntity altarBe) {
            altarBe.configureGeneratedSanctuary(false);
        }

        // Lit Prayer Candles
        BlockState litCandle = EarthTheology.PRAYER_CANDLE.get().defaultBlockState()
                .setValue(TheologyBlocks.PrayerCandleBlock.LIT, true);
        level.setBlock(center.offset(-1, 0, -1), litCandle, FLAGS);
        level.setBlock(center.offset(1, 0, -1), litCandle, FLAGS);
        level.setBlock(center.offset(-1, 0, 1), litCandle, FLAGS);
        level.setBlock(center.offset(1, 0, 1), litCandle, FLAGS);

        // Lit Censer with incense
        BlockPos censerPos = center.offset(-2, 0, 0);
        level.setBlock(censerPos, EarthTheology.CENSER.get().defaultBlockState()
                .setValue(TheologyBlocks.CenserBlock.LIT, true), FLAGS);
        if (level.getBlockEntity(censerPos) instanceof TheologyBlockEntities.CenserBlockEntity censerBe) {
            censerBe.setIncenseStack(new ItemStack(EarthTheology.INCENSE.get(), 4));
            censerBe.setBurnTicks(2400);
        }

        // Offering Tray with offerings
        BlockPos trayPos = center.offset(2, 0, 0);
        level.setBlock(trayPos, EarthTheology.OFFERING_TRAY.get().defaultBlockState(), FLAGS);
        if (level.getBlockEntity(trayPos) instanceof TheologyBlockEntities.OfferingTrayBlockEntity trayBe) {
            trayBe.insertStack(new ItemStack(Items.BREAD, 2));
            trayBe.insertStack(new ItemStack(Items.AMETHYST_SHARD, 1));
        }

        // Scripture Lectern with Prayer Scroll
        BlockPos lecternPos = center.offset(0, 0, -2);
        level.setBlock(lecternPos, EarthTheology.SCRIPTURE_LECTERN.get().defaultBlockState()
                .setValue(TheologyBlocks.ScriptureLecternBlock.HAS_SCROLL, true), FLAGS);
        if (level.getBlockEntity(lecternPos) instanceof TheologyBlockEntities.ScriptureLecternBlockEntity lecternBe) {
            lecternBe.setScroll(TheologyItems.ScriptureScrollItem.create(TheologyLoreIntegration.NODE_PRAYER, "Shrine Keeper"));
        }

        // Pews & Bell
        BlockState pewNorth = EarthTheology.PEW.get().defaultBlockState()
                .setValue(TheologyBlocks.PewBlock.FACING, Direction.NORTH);
        level.setBlock(center.offset(-2, 0, 2), pewNorth, FLAGS);
        level.setBlock(center.offset(2, 0, 2), pewNorth, FLAGS);
        level.setBlock(center.offset(0, 0, -3), Blocks.BELL.defaultBlockState(), FLAGS);

        // Sanctuary Supply Chest
        placeSanctuaryChest(level, center.offset(-2, 0, -2), random, false);
    }

    private static void buildMonasterySanctuary(WorldGenLevel level, BlockPos center, RandomSource random) {
        int r = Kind.MONASTERY_SANCTUARY.radius();
        clearInteriorVolume(level, center, r, 7);
        BlockState foundation = Blocks.STONE_BRICKS.defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean aisle = dx == 0 || dz == 0;
                BlockState floor = aisle
                        ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState();
                fillFoundationDown(level, center.offset(dx, -1, dz), floor, foundation);
            }
        }

        // Enclosed walls (y = 0..4) and solid roof (y = 5) so T-TEMPLE-01 enclosure (9x4x9 = 324 air blocks) is satisfied
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                level.setBlock(center.offset(dx, 5, dz), Blocks.STONE_BRICKS.defaultBlockState(), FLAGS);
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                if (!edge) {
                    continue;
                }
                for (int dy = 0; dy <= 4; dy++) {
                    if (dx == 0 && dz == r && (dy == 0 || dy == 1)) {
                        continue; // Doorway placed below
                    }
                    boolean window = dy == 2 && (Math.abs(dx) == 2 || Math.abs(dz) == 2)
                            && !(Math.abs(dx) == r && Math.abs(dz) == r);
                    BlockState wallState = window
                            ? Blocks.YELLOW_STAINED_GLASS.defaultBlockState()
                            : (dy == 4 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                    level.setBlock(center.offset(dx, dy, dz), wallState, FLAGS);
                }
            }
        }

        // Place closed oak door at (0, 0..1, r)
        BlockState doorLower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = doorLower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        level.setBlock(center.offset(0, 0, r), doorLower, FLAGS);
        level.setBlock(center.offset(0, 1, r), doorUpper, FLAGS);

        // Central Shrine Altar & Temple Keystone
        level.setBlock(center, EarthTheology.SHRINE_ALTAR.get().defaultBlockState(), FLAGS);
        if (level.getBlockEntity(center) instanceof TheologyBlockEntities.ShrineAltarBlockEntity altarBe) {
            altarBe.configureGeneratedSanctuary(true);
        }
        level.setBlock(center.offset(0, 0, -3), EarthTheology.TEMPLE_KEYSTONE.get().defaultBlockState(), FLAGS);

        // 4 Lit Prayer Candles (>= 2 required for temple)
        BlockState litCandle = EarthTheology.PRAYER_CANDLE.get().defaultBlockState()
                .setValue(TheologyBlocks.PrayerCandleBlock.LIT, true);
        level.setBlock(center.offset(-2, 0, -1), litCandle, FLAGS);
        level.setBlock(center.offset(2, 0, -1), litCandle, FLAGS);
        level.setBlock(center.offset(-2, 0, -3), litCandle, FLAGS);
        level.setBlock(center.offset(2, 0, -3), litCandle, FLAGS);

        // 6 Pews facing the altar (>= 4 required for temple)
        BlockState pewNorth = EarthTheology.PEW.get().defaultBlockState()
                .setValue(TheologyBlocks.PewBlock.FACING, Direction.NORTH);
        for (int px : new int[]{-3, -2, 2, 3}) {
            level.setBlock(center.offset(px, 0, 2), pewNorth, FLAGS);
        }
        level.setBlock(center.offset(-2, 0, 3), pewNorth, FLAGS);
        level.setBlock(center.offset(2, 0, 3), pewNorth, FLAGS);

        // 2 Bells (>= 1 required for temple)
        level.setBlock(center.offset(-3, 0, -3), Blocks.BELL.defaultBlockState(), FLAGS);
        level.setBlock(center.offset(3, 0, -3), Blocks.BELL.defaultBlockState(), FLAGS);

        // 2 Scripture Lecterns with Scrolls
        BlockState lecternState = EarthTheology.SCRIPTURE_LECTERN.get().defaultBlockState()
                .setValue(TheologyBlocks.ScriptureLecternBlock.HAS_SCROLL, true);
        BlockPos lectern1 = center.offset(-1, 0, -2);
        BlockPos lectern2 = center.offset(1, 0, -2);
        level.setBlock(lectern1, lecternState, FLAGS);
        level.setBlock(lectern2, lecternState, FLAGS);
        if (level.getBlockEntity(lectern1) instanceof TheologyBlockEntities.ScriptureLecternBlockEntity be1) {
            be1.setScroll(TheologyItems.ScriptureScrollItem.create(TheologyLoreIntegration.NODE_PRAYER, "Monastery Scribe"));
        }
        if (level.getBlockEntity(lectern2) instanceof TheologyBlockEntities.ScriptureLecternBlockEntity be2) {
            be2.setScroll(TheologyItems.ScriptureScrollItem.create(TheologyLoreIntegration.NODE_SANCTUM, "Monastery Scribe"));
        }

        // Lit Censer & Offering Tray
        BlockPos censerPos = center.offset(-1, 0, 0);
        level.setBlock(censerPos, EarthTheology.CENSER.get().defaultBlockState()
                .setValue(TheologyBlocks.CenserBlock.LIT, true), FLAGS);
        if (level.getBlockEntity(censerPos) instanceof TheologyBlockEntities.CenserBlockEntity censerBe) {
            censerBe.setIncenseStack(new ItemStack(EarthTheology.INCENSE.get(), 8));
            censerBe.setBurnTicks(2400);
        }

        BlockPos trayPos = center.offset(1, 0, 0);
        level.setBlock(trayPos, EarthTheology.OFFERING_TRAY.get().defaultBlockState(), FLAGS);
        if (level.getBlockEntity(trayPos) instanceof TheologyBlockEntities.OfferingTrayBlockEntity trayBe) {
            trayBe.insertStack(new ItemStack(Items.GOLDEN_APPLE, 1));
            trayBe.insertStack(new ItemStack(Items.EMERALD, 2));
            trayBe.insertStack(new ItemStack(Items.BREAD, 4));
        }

        // Occupied Reliquary & Ward Stone
        BlockPos reliquaryPos = center.offset(0, 0, -4);
        level.setBlock(reliquaryPos, EarthTheology.RELIQUARY.get().defaultBlockState()
                .setValue(TheologyBlocks.ReliquaryBlock.OCCUPIED, true), FLAGS);
        if (level.getBlockEntity(reliquaryPos) instanceof TheologyBlockEntities.ReliquaryBlockEntity relBe) {
            relBe.setRelic(TheologyItems.RelicItem.create("Saint Pilgrim", 4));
        }

        BlockPos wardPos = center.offset(0, 0, -2);
        level.setBlock(wardPos, EarthTheology.WARD_STONE.get().defaultBlockState()
                .setValue(TheologyBlocks.WardStoneBlock.ACTIVE, true), FLAGS);
        if (level.getBlockEntity(wardPos) instanceof TheologyBlockEntities.WardStoneBlockEntity wardBe) {
            wardBe.setWater(8);
        }

        // Sanctuary Chests
        placeSanctuaryChest(level, center.offset(-4, 0, -4), random, true);
    }

    private static void placeSanctuaryChest(WorldGenLevel level, BlockPos pos, RandomSource random, boolean monastery) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), FLAGS);
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(SANCTUARY_CHEST_LOOT, random.nextLong());
        }
        if (monastery) {
            BlockPos barrelPos = pos.offset(8, 0, 0);
            level.setBlock(barrelPos, Blocks.BARREL.defaultBlockState(), FLAGS);
            if (level.getBlockEntity(barrelPos) instanceof RandomizableContainerBlockEntity barrel) {
                barrel.setItem(0, new ItemStack(EarthTheology.INCENSE.get(), 6));
                barrel.setItem(1, TheologyItems.HolyWaterItem.create(Grade.FINE, 45));
                barrel.setItem(2, new ItemStack(EarthTheology.BLANK_SCROLL.get(), 4));
                barrel.setItem(3, new ItemStack(EarthTheology.PRAYER_CANDLE_ITEM.get(), 4));
                barrel.setItem(4, TheologyItems.ScriptureScrollItem.create(TheologyLoreIntegration.NODE_SCRIPTURES, "Abbot"));
            }
        }
    }
}
