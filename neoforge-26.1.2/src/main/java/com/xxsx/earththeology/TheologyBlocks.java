package com.xxsx.earththeology;

import com.mojang.serialization.MapCodec;
import com.xxsx.earthlore.api.Grade;
import com.xxsx.earthlore.api.LoreApi;
import com.xxsx.earthlore.api.StudyResult;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * All 9 blocks for Earth: Theology (18 册 §4–§9).
 */
public final class TheologyBlocks {
    private TheologyBlocks() {
    }

    public static Component failMsg(String code) {
        return Component.translatable("theology.earth_theology.fail." + code).withStyle(ChatFormatting.RED);
    }

    /** T-002 shrine_altar. */
    public static final class ShrineAltarBlock extends Block implements EntityBlock {
        public ShrineAltarBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TheologyBlockEntities.ShrineAltarBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide() || type != EarthTheology.SHRINE_ALTAR_BE.get()) {
                return null;
            }
            return (lvl, pos, st, be) -> TheologyBlockEntities.ShrineAltarBlockEntity.serverTick(
                    lvl, pos, st, (TheologyBlockEntities.ShrineAltarBlockEntity) be);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level instanceof ServerLevel serverLevel) {
                var owner = placer instanceof Player p ? p.getUUID() : null;
                if (level.getBlockEntity(pos) instanceof TheologyBlockEntities.ShrineAltarBlockEntity altarBe) {
                    altarBe.setOwnerUuid(owner);
                }
                boolean registered = SanctumFieldSavedData.get(serverLevel).tryRegisterAltar(serverLevel, pos, owner);
                if (!registered && placer instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(failMsg("altar_too_close"));
                }
            }
        }

        @Override
        public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
            if (level instanceof ServerLevel serverLevel) {
                SanctumFieldSavedData.get(serverLevel).removeAltar(pos);
            }
            return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            if (!(level.getBlockEntity(pos) instanceof TheologyBlockEntities.ShrineAltarBlockEntity altarBe)) {
                return InteractionResult.PASS;
            }
            SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
            Optional<SanctumFieldSavedData.AltarRecord> recordOpt = field.getAltar(pos);

            if (player.isShiftKeyDown() && stack.is(EarthTheology.INCENSE.get())) {
                return performBlessing(serverLevel, pos, serverPlayer, stack, altarBe, field, recordOpt);
            }
            if (isWaterBottle(stack)) {
                return makeHolyWater(serverLevel, pos, serverPlayer, hand, stack, altarBe, field, recordOpt);
            }
            if (stack.is(EarthTheology.HOLY_WATER.get())) {
                return performExorcism(serverLevel, pos, serverPlayer, stack, altarBe, field, recordOpt);
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
                Optional<SanctumFieldSavedData.AltarRecord> recordOpt = field.getAltar(pos);
                if (recordOpt.isEmpty()) {
                    serverPlayer.sendOverlayMessage(failMsg("altar_too_close"));
                    return InteractionResult.CONSUME;
                }
                SanctumFieldSavedData.ScanBreakdown scan = field.rescanAltar(serverLevel, recordOpt.get());
                serverPlayer.sendSystemMessage(Component.translatable(
                        "theology.earth_theology.altar.status",
                        scan.total(),
                        scan.litCandles() * 2,
                        scan.litCensers() * 5,
                        scan.scrollLecterns() * 4,
                        scan.pews(),
                        scan.bells() * 3,
                        scan.filledReliquaries() * 6,
                        scan.activeWardStones() * 4,
                        scan.blessed() ? 5 : 0,
                        scan.templeBonusApplies() ? TheologyMath.TEMPLE_BONUS : 0,
                        String.format(java.util.Locale.ROOT, "%.1f", scan.desecration()),
                        Component.translatable(scan.templeFormed() ? "theology.earth_theology.altar.temple_yes" : "theology.earth_theology.altar.temple_no")
                ).withStyle(ChatFormatting.GOLD));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.SUCCESS;
        }

        private static boolean isWaterBottle(ItemStack stack) {
            if (!stack.is(Items.POTION)) {
                return false;
            }
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            return contents != null && contents.is(Potions.WATER);
        }

        private static InteractionResult makeHolyWater(
                ServerLevel level,
                BlockPos pos,
                ServerPlayer player,
                InteractionHand hand,
                ItemStack bottleStack,
                TheologyBlockEntities.ShrineAltarBlockEntity altarBe,
                SanctumFieldSavedData field,
                Optional<SanctumFieldSavedData.AltarRecord> recordOpt
        ) {
            if (altarBe.waterCooldown() > 0) {
                player.sendOverlayMessage(failMsg("cooldown"));
                return InteractionResult.FAIL;
            }
            int sanctum = recordOpt.map(r -> field.rescanAltar(level, r).total()).orElse(0);
            if (sanctum < 10) {
                player.sendOverlayMessage(failMsg("sanctum_low"));
                return InteractionResult.FAIL;
            }
            boolean knowsHolyWater = LoreApi.knows(player, TheologyLoreIntegration.NODE_HOLY_WATER);
            boolean knowsMastery = LoreApi.knows(player, TheologyLoreIntegration.NODE_MASTERY);
            Grade grade = TheologyMath.resolveHolyWaterGrade(sanctum, knowsHolyWater, knowsMastery);
            ItemStack holyWater = TheologyItems.HolyWaterItem.create(grade, sanctum);
            altarBe.setWaterCooldown(100);
            if (!player.getAbilities().instabuild) {
                bottleStack.shrink(1);
            }
            if (bottleStack.isEmpty()) {
                player.setItemInHand(hand, holyWater);
            } else if (!player.getInventory().add(holyWater)) {
                player.drop(holyWater, false);
            }
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.1F);
            return InteractionResult.CONSUME;
        }

        private static InteractionResult performBlessing(
                ServerLevel level,
                BlockPos pos,
                ServerPlayer player,
                ItemStack incenseStack,
                TheologyBlockEntities.ShrineAltarBlockEntity altarBe,
                SanctumFieldSavedData field,
                Optional<SanctumFieldSavedData.AltarRecord> recordOpt
        ) {
            if (!LoreApi.knows(player, TheologyLoreIntegration.NODE_BLESSINGS)) {
                player.sendOverlayMessage(failMsg("lore_locked"));
                return InteractionResult.FAIL;
            }
            if (altarBe.blessCooldown() > 0) {
                player.sendOverlayMessage(failMsg("cooldown"));
                return InteractionResult.FAIL;
            }
            int sanctum = recordOpt.map(r -> field.rescanAltar(level, r).total()).orElse(0);
            if (sanctum < 20) {
                player.sendOverlayMessage(failMsg("sanctum_low"));
                return InteractionResult.FAIL;
            }
            if (incenseStack.isEmpty() || !incenseStack.is(EarthTheology.INCENSE.get())) {
                player.sendOverlayMessage(failMsg("no_incense"));
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) {
                incenseStack.shrink(1);
            }
            altarBe.setBlessCooldown(1200);
            int piety = player.getData(EarthTheology.PIETY.get()).value();
            boolean feast = TheologyMath.isFeastDay(level.getDefaultClockTime());
            boolean vestment = player.getItemBySlot(EquipmentSlot.CHEST).is(EarthTheology.VESTMENT.get());
            int duration = TheologyMath.computeBlessDurationTicks(piety, feast, vestment);
            AABB box = new AABB(pos).inflate(TheologyMath.ALTAR_REACH);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e instanceof Player || e instanceof Villager)) {
                if (target.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D) {
                    target.addEffect(new MobEffectInstance(EarthTheology.BLESSED, duration, 0, false, true, true));
                }
            }
            level.playSound(null, pos, EarthTheology.SOUND_ALTAR_BLESS.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            return InteractionResult.CONSUME;
        }

        private static InteractionResult performExorcism(
                ServerLevel level,
                BlockPos pos,
                ServerPlayer player,
                ItemStack holyWaterStack,
                TheologyBlockEntities.ShrineAltarBlockEntity altarBe,
                SanctumFieldSavedData field,
                Optional<SanctumFieldSavedData.AltarRecord> recordOpt
        ) {
            if (!LoreApi.knows(player, TheologyLoreIntegration.NODE_EXORCISM)) {
                player.sendOverlayMessage(failMsg("lore_locked"));
                return InteractionResult.FAIL;
            }
            if (altarBe.exorcismCooldown() > 0) {
                player.sendOverlayMessage(failMsg("cooldown"));
                return InteractionResult.FAIL;
            }
            boolean hasLitCenser = false;
            for (int dx = -8; dx <= 8 && !hasLitCenser; dx++) {
                for (int dy = -4; dy <= 4 && !hasLitCenser; dy++) {
                    for (int dz = -8; dz <= 8 && !hasLitCenser; dz++) {
                        BlockPos p = pos.offset(dx, dy, dz);
                        if (p.distSqr(pos) <= 64.0D && level.isLoaded(p)) {
                            BlockState st = level.getBlockState(p);
                            if (st.is(EarthTheology.CENSER.get()) && st.getValue(CenserBlock.LIT)) {
                                hasLitCenser = true;
                            }
                        }
                    }
                }
            }
            if (!hasLitCenser) {
                player.sendOverlayMessage(failMsg("no_censer"));
                return InteractionResult.FAIL;
            }
            SanctityComponent sanctity = holyWaterStack.getOrDefault(EarthTheology.SANCTITY.get(), SanctityComponent.DEFAULT);
            float damage = TheologyMath.computeExorcismDamage(sanctity.grade());
            if (!player.getAbilities().instabuild) {
                holyWaterStack.shrink(1);
            }
            altarBe.setExorcismCooldown(6000);
            field.clearDesecrationAtAltar(level, pos);
            AABB box = new AABB(pos).inflate(16.0D);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (entity.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 256.0D) {
                    continue;
                }
                if (TheologyTags.isUndead(entity)) {
                    entity.hurtServer(level, level.damageSources().magic(), damage);
                    entity.igniteForTicks(100);
                } else if (entity instanceof Player targetPlayer) {
                    targetPlayer.removeEffect(MobEffects.WITHER);
                    targetPlayer.removeEffect(MobEffects.DARKNESS);
                    targetPlayer.removeEffect(MobEffects.BLINDNESS);
                }
            }
            level.playSound(null, pos, EarthTheology.SOUND_ALTAR_EXORCISE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
    }

    /** T-003 prayer_candle. */
    public static final class PrayerCandleBlock extends Block {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public PrayerCandleBlock(Properties properties) {
            super(properties.lightLevel(state -> state.getValue(LIT) ? 12 : 0));
            this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!state.getValue(LIT) && (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE))) {
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
                    SanctumFieldSavedData.get(serverLevel).rescanNearbyAltars(serverLevel, pos);
                    if (stack.is(Items.FIRE_CHARGE) && !player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (state.getValue(LIT)) {
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
                    SanctumFieldSavedData.get(serverLevel).rescanNearbyAltars(serverLevel, pos);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
    }

    /** T-004 scripture_lectern. */
    public static final class ScriptureLecternBlock extends Block implements EntityBlock {
        public static final BooleanProperty HAS_SCROLL = BooleanProperty.create("has_scroll");

        public ScriptureLecternBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(HAS_SCROLL, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(HAS_SCROLL);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TheologyBlockEntities.ScriptureLecternBlockEntity(pos, state);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.is(EarthTheology.SCRIPTURE_SCROLL.get())) {
                if (level.getBlockEntity(pos) instanceof TheologyBlockEntities.ScriptureLecternBlockEntity be && be.scroll().isEmpty()) {
                    if (level instanceof ServerLevel) {
                        be.setScroll(stack.split(1));
                    }
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.CONSUME;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level.getBlockEntity(pos) instanceof TheologyBlockEntities.ScriptureLecternBlockEntity be)) {
                return InteractionResult.PASS;
            }
            ItemStack scroll = be.scroll();
            if (scroll.isEmpty()) {
                return InteractionResult.PASS;
            }
            if (player.isShiftKeyDown()) {
                if (level instanceof ServerLevel) {
                    ItemStack taken = scroll.copy();
                    be.setScroll(ItemStack.EMPTY);
                    if (!player.getInventory().add(taken)) {
                        player.drop(taken, false);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                StudyResult result = LoreApi.beginStudy(serverPlayer, scroll);
                if (!result.started()) {
                    serverPlayer.sendOverlayMessage(Component.translatable("lore.earth_lore.fail." + result.failCode()).withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected boolean hasAnalogOutputSignal(BlockState state) {
            return true;
        }

        @Override
        protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
            return state.getValue(HAS_SCROLL) ? 15 : 0;
        }
    }

    /** T-005 offering_tray. */
    public static final class OfferingTrayBlock extends Block implements EntityBlock {
        public OfferingTrayBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TheologyBlockEntities.OfferingTrayBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide() || type != EarthTheology.OFFERING_TRAY_BE.get()) {
                return null;
            }
            return (lvl, pos, st, be) -> TheologyBlockEntities.OfferingTrayBlockEntity.serverTick(
                    lvl, pos, st, (TheologyBlockEntities.OfferingTrayBlockEntity) be);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level instanceof ServerLevel serverLevel) {
                if (level.getBlockEntity(pos) instanceof TheologyBlockEntities.OfferingTrayBlockEntity trayBe && placer instanceof Player p) {
                    trayBe.setOwnerUuid(p.getUUID());
                }
                if (SanctumFieldSavedData.get(serverLevel).nearestAltar(pos, 4).isEmpty() && placer instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(failMsg("no_altar"));
                }
            }
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (!(level.getBlockEntity(pos) instanceof TheologyBlockEntities.OfferingTrayBlockEntity trayBe)) {
                return InteractionResult.PASS;
            }
            if (!TheologyTags.isOffering(stack)) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(failMsg("not_offering"));
                }
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel) {
                if (!trayBe.insertStack(stack)) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendOverlayMessage(failMsg("full"));
                    }
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TheologyBlockEntities.OfferingTrayBlockEntity trayBe) {
                ItemStack taken = trayBe.removeLastStack();
                if (!taken.isEmpty()) {
                    if (!player.getInventory().add(taken)) {
                        player.drop(taken, false);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }
    }

    /** T-007 censer. */
    public static final class CenserBlock extends Block implements EntityBlock {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public CenserBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TheologyBlockEntities.CenserBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide() || type != EarthTheology.CENSER_BE.get()) {
                return null;
            }
            return (lvl, pos, st, be) -> TheologyBlockEntities.CenserBlockEntity.serverTick(
                    lvl, pos, st, (TheologyBlockEntities.CenserBlockEntity) be);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.is(EarthTheology.INCENSE.get())) {
                if (level.getBlockEntity(pos) instanceof TheologyBlockEntities.CenserBlockEntity be) {
                    if (level instanceof ServerLevel) {
                        ItemStack cur = be.incenseStack();
                        int room = 16 - cur.getCount();
                        if (room > 0) {
                            int move = Math.min(room, stack.getCount());
                            if (cur.isEmpty()) {
                                be.setIncenseStack(stack.split(move));
                            } else {
                                cur.grow(move);
                                stack.shrink(move);
                                be.setChanged();
                            }
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TheologyBlockEntities.CenserBlockEntity be) {
                ItemStack cur = be.incenseStack();
                if (!cur.isEmpty()) {
                    ItemStack taken = cur.copy();
                    be.setIncenseStack(ItemStack.EMPTY);
                    if (!player.getInventory().add(taken)) {
                        player.drop(taken, false);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }
    }

    /** T-008 pew. */
    public static final class PewBlock extends HorizontalDirectionalBlock {
        public static final MapCodec<PewBlock> CODEC = simpleCodec(PewBlock::new);
        public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

        public PewBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level instanceof ServerLevel serverLevel) {
                SanctumFieldSavedData.get(serverLevel).rescanNearbyAltars(serverLevel, pos);
            }
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel serverLevel) {
                List<PewSeatEntity> existing = serverLevel.getEntitiesOfClass(PewSeatEntity.class, new AABB(pos));
                if (!existing.isEmpty()) {
                    return InteractionResult.PASS;
                }
                PewSeatEntity seat = EarthTheology.SEAT_ENTITY.get().create(serverLevel, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                if (seat != null) {
                    seat.bindTo(pos);
                    serverLevel.addFreshEntity(seat);
                    player.startRiding(seat, false, false);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** T-010 reliquary. */
    public static final class ReliquaryBlock extends Block implements EntityBlock {
        public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;

        public ReliquaryBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(OCCUPIED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(OCCUPIED);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TheologyBlockEntities.ReliquaryBlockEntity(pos, state);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level.getBlockEntity(pos) instanceof TheologyBlockEntities.ReliquaryBlockEntity be && placer instanceof Player p) {
                be.setOwnerUuid(p.getUUID());
            }
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.is(EarthTheology.RELIC.get())) {
                if (level.getBlockEntity(pos) instanceof TheologyBlockEntities.ReliquaryBlockEntity be) {
                    if (!be.relic().isEmpty()) {
                        if (player instanceof ServerPlayer sp) {
                            sp.sendOverlayMessage(failMsg("occupied"));
                        }
                        return InteractionResult.FAIL;
                    }
                    if (level instanceof ServerLevel) {
                        be.setRelic(stack.split(1));
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof TheologyBlockEntities.ReliquaryBlockEntity be) {
                if (be.relic().isEmpty()) {
                    return InteractionResult.PASS;
                }
                if (be.ownerUuid() != null && !be.ownerUuid().equals(player.getUUID())) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendOverlayMessage(failMsg("not_owner"));
                    }
                    return InteractionResult.FAIL;
                }
                ItemStack taken = be.relic().copy();
                be.setRelic(ItemStack.EMPTY);
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** T-012 temple_keystone. */
    public static final class TempleKeystoneBlock extends Block {
        public TempleKeystoneBlock(Properties properties) {
            super(properties);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level instanceof ServerLevel serverLevel) {
                SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
                if (field.nearestAltar(pos, TheologyMath.ALTAR_REACH).isEmpty() && placer instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(failMsg("no_altar"));
                } else {
                    field.rescanNearbyAltars(serverLevel, pos);
                }
            }
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
                Optional<SanctumFieldSavedData.AltarRecord> near = field.nearestAltar(pos, TheologyMath.ALTAR_REACH);
                if (near.isEmpty()) {
                    sp.sendOverlayMessage(failMsg("no_altar"));
                    return InteractionResult.FAIL;
                }
                SanctumFieldSavedData.AltarRecord altar = near.get();
                List<String> fails = field.checkTempleFormation(serverLevel, altar.pos());
                field.rescanAltar(serverLevel, altar);
                if (fails.isEmpty()) {
                    sp.sendSystemMessage(Component.translatable("theology.earth_theology.temple.formed").withStyle(ChatFormatting.GOLD));
                    serverLevel.playSound(null, pos, EarthTheology.SOUND_TEMPLE_FORMED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                } else {
                    for (String code : fails) {
                        sp.sendSystemMessage(failMsg(code));
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** T-015 ward_stone. */
    public static final class WardStoneBlock extends Block implements EntityBlock {
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        public WardStoneBlock(Properties properties) {
            super(properties);
            this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVE);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TheologyBlockEntities.WardStoneBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide() || type != EarthTheology.WARD_STONE_BE.get()) {
                return null;
            }
            return (lvl, pos, st, be) -> TheologyBlockEntities.WardStoneBlockEntity.serverTick(
                    lvl, pos, st, (TheologyBlockEntities.WardStoneBlockEntity) be);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level instanceof ServerLevel serverLevel) {
                if (SanctumFieldSavedData.get(serverLevel).nearestAltar(pos, TheologyMath.ALTAR_REACH).isEmpty() && placer instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(failMsg("no_altar"));
                }
            }
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.is(EarthTheology.HOLY_WATER.get())) {
                if (!(level.getBlockEntity(pos) instanceof TheologyBlockEntities.WardStoneBlockEntity wardBe)) {
                    return InteractionResult.PASS;
                }
                if (wardBe.water() >= 16) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendOverlayMessage(failMsg("full"));
                    }
                    return InteractionResult.FAIL;
                }
                if (level instanceof ServerLevel serverLevel) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                        if (!player.getInventory().add(bottle)) {
                            player.drop(bottle, false);
                        }
                    }
                    wardBe.setWater(wardBe.water() + 1);
                    if (SanctumFieldSavedData.get(serverLevel).nearestAltar(pos, TheologyMath.ALTAR_REACH).isEmpty() && player instanceof ServerPlayer sp) {
                        sp.sendOverlayMessage(failMsg("no_altar"));
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
    }
}

