package com.xxsx.earththeology;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * All 6 BlockEntities for Earth: Theology (18 册 §4–§9).
 */
public final class TheologyBlockEntities {
    private TheologyBlockEntities() {
    }

    /** T-002 shrine_altar BlockEntity. */
    public static final class ShrineAltarBlockEntity extends BlockEntity {
        private UUID ownerUuid;
        private int waterCooldown;
        private int exorcismCooldown;
        private int blessCooldown;
        private boolean generatedSanctuary;
        private boolean monastery;
        private boolean residentsSpawned;

        public ShrineAltarBlockEntity(BlockPos pos, BlockState state) {
            super(EarthTheology.SHRINE_ALTAR_BE.get(), pos, state);
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, ShrineAltarBlockEntity be) {
            boolean changed = false;
            if (be.waterCooldown > 0) {
                be.waterCooldown--;
                changed = true;
            }
            if (be.exorcismCooldown > 0) {
                be.exorcismCooldown--;
                changed = true;
            }
            if (be.blessCooldown > 0) {
                be.blessCooldown--;
                changed = true;
            }
            if (level instanceof ServerLevel serverLevel && be.generatedSanctuary && serverLevel.getGameTime() % 20 == 0) {
                SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
                if (field.getAltar(pos).isEmpty()) {
                    field.tryRegisterAltar(serverLevel, pos, be.ownerUuid);
                }
                if (!be.residentsSpawned) {
                    be.spawnSanctuaryClergy(serverLevel, pos);
                    be.residentsSpawned = true;
                    changed = true;
                }
            }
            if (level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % TheologyMath.SANCTUM_REFRESH_TICKS == 0) {
                SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
                field.getAltar(pos).ifPresent(record -> field.rescanAltar(serverLevel, record));
            }
            if (changed) {
                be.setChanged();
            }
        }

        public void configureGeneratedSanctuary(boolean monastery) {
            this.generatedSanctuary = true;
            this.monastery = monastery;
            this.residentsSpawned = false;
            setChanged();
        }

        public boolean generatedSanctuary() {
            return generatedSanctuary;
        }

        public boolean monastery() {
            return monastery;
        }

        private void spawnSanctuaryClergy(ServerLevel level, BlockPos pos) {
            spawnClergyMember(level, pos.offset(1, 0, 1), true, monastery ? 2 : 1);
            if (monastery) {
                spawnClergyMember(level, pos.offset(-1, 0, 1), false, 1);
            }
        }

        private static void spawnClergyMember(ServerLevel level, BlockPos spot, boolean priest, int profLevel) {
            Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
            if (villager == null) {
                return;
            }
            var prof = priest ? EarthTheology.PRIEST : EarthTheology.SCRIBE;
            villager.setVillagerData(villager.getVillagerData().withProfession(prof).withLevel(profLevel));
            villager.snapTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
            villager.setPersistenceRequired();
            villager.getOffers();
            level.addFreshEntity(villager);
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public void setOwnerUuid(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
            setChanged();
        }

        public int waterCooldown() {
            return waterCooldown;
        }

        public void setWaterCooldown(int waterCooldown) {
            this.waterCooldown = Math.max(0, Math.min(100, waterCooldown));
            setChanged();
        }

        public int exorcismCooldown() {
            return exorcismCooldown;
        }

        public void setExorcismCooldown(int exorcismCooldown) {
            this.exorcismCooldown = Math.max(0, Math.min(6000, exorcismCooldown));
            setChanged();
        }

        public int blessCooldown() {
            return blessCooldown;
        }

        public void setBlessCooldown(int blessCooldown) {
            this.blessCooldown = Math.max(0, Math.min(1200, blessCooldown));
            setChanged();
        }

        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
            if (level instanceof ServerLevel serverLevel) {
                SanctumFieldSavedData.get(serverLevel).removeAltar(pos);
            }
            super.preRemoveSideEffects(pos, oldState);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.storeNullable("owner_uuid", UUIDUtil.CODEC, ownerUuid);
            output.putInt("water_cooldown", waterCooldown);
            output.putInt("exorcism_cooldown", exorcismCooldown);
            output.putInt("bless_cooldown", blessCooldown);
            output.putBoolean("generated_sanctuary", generatedSanctuary);
            output.putBoolean("monastery", monastery);
            output.putBoolean("residents_spawned", residentsSpawned);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            ownerUuid = input.read("owner_uuid", UUIDUtil.CODEC).orElse(null);
            waterCooldown = Math.max(0, Math.min(100, input.getIntOr("water_cooldown", 0)));
            exorcismCooldown = Math.max(0, Math.min(6000, input.getIntOr("exorcism_cooldown", 0)));
            blessCooldown = Math.max(0, Math.min(1200, input.getIntOr("bless_cooldown", 0)));
            generatedSanctuary = input.getBooleanOr("generated_sanctuary", false);
            monastery = input.getBooleanOr("monastery", false);
            residentsSpawned = input.getBooleanOr("residents_spawned", false);
        }
    }

    /** T-004 scripture_lectern BlockEntity. */
    public static final class ScriptureLecternBlockEntity extends BlockEntity {
        private ItemStack scroll = ItemStack.EMPTY;

        public ScriptureLecternBlockEntity(BlockPos pos, BlockState state) {
            super(EarthTheology.SCRIPTURE_LECTERN_BE.get(), pos, state);
        }

        public ItemStack scroll() {
            return scroll;
        }

        public void setScroll(ItemStack stack) {
            this.scroll = stack == null ? ItemStack.EMPTY : stack;
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                BlockState st = getBlockState();
                boolean has = !this.scroll.isEmpty();
                if (st.is(EarthTheology.SCRIPTURE_LECTERN.get()) && st.getValue(TheologyBlocks.ScriptureLecternBlock.HAS_SCROLL) != has) {
                    serverLevel.setBlock(worldPosition, st.setValue(TheologyBlocks.ScriptureLecternBlock.HAS_SCROLL, has), Block.UPDATE_ALL);
                }
                SanctumFieldSavedData.get(serverLevel).rescanNearbyAltars(serverLevel, worldPosition);
            }
        }

        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
            if (level != null && !scroll.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, scroll.copy());
                scroll = ItemStack.EMPTY;
            }
            super.preRemoveSideEffects(pos, oldState);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.storeNullable("scroll", ItemStack.CODEC, scroll.isEmpty() ? null : scroll);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            scroll = input.read("scroll", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }
    }

    /** T-005 offering_tray BlockEntity (accepts hopper from UP for #earth_theology:offerings). */
    public static final class OfferingTrayBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
        private static final int[] SLOTS_UP = {0, 1, 2, 3};
        private static final int[] SLOTS_NONE = {};
        private NonNullList<ItemStack> contents = NonNullList.withSize(4, ItemStack.EMPTY);
        private UUID ownerUuid;
        private long lastSettledDay = -1L;

        public OfferingTrayBlockEntity(BlockPos pos, BlockState state) {
            super(EarthTheology.OFFERING_TRAY_BE.get(), pos, state);
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, OfferingTrayBlockEntity be) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            long dayTime = serverLevel.getDefaultClockTime();
            long currentDay = TheologyMath.gameDay(dayTime);
            if (dayTime % 24000L == 0L && be.lastSettledDay != currentDay) {
                be.lastSettledDay = currentDay;
                be.settle(serverLevel);
            }
        }

        public void settle(ServerLevel level) {
            SanctumFieldSavedData field = SanctumFieldSavedData.get(level);
            Optional<SanctumFieldSavedData.AltarRecord> near = field.nearestAltar(worldPosition, 4);
            if (near.isEmpty()) {
                return;
            }
            int maxVal = 0;
            boolean any = false;
            for (ItemStack stack : contents) {
                if (!stack.isEmpty()) {
                    any = true;
                    maxVal = Math.max(maxVal, TheologyTags.offeringValue(stack));
                }
            }
            if (!any || maxVal <= 0) {
                return;
            }
            for (int i = 0; i < contents.size(); i++) {
                contents.set(i, ItemStack.EMPTY);
            }
            long nextDayStart = (TheologyMath.gameDay(level.getDefaultClockTime()) + 1L) * 24000L;
            SanctumFieldSavedData.AltarRecord altar = near.get();
            altar.setBlessedUntil(Math.max(level.getGameTime() + 24000L, nextDayStart));
            field.rescanAltar(level, altar);
            setChanged();
            level.playSound(null, worldPosition, EarthTheology.SOUND_ALTAR_OFFER.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 1.2F);
            TheologyBlocks.emitSacredParticles(level, worldPosition, 10, 8, 8);
            TheologyBlocks.emitSacredParticles(level, altar.pos(), 8, 6, 6);

            if (ownerUuid != null && com.xxsx.earthlore.api.LoreApi.knows(ownerUuid, TheologyLoreIntegration.NODE_OFFERINGS)) {
                var ownerPlayer = level.getServer().getPlayerList().getPlayer(ownerUuid);
                if (ownerPlayer != null) {
                    PietyAttachment piety = ownerPlayer.getData(EarthTheology.PIETY.get());
                    long today = TheologyMath.gameDay(level.getDefaultClockTime());
                    if (piety.offeringDay() != today) {
                        piety.setOfferingDay(today);
                        piety.setOfferingGained(0);
                    }
                    boolean feast = TheologyMath.isFeastDay(level.getDefaultClockTime());
                    int gain = TheologyMath.computeOfferingPietyGain(maxVal, piety.offeringGained(), feast, true);
                    if (gain > 0) {
                        piety.addPiety(gain);
                        piety.setOfferingGained(piety.offeringGained() + gain);
                        ownerPlayer.setData(EarthTheology.PIETY.get(), piety);
                    }
                }
            }
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public void setOwnerUuid(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
            setChanged();
        }

        public boolean insertStack(ItemStack stack) {
            for (int i = 0; i < contents.size(); i++) {
                if (contents.get(i).isEmpty()) {
                    contents.set(i, stack.copy());
                    stack.setCount(0);
                    setChanged();
                    return true;
                }
            }
            return false;
        }

        public ItemStack removeLastStack() {
            for (int i = contents.size() - 1; i >= 0; i--) {
                if (!contents.get(i).isEmpty()) {
                    ItemStack out = contents.get(i);
                    contents.set(i, ItemStack.EMPTY);
                    setChanged();
                    return out;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getContainerSize() {
            return 4;
        }

        @Override
        protected NonNullList<ItemStack> getItems() {
            return contents;
        }

        @Override
        protected void setItems(NonNullList<ItemStack> items) {
            this.contents = items;
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable("block.earth_theology.offering_tray");
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return null;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.UP ? SLOTS_UP : SLOTS_NONE;
        }

        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return TheologyTags.isOffering(stack);
        }

        @Override
        public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
            return direction == Direction.UP && TheologyTags.isOffering(stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
            return false;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            ContainerHelper.saveAllItems(output, contents);
            output.storeNullable("owner_uuid", UUIDUtil.CODEC, ownerUuid);
            output.putLong("last_settled_day", lastSettledDay);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            contents = NonNullList.withSize(4, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(input, contents);
            ownerUuid = input.read("owner_uuid", UUIDUtil.CODEC).orElse(null);
            lastSettledDay = input.getLongOr("last_settled_day", -1L);
        }
    }

    /** T-007 censer BlockEntity (accepts hopper from UP for incense). */
    public static final class CenserBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
        private static final int[] SLOTS_UP = {0};
        private static final int[] SLOTS_NONE = {};
        private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
        private int burnTicks;

        public CenserBlockEntity(BlockPos pos, BlockState state) {
            super(EarthTheology.CENSER_BE.get(), pos, state);
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, CenserBlockEntity be) {
            boolean wasLit = state.getValue(TheologyBlocks.CenserBlock.LIT);
            if (be.burnTicks == 0 && !be.items.get(0).isEmpty() && be.items.get(0).is(EarthTheology.INCENSE.get())) {
                be.items.get(0).shrink(1);
                if (be.items.get(0).isEmpty()) {
                    be.items.set(0, ItemStack.EMPTY);
                }
                be.burnTicks = 2400;
                be.setChanged();
            } else if (be.burnTicks > 0) {
                be.burnTicks--;
                if (level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 10 == 0) {
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 1, 0.05D, 0.05D, 0.05D, 0.01D);
                    if (serverLevel.getGameTime() % 20 == 0) {
                        TheologyBlocks.emitSacredParticles(serverLevel, pos, 1, 1, 1);
                    }
                    if (serverLevel.getGameTime() % 80 == 0) {
                        serverLevel.playSound(null, pos, EarthTheology.SOUND_CENSER_BURN.get(), SoundSource.BLOCKS, 0.35F, 1.0F);
                    }
                }
                be.setChanged();
            }
            boolean nowLit = be.burnTicks > 0;
            if (wasLit != nowLit && level instanceof ServerLevel serverLevel) {
                serverLevel.setBlock(pos, state.setValue(TheologyBlocks.CenserBlock.LIT, nowLit), Block.UPDATE_ALL);
                SanctumFieldSavedData.get(serverLevel).rescanNearbyAltars(serverLevel, pos);
            }
        }

        public int burnTicks() {
            return burnTicks;
        }

        public void setBurnTicks(int burnTicks) {
            this.burnTicks = Math.max(0, Math.min(2400, burnTicks));
            setChanged();
        }

        public ItemStack incenseStack() {
            return items.get(0);
        }

        public void setIncenseStack(ItemStack stack) {
            items.set(0, stack);
            setChanged();
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        protected NonNullList<ItemStack> getItems() {
            return items;
        }

        @Override
        protected void setItems(NonNullList<ItemStack> items) {
            this.items = items;
        }

        @Override
        protected Component getDefaultName() {
            return Component.translatable("block.earth_theology.censer");
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return null;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return side == Direction.UP ? SLOTS_UP : SLOTS_NONE;
        }

        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.is(EarthTheology.INCENSE.get()) && (items.get(0).isEmpty() || items.get(0).getCount() < 16);
        }

        @Override
        public int getMaxStackSize() {
            return 16;
        }

        @Override
        public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
            return direction == Direction.UP && canPlaceItem(index, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
            return false;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            ContainerHelper.saveAllItems(output, items);
            output.putInt("burn_ticks", burnTicks);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            items = NonNullList.withSize(1, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(input, items);
            burnTicks = Math.max(0, Math.min(2400, input.getIntOr("burn_ticks", 0)));
        }
    }

    /** T-010 reliquary BlockEntity. */
    public static final class ReliquaryBlockEntity extends BlockEntity {
        private ItemStack relic = ItemStack.EMPTY;
        private UUID ownerUuid;

        public ReliquaryBlockEntity(BlockPos pos, BlockState state) {
            super(EarthTheology.RELIQUARY_BE.get(), pos, state);
        }

        public ItemStack relic() {
            return relic;
        }

        public void setRelic(ItemStack stack) {
            this.relic = stack == null ? ItemStack.EMPTY : stack;
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                BlockState st = getBlockState();
                boolean occ = !this.relic.isEmpty();
                if (st.is(EarthTheology.RELIQUARY.get()) && st.getValue(TheologyBlocks.ReliquaryBlock.OCCUPIED) != occ) {
                    serverLevel.setBlock(worldPosition, st.setValue(TheologyBlocks.ReliquaryBlock.OCCUPIED, occ), Block.UPDATE_ALL);
                }
                SanctumFieldSavedData.get(serverLevel).rescanNearbyAltars(serverLevel, worldPosition);
            }
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public void setOwnerUuid(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
            setChanged();
        }

        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
            if (level != null && !relic.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, relic.copy());
                relic = ItemStack.EMPTY;
            }
            super.preRemoveSideEffects(pos, oldState);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.storeNullable("relic", ItemStack.CODEC, relic.isEmpty() ? null : relic);
            output.storeNullable("owner_uuid", UUIDUtil.CODEC, ownerUuid);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            relic = input.read("relic", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            ownerUuid = input.read("owner_uuid", UUIDUtil.CODEC).orElse(null);
        }
    }

    /** T-015 ward_stone BlockEntity. */
    public static final class WardStoneBlockEntity extends BlockEntity {
        private int water;
        private boolean active;
        private long lastUpkeepDay = -1L;

        public WardStoneBlockEntity(BlockPos pos, BlockState state) {
            super(EarthTheology.WARD_STONE_BE.get(), pos, state);
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, WardStoneBlockEntity be) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            long dayTime = serverLevel.getDefaultClockTime();
            long currentDay = TheologyMath.gameDay(dayTime);
            if (dayTime % 24000L == 0L && be.lastUpkeepDay != currentDay) {
                be.lastUpkeepDay = currentDay;
                if (be.water > 0) {
                    be.water--;
                    be.setChanged();
                    SanctumFieldSavedData.get(serverLevel).rescanNearbyAltars(serverLevel, pos);
                }
            }
            if (be.active && serverLevel.getGameTime() % 40 == 0) {
                TheologyBlocks.emitSacredParticles(serverLevel, pos, 2, 1, 3);
                SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
                field.nearestAltar(pos, TheologyMath.ALTAR_REACH).ifPresent(altar -> {
                    BlockPos center = altar.pos();
                    AABB box = new AABB(center).inflate(TheologyMath.WARD_RADIUS);
                    for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, box, TheologyTags::isUndead)) {
                        if (entity.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D)
                                <= TheologyMath.WARD_RADIUS * TheologyMath.WARD_RADIUS) {
                            entity.hurtServer(serverLevel, serverLevel.damageSources().magic(), 1.0F);
                            TheologyBlocks.emitSacredParticles(serverLevel, entity.blockPosition(), 3, 1, 3);
                        }
                    }
                });
            }
        }

        public int water() {
            return water;
        }

        public void setWater(int water) {
            this.water = Math.max(0, Math.min(16, water));
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                SanctumFieldSavedData.get(serverLevel).rescanNearbyAltars(serverLevel, worldPosition);
            }
        }

        public boolean active() {
            return active;
        }

        public void updateActiveState(ServerLevel level, BlockPos pos, boolean shouldActive) {
            if (this.active != shouldActive) {
                this.active = shouldActive;
                setChanged();
                BlockState st = level.getBlockState(pos);
                if (st.is(EarthTheology.WARD_STONE.get()) && st.getValue(TheologyBlocks.WardStoneBlock.ACTIVE) != shouldActive) {
                    level.setBlock(pos, st.setValue(TheologyBlocks.WardStoneBlock.ACTIVE, shouldActive), Block.UPDATE_ALL);
                }
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("water", water);
            output.putBoolean("active", active);
            output.putLong("last_upkeep_day", lastUpkeepDay);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            water = Math.max(0, Math.min(16, input.getIntOr("water", 0)));
            active = input.getBooleanOr("active", false);
            lastUpkeepDay = input.getLongOr("last_upkeep_day", -1L);
        }
    }
}

