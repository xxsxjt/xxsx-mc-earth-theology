package com.xxsx.earththeology;

import com.xxsx.earthlore.api.Grade;
import com.xxsx.earthlore.api.LoreApi;
import com.xxsx.earthlore.study.LiteratureItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * All 10 items for Earth: Theology (18 册 §4–§9).
 */
public final class TheologyItems {
    private TheologyItems() {
    }

    public static void treatEarthHumanInjuries(ServerPlayer player, double fractureRelief,
                                               double bodyHeal, double lungAirRestore,
                                               double fatigueReduction) {
        try {
            Class<?> bodyClass = Class.forName("com.xxsx.earthhuman.HumanBodyStatus");
            Class<?> statusClass = Class.forName("com.xxsx.earthhuman.HumanStatus");
            Class<?> netClass = Class.forName("com.xxsx.earthhuman.HumanNetwork");

            java.lang.reflect.Method getBody = bodyClass.getMethod("get", Player.class);
            Object body = getBody.invoke(null, player);
            if (fractureRelief > 0.0D) {
                java.lang.reflect.Method treatFracture = bodyClass.getMethod("treatFracture", ServerPlayer.class, double.class, double.class);
                treatFracture.invoke(body, player, fractureRelief, bodyHeal);
            } else if (bodyHeal > 0.0D) {
                java.lang.reflect.Method healWorst = bodyClass.getMethod("healWorst", ServerPlayer.class, double.class, boolean.class);
                healWorst.invoke(body, player, bodyHeal, true);
            }
            bodyClass.getMethod("applyFunctionalEffects", ServerPlayer.class).invoke(body, player);
            bodyClass.getMethod("save", Player.class).invoke(body, player);
            netClass.getMethod("syncBody", ServerPlayer.class, bodyClass).invoke(null, player, body);

            Object status = statusClass.getMethod("get", Player.class).invoke(null, player);
            if (lungAirRestore > 0.0D) {
                double air = statusClass.getField("airQuality").getDouble(status);
                statusClass.getField("airQuality").setDouble(status, Math.max(0.0D, Math.min(100.0D, air + lungAirRestore)));
            }
            if (fatigueReduction > 0.0D) {
                double fat = statusClass.getField("fatigue").getDouble(status);
                statusClass.getField("fatigue").setDouble(status, Math.max(0.0D, fat - fatigueReduction));
            }
            statusClass.getMethod("save", Player.class).invoke(status, player);
            netClass.getMethod("sync", ServerPlayer.class, statusClass).invoke(null, player, status);
        } catch (Throwable ignored) {
        }
    }

    /** T-001 prayer_beads (modes: daily, healing, revelation, miracle). */
    public static final class PrayerBeadsItem extends Item {
        public enum Mode {
            DAILY("daily", 100),
            HEALING("healing", 100),
            REVELATION("revelation", 200),
            MIRACLE("miracle", 400);

            private final String id;
            private final int ticks;

            Mode(String id, int ticks) {
                this.id = id;
                this.ticks = ticks;
            }

            public String id() {
                return id;
            }

            public int ticks() {
                return ticks;
            }

            public static Mode byId(String id) {
                for (Mode m : values()) {
                    if (m.id.equalsIgnoreCase(id)) {
                        return m;
                    }
                }
                return DAILY;
            }
        }

        public enum MiracleKind {
            REVIVAL("revival"),
            CLEAR_SKY("clear_sky"),
            HOLY_LIGHT("holy_light");

            private final String id;

            MiracleKind(String id) {
                this.id = id;
            }

            public String id() {
                return id;
            }

            public static MiracleKind byId(String id) {
                for (MiracleKind k : values()) {
                    if (k.id.equalsIgnoreCase(id)) {
                        return k;
                    }
                }
                return REVIVAL;
            }
        }

        public PrayerBeadsItem(Properties properties) {
            super(properties);
        }

        public static Mode getMode(ItemStack stack) {
            CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            return Mode.byId(cd.copyTag().getStringOr("mode", "daily"));
        }

        public static void setMode(ItemStack stack, Mode mode) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("mode", mode.id()));
        }

        public static MiracleKind getMiracleKind(ItemStack stack) {
            CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            return MiracleKind.byId(cd.copyTag().getStringOr("miracle_kind", "revival"));
        }

        public static void setMiracleKind(ItemStack stack, MiracleKind kind) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("miracle_kind", kind.id()));
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return 72000;
        }

        @Override
        public ItemUseAnimation getUseAnimation(ItemStack stack) {
            return ItemUseAnimation.BOW;
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
            if (!(player.level() instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            if (player.getCooldowns().isOnCooldown(stack)) {
                sp.sendOverlayMessage(TheologyBlocks.failMsg("cooldown"));
                return InteractionResult.FAIL;
            }
            if (target instanceof Villager || target instanceof Player || (target instanceof TamableAnimal ta && ta.isTame())) {
                target.heal(6.0F);
                target.addEffect(new MobEffectInstance(EarthTheology.BLESSED, 1200, 0, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 0, false, true, true));
                sp.addEffect(new MobEffectInstance(EarthTheology.SERENITY, 600, 0, false, true, true));
                treatEarthHumanInjuries(sp, 0.35D, 2.0D, 20.0D, 8.0D);
                PietyAttachment piety = sp.getData(EarthTheology.PIETY.get());
                piety.addPiety(1);
                sp.setData(EarthTheology.PIETY.get(), piety);
                player.getCooldowns().addCooldown(stack, 120);
                player.swing(hand, true);
                serverLevel.playSound(null, target.blockPosition(), EarthTheology.SOUND_ALTAR_BLESS.get(), SoundSource.PLAYERS, 0.8F, 1.15F);
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.2F);
                TheologyBlocks.emitSacredParticles(serverLevel, target.blockPosition(), 10, 6, 8);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isShiftKeyDown()) {
                if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                    cycleMode(sp, stack);
                    sp.swing(hand, true);
                    serverLevel.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.25F);
                    TheologyBlocks.emitSacredParticles(serverLevel, sp.blockPosition(), 3, 2, 3);
                }
                return InteractionResult.SUCCESS;
            }
            if (player.getCooldowns().isOnCooldown(stack)) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(TheologyBlocks.failMsg("cooldown"));
                }
                return InteractionResult.FAIL;
            }
            Mode mode = getMode(stack);
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                if (!beginPrayer(serverLevel, sp, stack, mode)) {
                    return InteractionResult.FAIL;
                }
                serverLevel.playSound(null, sp.blockPosition(), EarthTheology.SOUND_BEADS_PRAY.get(), SoundSource.PLAYERS, 0.6F, 1.05F);
                serverLevel.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.15F);
                TheologyBlocks.emitSacredParticles(serverLevel, sp.blockPosition(), 4, 2, 4);
            }
            player.startUsingItem(hand);
            return InteractionResult.SUCCESS;
        }

        private static void cycleMode(ServerPlayer player, ItemStack stack) {
            List<Mode> unlocked = new ArrayList<>();
            unlocked.add(Mode.DAILY);
            if (LoreApi.knows(player, TheologyLoreIntegration.NODE_HEALING_PRAYER)) {
                unlocked.add(Mode.HEALING);
            }
            if (LoreApi.knows(player, TheologyLoreIntegration.NODE_REVELATION)) {
                unlocked.add(Mode.REVELATION);
            }
            if (LoreApi.knows(player, TheologyLoreIntegration.NODE_MIRACLES)) {
                unlocked.add(Mode.MIRACLE);
            }
            Mode cur = getMode(stack);
            if (cur == Mode.MIRACLE && unlocked.contains(Mode.MIRACLE)) {
                MiracleKind mk = getMiracleKind(stack);
                if (mk == MiracleKind.REVIVAL) {
                    setMiracleKind(stack, MiracleKind.CLEAR_SKY);
                    player.sendOverlayMessage(Component.translatable("theology.earth_theology.beads.miracle.clear_sky").withStyle(ChatFormatting.GOLD));
                    return;
                } else if (mk == MiracleKind.CLEAR_SKY) {
                    setMiracleKind(stack, MiracleKind.HOLY_LIGHT);
                    player.sendOverlayMessage(Component.translatable("theology.earth_theology.beads.miracle.holy_light").withStyle(ChatFormatting.GOLD));
                    return;
                } else {
                    setMiracleKind(stack, MiracleKind.REVIVAL);
                }
            }
            int idx = unlocked.indexOf(cur);
            Mode next = unlocked.get((idx + 1) % unlocked.size());
            setMode(stack, next);
            player.sendOverlayMessage(Component.translatable("theology.earth_theology.beads.mode." + next.id()).withStyle(ChatFormatting.GOLD));
        }

        private static boolean beginPrayer(ServerLevel level, ServerPlayer player, ItemStack stack, Mode mode) {
            SanctumFieldSavedData field = SanctumFieldSavedData.get(level);
            Optional<SanctumFieldSavedData.AltarRecord> nearAltar = field.nearestAltar(player.blockPosition(), TheologyMath.ALTAR_REACH);
            int sanctum = nearAltar.map(r -> field.rescanAltar(level, r).total()).orElse(0);

            switch (mode) {
                case DAILY -> {
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("active_prayer", true));
                    return true;
                }
                case HEALING -> {
                    if (!LoreApi.knows(player, TheologyLoreIntegration.NODE_HEALING_PRAYER)) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("lore_locked"));
                        return false;
                    }
                    if (sanctum < 20) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("sanctum_low"));
                        return false;
                    }
                    Grade bestGrade = consumeBestHolyWater(player);
                    if (bestGrade == null) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("no_holy_water"));
                        return false;
                    }
                    LivingEntity target = findHealTarget(level, player);
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                        tag.putBoolean("active_prayer", true);
                        tag.putString("heal_grade", bestGrade.id());
                        tag.putInt("heal_target_id", target.getId());
                    });
                    return true;
                }
                case REVELATION -> {
                    if (!LoreApi.knows(player, TheologyLoreIntegration.NODE_REVELATION)) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("lore_locked"));
                        return false;
                    }
                    if (!level.isDarkOutside()) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("needs_night"));
                        return false;
                    }
                    if (sanctum < 30) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("sanctum_low"));
                        return false;
                    }
                    PietyAttachment piety = player.getData(EarthTheology.PIETY.get());
                    long today = TheologyMath.gameDay(level.getDefaultClockTime());
                    if (piety.lastRevelationDay() == today) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("already_revealed"));
                        return false;
                    }
                    if (!consumeItemCount(player, EarthTheology.INCENSE.get(), 2)) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("no_incense"));
                        return false;
                    }
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("active_prayer", true));
                    return true;
                }
                case MIRACLE -> {
                    if (!LoreApi.knows(player, TheologyLoreIntegration.NODE_MIRACLES)) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("lore_locked"));
                        return false;
                    }
                    if (!level.isThundering()) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("needs_thunder"));
                        return false;
                    }
                    if (nearAltar.isEmpty() || !nearAltar.get().temple()) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("not_temple"));
                        return false;
                    }
                    if (sanctum < 60) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("sanctum_low"));
                        return false;
                    }
                    BlockPos altarPos = nearAltar.get().pos();
                    if (!hasReliquaryWithRelic(level, altarPos)) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("no_relic"));
                        return false;
                    }
                    PietyAttachment piety = player.getData(EarthTheology.PIETY.get());
                    long today = TheologyMath.gameDay(level.getDefaultClockTime());
                    if (today - piety.lastMiracleDay() < TheologyMath.MIRACLE_INTERVAL_DAYS) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("too_soon"));
                        return false;
                    }
                    if (countItems(player, EarthTheology.HOLY_WATER.get()) < 4 || countItems(player, EarthTheology.INCENSE.get()) < 4) {
                        player.sendOverlayMessage(TheologyBlocks.failMsg("missing_materials"));
                        return false;
                    }
                    consumeItemCount(player, EarthTheology.HOLY_WATER.get(), 4);
                    consumeItemCount(player, EarthTheology.INCENSE.get(), 4);
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("active_prayer", true));
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
            if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof ServerPlayer player)) {
                return;
            }
            CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            if (!cd.copyTag().getBooleanOr("active_prayer", false)) {
                return;
            }
            int elapsed = getUseDuration(stack, livingEntity) - remainingUseDuration;
            if (elapsed > 0 && elapsed % 20 == 0) {
                serverLevel.playSound(null, player.blockPosition(), EarthTheology.SOUND_BEADS_PRAY.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
                serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 1.2F);
                TheologyBlocks.emitSacredParticles(serverLevel, player.blockPosition(), 3, 2, 3);
            }
            Mode mode = getMode(stack);
            if (elapsed >= mode.ticks()) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("active_prayer", false));
                completePrayer(serverLevel, player, stack, mode);
                player.stopUsingItem();
            }
        }

        @Override
        public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            if (entity instanceof ServerPlayer player) {
                CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                if (cd.copyTag().getBooleanOr("active_prayer", false)) {
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("active_prayer", false));
                    player.sendOverlayMessage(TheologyBlocks.failMsg("interrupted"));
                }
            }
            return false;
        }

        public static void completePrayer(ServerLevel level, ServerPlayer player, ItemStack stack, Mode mode) {
            SanctumFieldSavedData field = SanctumFieldSavedData.get(level);
            Optional<SanctumFieldSavedData.AltarRecord> nearAltar = field.nearestAltar(player.blockPosition(), TheologyMath.ALTAR_REACH);

            switch (mode) {
                case DAILY -> {
                    player.addEffect(new MobEffectInstance(EarthTheology.SERENITY, 600, 0, false, true, true));
                    treatEarthHumanInjuries(player, 0.35D, 2.5D, 25.0D, 10.0D);
                    if (nearAltar.isPresent()) {
                        PietyAttachment piety = player.getData(EarthTheology.PIETY.get());
                        long today = TheologyMath.gameDay(level.getDefaultClockTime());
                        if (piety.lastPrayerDay() != today) {
                            int gain = TheologyMath.isFeastDay(level.getDefaultClockTime()) ? 2 : 1;
                            if (player.getItemBySlot(EquipmentSlot.CHEST).is(EarthTheology.VESTMENT.get())) {
                                gain += 1;
                            }
                            piety.addPiety(gain);
                            piety.setLastPrayerDay(today);
                            player.setData(EarthTheology.PIETY.get(), piety);
                        }
                    }
                    player.getCooldowns().addCooldown(stack, TheologyMath.PRAYER_COOLDOWN);
                    level.playSound(null, player.blockPosition(), EarthTheology.SOUND_ALTAR_BLESS.get(), SoundSource.PLAYERS, 0.8F, 1.1F);
                    level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.65F, 1.2F);
                    level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.15F);
                    TheologyBlocks.emitSacredParticles(level, player.blockPosition(), 10, 6, 8);
                }
                case HEALING -> {
                    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    String gradeId = tag.getStringOr("heal_grade", "common");
                    Grade grade = Grade.COMMON;
                    for (Grade g : Grade.values()) {
                        if (g.id().equalsIgnoreCase(gradeId)) {
                            grade = g;
                            break;
                        }
                    }
                    int targetId = tag.getIntOr("heal_target_id", player.getId());
                    var ent = level.getEntity(targetId);
                    LivingEntity target = ent instanceof LivingEntity le && le.isAlive() ? le : player;
                    int pietyVal = player.getData(EarthTheology.PIETY.get()).value();
                    float amount = TheologyMath.computeHealingPrayerAmount(pietyVal, grade);
                    target.heal(amount);
                    target.removeEffect(MobEffects.POISON);
                    target.removeEffect(MobEffects.WITHER);
                    if (target instanceof ServerPlayer spTarget) {
                        treatEarthHumanInjuries(spTarget, 0.80D, 5.0D, 45.0D, 18.0D);
                    }
                    player.getCooldowns().addCooldown(stack, 600);
                    level.playSound(null, target.blockPosition(), EarthTheology.SOUND_ALTAR_BLESS.get(), SoundSource.PLAYERS, 0.9F, 1.1F);
                    level.playSound(null, target.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.2F);
                    level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
                    TheologyBlocks.emitSacredParticles(level, target.blockPosition(), 14, 10, 10);
                }
                case REVELATION -> {
                    PietyAttachment piety = player.getData(EarthTheology.PIETY.get());
                    piety.setLastRevelationDay(TheologyMath.gameDay(level.getDefaultClockTime()));
                    player.setData(EarthTheology.PIETY.get(), piety);
                    BlockPos center = nearAltar.map(SanctumFieldSavedData.AltarRecord::pos).orElse(player.blockPosition());
                    AABB box = new AABB(center).inflate(48.0D);
                    for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy)) {
                        if (mob.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= 48.0D * 48.0D) {
                            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, true, true));
                        }
                    }
                    reportUnopenedLootContainers(level, player, center);
                    level.playSound(null, center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.9F, 0.9F);
                    level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.75F, 1.1F);
                    level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.0F);
                    TheologyBlocks.emitSacredParticles(level, player.blockPosition(), 16, 8, 16);
                }
                case MIRACLE -> {
                    PietyAttachment piety = player.getData(EarthTheology.PIETY.get());
                    long today = TheologyMath.gameDay(level.getDefaultClockTime());
                    piety.setLastMiracleDay(today);
                    double chance = TheologyMath.computeMiracleChance(piety.value());
                    if (level.getRandom().nextDouble() >= chance) {
                        piety.addPiety(-5);
                        player.setData(EarthTheology.PIETY.get(), piety);
                        player.sendSystemMessage(Component.translatable("theology.earth_theology.miracle.failed").withStyle(ChatFormatting.RED));
                        return;
                    }
                    player.setData(EarthTheology.PIETY.get(), piety);
                    BlockPos center = nearAltar.map(SanctumFieldSavedData.AltarRecord::pos).orElse(player.blockPosition());
                    MiracleKind kind = getMiracleKind(stack);
                    switch (kind) {
                        case REVIVAL -> {
                            AABB box = new AABB(center).inflate(32.0D);
                            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box,
                                    e -> e instanceof Player || e instanceof Villager || (e instanceof TamableAnimal ta && ta.isTame()))) {
                                if (le.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= 32.0D * 32.0D) {
                                    le.setHealth(le.getMaxHealth());
                                    List<Holder<net.minecraft.world.effect.MobEffect>> harmful = le.getActiveEffects().stream()
                                            .map(MobEffectInstance::getEffect)
                                            .filter(h -> !h.value().isBeneficial())
                                            .toList();
                                    harmful.forEach(le::removeEffect);
                                }
                            }
                        }
                        case CLEAR_SKY -> {
                            WeatherData wd = level.getWeatherData();
                            wd.setClearWeatherTime(24000);
                            wd.setRainTime(0);
                            wd.setThunderTime(0);
                            wd.setRaining(false);
                            wd.setThundering(false);
                        }
                        case HOLY_LIGHT -> {
                            AABB box = new AABB(center).inflate(48.0D);
                            for (LivingEntity undead : level.getEntitiesOfClass(LivingEntity.class, box, TheologyTags::isUndead)) {
                                if (undead.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= 48.0D * 48.0D) {
                                    undead.hurtServer(level, level.damageSources().magic(), 20.0F);
                                }
                            }
                        }
                    }
                    level.playSound(null, center, EarthTheology.SOUND_MIRACLE_TRIGGER.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.playSound(null, center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
                    TheologyBlocks.emitSacredParticles(level, center, 28, 18, 22);
                    TheologyBlocks.emitSacredParticles(level, player.blockPosition(), 18, 12, 14);
                }
            }
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            Mode mode = getMode(stack);
            if (mode == Mode.MIRACLE) {
                MiracleKind mk = getMiracleKind(stack);
                String key = mk == MiracleKind.REVIVAL
                        ? "theology.earth_theology.beads.mode.miracle"
                        : "theology.earth_theology.beads.miracle." + mk.id();
                builder.accept(Component.translatable(key).withStyle(ChatFormatting.GOLD));
            } else {
                builder.accept(Component.translatable("theology.earth_theology.beads.mode." + mode.id()).withStyle(ChatFormatting.GOLD));
            }
        }

        private static Grade consumeBestHolyWater(ServerPlayer player) {
            int bestSlot = -1;
            Grade bestGrade = null;
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(EarthTheology.HOLY_WATER.get())) {
                    Grade g = s.getOrDefault(EarthTheology.SANCTITY.get(), SanctityComponent.DEFAULT).grade();
                    if (bestGrade == null || g.ordinal() > bestGrade.ordinal()) {
                        bestGrade = g;
                        bestSlot = i;
                    }
                }
            }
            if (bestSlot < 0) {
                return null;
            }
            if (!player.getAbilities().instabuild) {
                inv.getItem(bestSlot).shrink(1);
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (!inv.add(bottle)) {
                    player.drop(bottle, false);
                }
            }
            return bestGrade;
        }

        private static LivingEntity findHealTarget(ServerLevel level, ServerPlayer player) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            AABB search = player.getBoundingBox().expandTowards(look.scale(6.0D)).inflate(1.0D);
            LivingEntity best = null;
            double bestDist = 36.0D;
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, search,
                    e -> e != player && (e instanceof Player || e instanceof Villager || (e instanceof TamableAnimal ta && ta.isTame())))) {
                double d = candidate.distanceToSqr(eye);
                if (d <= bestDist) {
                    bestDist = d;
                    best = candidate;
                }
            }
            return best != null ? best : player;
        }

        private static boolean hasReliquaryWithRelic(ServerLevel level, BlockPos altarPos) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -8; dz <= 8; dz++) {
                        BlockPos p = altarPos.offset(dx, dy, dz);
                        if (p.distSqr(altarPos) <= 64.0D && level.isLoaded(p)) {
                            BlockState st = level.getBlockState(p);
                            if (st.is(EarthTheology.RELIQUARY.get()) && st.getValue(TheologyBlocks.ReliquaryBlock.OCCUPIED)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        private static int countItems(ServerPlayer player, Item item) {
            if (player.getAbilities().instabuild) {
                return 64;
            }
            int total = 0;
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(item)) {
                    total += s.getCount();
                }
            }
            return total;
        }

        private static boolean consumeItemCount(ServerPlayer player, Item item, int count) {
            if (player.getAbilities().instabuild) {
                return true;
            }
            if (countItems(player, item) < count) {
                return false;
            }
            int rem = count;
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize() && rem > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(item)) {
                    int take = Math.min(rem, s.getCount());
                    s.shrink(take);
                    rem -= take;
                }
            }
            return true;
        }

        private static void reportUnopenedLootContainers(ServerLevel level, ServerPlayer player, BlockPos center) {
            int count = 0;
            BlockPos nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (int dx = -48; dx <= 48; dx++) {
                for (int dz = -48; dz <= 48; dz++) {
                    BlockPos col = center.offset(dx, 0, dz);
                    if (!level.isLoaded(col)) {
                        continue;
                    }
                    for (int dy = -24; dy <= 24; dy++) {
                        BlockPos p = center.offset(dx, dy, dz);
                        if (p.distSqr(center) > 48.0D * 48.0D) {
                            continue;
                        }
                        BlockEntity be = level.getBlockEntity(p);
                        boolean hasLoot = false;
                        if (be instanceof RandomizableContainerBlockEntity rc && rc.getLootTable() != null) {
                            hasLoot = true;
                        } else if (be instanceof BrushableBlockEntity bb && bb.getItem().isEmpty()) {
                            hasLoot = true;
                        }
                        if (hasLoot) {
                            count++;
                            double d = p.distSqr(player.blockPosition());
                            if (d < nearestDistSq) {
                                nearestDistSq = d;
                                nearest = p.immutable();
                            }
                        }
                    }
                }
            }
            String dir = nearest == null ? "-" : compassDirection(player.blockPosition(), nearest);
            int dist = nearest == null ? 0 : (int) Math.round(Math.sqrt(nearestDistSq));
            player.sendSystemMessage(Component.translatable("theology.earth_theology.revelation.report", count, dir, dist).withStyle(ChatFormatting.AQUA));
        }

        private static String compassDirection(BlockPos from, BlockPos to) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double angle = Math.toDegrees(Math.atan2(dz, dx));
            if (angle < 0) {
                angle += 360.0D;
            }
            String[] dirs = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
            int idx = (int) Math.round(angle / 45.0D) % 8;
            return dirs[idx];
        }
    }

    /** T-006 holy_water. */
    public static final class HolyWaterItem extends Item {
        public HolyWaterItem(Properties properties) {
            super(properties);
        }

        public static ItemStack create(Grade grade, int sanctumAtMake) {
            ItemStack stack = new ItemStack(EarthTheology.HOLY_WATER.get());
            stack.set(EarthTheology.SANCTITY.get(), new SanctityComponent(grade, sanctumAtMake));
            return stack;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return 32;
        }

        @Override
        public ItemUseAnimation getUseAnimation(ItemStack stack) {
            return ItemUseAnimation.DRINK;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResult.SUCCESS;
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos clicked = context.getClickedPos();
            Player player = context.getPlayer();
            if (player == null || !player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            BlockState state = level.getBlockState(clicked);
            if (state.is(EarthTheology.SHRINE_ALTAR.get()) || state.is(EarthTheology.WARD_STONE.get())) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                ItemStack stack = context.getItemInHand();
                SanctityComponent sanctity = stack.getOrDefault(EarthTheology.SANCTITY.get(), SanctityComponent.DEFAULT);
                float dmg = TheologyMath.computeHolyWaterSplashDamage(sanctity.grade());
                BlockPos targetPos = clicked.above();
                AABB box = new AABB(targetPos).inflate(4.5D);
                for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (TheologyTags.isUndead(entity)) {
                        entity.hurtServer(serverLevel, serverLevel.damageSources().magic(), dmg);
                        entity.igniteForTicks(80);
                        TheologyBlocks.emitSacredParticles(serverLevel, entity.blockPosition(), 6, 4, 6);
                    } else if (entity instanceof Player ally || entity instanceof Villager) {
                        entity.heal(2.0F);
                        entity.addEffect(new MobEffectInstance(EarthTheology.SERENITY, 200, 0, false, true, true));
                    }
                }
                treatEarthHumanInjuries(sp, 0.30D, 2.0D, 20.0D, 8.0D);
                if (!sp.getAbilities().instabuild) {
                    stack.shrink(1);
                    ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                    if (!sp.getInventory().add(bottle)) {
                        sp.drop(bottle, false);
                    }
                }
                sp.swing(context.getHand(), true);
                serverLevel.playSound(null, targetPos, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
                serverLevel.playSound(null, targetPos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.2F);
                serverLevel.playSound(null, targetPos, EarthTheology.SOUND_ALTAR_BLESS.get(), SoundSource.PLAYERS, 0.65F, 1.1F);
                TheologyBlocks.emitSacredParticles(serverLevel, targetPos, 10, 8, 8);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide()) {
                entity.removeEffect(MobEffects.POISON);
                entity.removeEffect(MobEffects.WITHER);
                entity.heal(4.0F);
                if (entity instanceof ServerPlayer sp) {
                    treatEarthHumanInjuries(sp, 0.60D, 4.0D, 40.0D, 12.0D);
                    if (level instanceof ServerLevel serverLevel) {
                        SanctityComponent sanctity = stack.getOrDefault(EarthTheology.SANCTITY.get(), SanctityComponent.DEFAULT);
                        float splashDmg = TheologyMath.computeHolyWaterSplashDamage(sanctity.grade());
                        AABB box = sp.getBoundingBox().inflate(5.5D);
                        for (LivingEntity undead : serverLevel.getEntitiesOfClass(LivingEntity.class, box, TheologyTags::isUndead)) {
                            undead.hurtServer(serverLevel, serverLevel.damageSources().magic(), splashDmg);
                            undead.igniteForTicks(80);
                            TheologyBlocks.emitSacredParticles(serverLevel, undead.blockPosition(), 6, 4, 6);
                        }
                        serverLevel.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.2F);
                        serverLevel.playSound(null, sp.blockPosition(), EarthTheology.SOUND_ALTAR_BLESS.get(), SoundSource.PLAYERS, 0.7F, 1.1F);
                        TheologyBlocks.emitSacredParticles(serverLevel, sp.blockPosition(), 10, 8, 8);
                    }
                }
            }
            if (entity instanceof Player player && !player.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) {
                    return bottle;
                }
                if (!player.getInventory().add(bottle)) {
                    player.drop(bottle, false);
                }
            }
            return stack;
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
            if (!TheologyTags.isUndead(target)) {
                return InteractionResult.PASS;
            }
            if (player.level() instanceof ServerLevel serverLevel) {
                SanctityComponent sanctity = stack.getOrDefault(EarthTheology.SANCTITY.get(), SanctityComponent.DEFAULT);
                float dmg = TheologyMath.computeHolyWaterSplashDamage(sanctity.grade());
                target.hurtServer(serverLevel, serverLevel.damageSources().magic(), dmg);
                target.igniteForTicks(60);
                player.swing(hand, true);
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.15F);
                serverLevel.playSound(null, target.blockPosition(), EarthTheology.SOUND_ALTAR_EXORCISE.get(), SoundSource.PLAYERS, 0.75F, 1.1F);
                TheologyBlocks.emitSacredParticles(serverLevel, target.blockPosition(), 12, 8, 8);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                    if (!player.getInventory().add(bottle)) {
                        player.drop(bottle, false);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            SanctityComponent sanctity = stack.getOrDefault(EarthTheology.SANCTITY.get(), SanctityComponent.DEFAULT);
            builder.accept(Component.translatable("tooltip.earth_theology.holy_water.grade",
                    Component.translatable("grade.earth_lore." + sanctity.grade().id()), sanctity.sanctumAtMake()).withStyle(ChatFormatting.GOLD));
        }
    }

    /** T-009 feast_calendar. */
    public static final class FeastCalendarItem extends Item {
        public FeastCalendarItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                long dayTime = serverLevel.getDefaultClockTime();
                int phase = TheologyMath.moonPhase(dayTime);
                boolean feast = TheologyMath.isFeastDay(dayTime);
                int until = TheologyMath.daysUntilNextFeast(dayTime);
                sp.sendSystemMessage(Component.translatable(
                        "theology.earth_theology.calendar.report",
                        phase,
                        Component.translatable(feast ? "theology.earth_theology.calendar.feast_yes" : "theology.earth_theology.calendar.feast_no"),
                        until
                ).withStyle(ChatFormatting.GOLD));
                sp.swing(hand, true);
                serverLevel.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.05F);
                serverLevel.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.2F);
                TheologyBlocks.emitSacredParticles(serverLevel, sp.blockPosition(), 4, 2, 4);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** T-011 relic. */
    public static final class RelicItem extends Item {
        public RelicItem(Properties properties) {
            super(properties);
        }

        public static ItemStack create(String pilgrimName, int sites) {
            ItemStack stack = new ItemStack(EarthTheology.RELIC.get());
            int clampedSites = Math.max(3, Math.min(8, sites));
            String safeName = pilgrimName == null ? "" : (pilgrimName.length() > 32 ? pilgrimName.substring(0, 32) : pilgrimName);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putString("pilgrim_name", safeName);
                tag.putInt("sites", clampedSites);
            });
            return stack;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                sp.addEffect(new MobEffectInstance(EarthTheology.SERENITY, 400, 0, false, true, true));
                sp.addEffect(new MobEffectInstance(EarthTheology.BLESSED, 200, 0, false, true, true));
                sp.getCooldowns().addCooldown(stack, 200);
                sp.swing(hand, true);
                serverLevel.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
                serverLevel.playSound(null, sp.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.65F, 1.25F);
                TheologyBlocks.emitSacredParticles(serverLevel, sp.blockPosition(), 8, 6, 8);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            String name = tag.getStringOr("pilgrim_name", "");
            int sites = tag.getIntOr("sites", 3);
            if (!name.isEmpty()) {
                builder.accept(Component.translatable("tooltip.earth_theology.relic.pilgrim", name).withStyle(ChatFormatting.GRAY));
            }
            builder.accept(Component.translatable("tooltip.earth_theology.relic.sites", sites).withStyle(ChatFormatting.GOLD));
        }
    }

    /** T-014 ward_charm (durability 64). */
    public static final class WardCharmItem extends Item {
        public WardCharmItem(Properties properties) {
            super(properties.durability(64));
        }
    }

    /** T-016 pilgrim_staff. */
    public static final class PilgrimStaffItem extends Item {
        private static final Identifier STAFF_ATTACK_DAMAGE_ID = Identifier.withDefaultNamespace("base_attack_damage");
        private static final Identifier STAFF_ATTACK_SPEED_ID = Identifier.withDefaultNamespace("base_attack_speed");

        public PilgrimStaffItem(Properties properties) {
            super(properties.attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE,
                            new AttributeModifier(STAFF_ATTACK_DAMAGE_ID, 6.5D, AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED,
                            new AttributeModifier(STAFF_ATTACK_SPEED_ID, -2.5D, AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .build()));
        }

        @Override
        public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            super.hurtEnemy(stack, target, attacker);
            if (target.level() instanceof ServerLevel serverLevel) {
                float bonus = TheologyTags.isUndead(target) ? 6.0F : 2.5F;
                target.hurtServer(serverLevel, serverLevel.damageSources().magic(), bonus);
                if (TheologyTags.isUndead(target)) {
                    target.igniteForSeconds(4);
                }
                Vec3 knock = target.position().subtract(attacker.position());
                if (knock.lengthSqr() > 0.001D) {
                    Vec3 push = knock.normalize().scale(0.6D);
                    target.push(push.x, 0.25D, push.z);
                }
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.25F);
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.2F);
                TheologyBlocks.emitSacredParticles(serverLevel, target.blockPosition(), 10, 6, 8);
            }
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
            if (player.getCooldowns().isOnCooldown(stack)) {
                return InteractionResult.FAIL;
            }
            if (target instanceof Villager || target instanceof Player || (target instanceof TamableAnimal ta && ta.isTame())) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    target.addEffect(new MobEffectInstance(EarthTheology.BLESSED, 400, 0, false, true, true));
                    target.addEffect(new MobEffectInstance(EarthTheology.SERENITY, 400, 0, false, true, true));
                    player.getCooldowns().addCooldown(stack, 80);
                    player.swing(hand, true);
                    serverLevel.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.2F);
                    serverLevel.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.25F);
                    TheologyBlocks.emitSacredParticles(serverLevel, target.blockPosition(), 8, 6, 6);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        public static Optional<BlockPos> getStartPos(ItemStack stack) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.getBooleanOr("has_start", false)) {
                return Optional.empty();
            }
            return Optional.of(BlockPos.of(tag.getLongOr("start_pos", 0L)));
        }

        public static List<String> getStamps(ItemStack stack) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            String raw = tag.getStringOr("stamps", "");
            if (raw.isEmpty()) {
                return List.of();
            }
            return new ArrayList<>(List.of(raw.split(",")));
        }

        public static void setPilgrimageState(ItemStack stack, BlockPos startPos, List<String> stamps) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                if (startPos == null) {
                    tag.putBoolean("has_start", false);
                    tag.remove("start_pos");
                    tag.putString("stamps", "");
                } else {
                    tag.putBoolean("has_start", true);
                    tag.putLong("start_pos", startPos.asLong());
                    tag.putString("stamps", String.join(",", stamps.subList(0, Math.min(8, stamps.size()))));
                }
            });
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Level level = context.getLevel();
            BlockPos clicked = context.getClickedPos();
            Player player = context.getPlayer();
            ItemStack stack = context.getItemInHand();
            if (player == null || !level.getBlockState(clicked).is(EarthTheology.SHRINE_ALTAR.get())) {
                return InteractionResult.PASS;
            }
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            return handleAltarInteraction(serverLevel, clicked, sp, context.getHand(), stack);
        }

        public static InteractionResult handleAltarInteraction(
                ServerLevel serverLevel,
                BlockPos clicked,
                ServerPlayer sp,
                InteractionHand hand,
                ItemStack stack
        ) {
            Optional<BlockPos> startOpt = getStartPos(stack);
            if (startOpt.isEmpty()) {
                setPilgrimageState(stack, clicked, List.of());
                sp.swing(hand, true);
                sp.sendOverlayMessage(Component.translatable("theology.earth_theology.pilgrim.started").withStyle(ChatFormatting.GOLD));
                serverLevel.playSound(null, clicked, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.85F, 1.15F);
                serverLevel.playSound(null, clicked, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
                TheologyBlocks.emitSacredParticles(serverLevel, clicked, 10, 6, 8);
                return InteractionResult.SUCCESS;
            }
            if (startOpt.get().equals(clicked)) {
                List<String> stamps = getStamps(stack);
                if (stamps.size() < TheologyMath.PILGRIM_STAMPS) {
                    sp.sendOverlayMessage(TheologyBlocks.failMsg("not_enough_stamps"));
                    return InteractionResult.FAIL;
                }
                ItemStack relic = RelicItem.create(sp.getScoreboardName(), stamps.size());
                if (!sp.getInventory().add(relic)) {
                    sp.drop(relic, false);
                }
                PietyAttachment piety = sp.getData(EarthTheology.PIETY.get());
                piety.addPiety(10);
                sp.setData(EarthTheology.PIETY.get(), piety);
                setPilgrimageState(stack, null, List.of());
                sp.swing(hand, true);
                serverLevel.playSound(null, clicked, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
                serverLevel.playSound(null, clicked, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.9F, 1.1F);
                serverLevel.playSound(null, clicked, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 1.15F);
                TheologyBlocks.emitSacredParticles(serverLevel, clicked, 20, 14, 16);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                setPilgrimageState(stack, null, List.of());
                sp.swing(hand, true);
                sp.sendOverlayMessage(Component.translatable("theology.earth_theology.pilgrim.abandoned").withStyle(ChatFormatting.YELLOW));
                serverLevel.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 0.95F);
                TheologyBlocks.emitSacredParticles(serverLevel, sp.blockPosition(), 4, 2, 4);
                return InteractionResult.SUCCESS;
            }
            Optional<BlockPos> startOpt = getStartPos(stack);
            if (startOpt.isEmpty()) {
                List<LivingEntity> nearbyUndead = serverLevel.getEntitiesOfClass(
                        LivingEntity.class, sp.getBoundingBox().inflate(6.5D), TheologyTags::isUndead);
                if (!nearbyUndead.isEmpty() && !sp.getCooldowns().isOnCooldown(stack)) {
                    for (LivingEntity undead : nearbyUndead) {
                        undead.hurtServer(serverLevel, serverLevel.damageSources().magic(), 8.0F);
                        undead.igniteForTicks(100);
                        Vec3 knock = undead.position().subtract(sp.position());
                        if (knock.lengthSqr() > 0.001D) {
                            Vec3 push = knock.normalize().scale(0.75D);
                            undead.push(push.x, 0.3D, push.z);
                        }
                        TheologyBlocks.emitSacredParticles(serverLevel, undead.blockPosition(), 8, 4, 6);
                    }
                    sp.addEffect(new MobEffectInstance(EarthTheology.BLESSED, 240, 0, false, true, true));
                    sp.getCooldowns().addCooldown(stack, 100);
                    sp.swing(hand, true);
                    serverLevel.playSound(null, sp.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.9F, 1.1F);
                    serverLevel.playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.2F);
                    serverLevel.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.15F);
                    TheologyBlocks.emitSacredParticles(serverLevel, sp.blockPosition(), 16, 10, 12);
                    return InteractionResult.SUCCESS;
                }
                sp.sendOverlayMessage(TheologyBlocks.failMsg("not_started"));
                return InteractionResult.FAIL;
            }
            BlockPos start = startOpt.get();
            BlockPos cur = sp.blockPosition();
            if (!TheologyMath.meetsPilgrimDistance(start.getX(), start.getZ(), cur.getX(), cur.getZ())) {
                sp.sendOverlayMessage(TheologyBlocks.failMsg("too_close"));
                return InteractionResult.FAIL;
            }
            StructureStart structureStart = serverLevel.structureManager().getStructureWithPieceAt(cur, TheologyTags.PILGRIMAGE_SITES);
            if (!structureStart.isValid()) {
                sp.sendOverlayMessage(TheologyBlocks.failMsg("not_in_site"));
                return InteractionResult.FAIL;
            }
            var structReg = serverLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Identifier structId = structReg.getKey(structureStart.getStructure());
            if (structId == null) {
                sp.sendOverlayMessage(TheologyBlocks.failMsg("not_in_site"));
                return InteractionResult.FAIL;
            }
            List<String> stamps = new ArrayList<>(getStamps(stack));
            if (stamps.contains(structId.toString())) {
                sp.sendOverlayMessage(TheologyBlocks.failMsg("already_stamped"));
                return InteractionResult.FAIL;
            }
            if (stamps.size() < 8) {
                stamps.add(structId.toString());
            }
            setPilgrimageState(stack, start, stamps);
            sp.swing(hand, true);
            serverLevel.playSound(null, cur, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
            serverLevel.playSound(null, cur, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85F, 1.15F);
            serverLevel.playSound(null, cur, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.65F, 1.2F);
            TheologyBlocks.emitSacredParticles(serverLevel, cur, 14, 8, 12);
            sp.sendOverlayMessage(Component.translatable("theology.earth_theology.pilgrim.stamped", stamps.size()).withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
            Optional<BlockPos> startOpt = getStartPos(stack);
            if (startOpt.isPresent()) {
                BlockPos p = startOpt.get();
                List<String> stamps = getStamps(stack);
                builder.accept(Component.translatable("tooltip.earth_theology.pilgrim_staff.status",
                        p.getX(), p.getY(), p.getZ(), stamps.size(), TheologyMath.PILGRIM_STAMPS).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** T-022 scripture_scroll. */
    public static final class ScriptureScrollItem extends LiteratureItem {
        public ScriptureScrollItem(Properties properties) {
            super(properties, false);
        }

        public static ItemStack create(Identifier node, String authorName) {
            ItemStack stack = new ItemStack(EarthTheology.SCRIPTURE_SCROLL.get());
            stack.set(com.xxsx.earthlore.registry.LoreContent.LORE_TEXT.get(),
                    new com.xxsx.earthlore.data.LoreText(
                            List.of(node),
                            TheologyLoreIntegration.DISCIPLINE_THE,
                            "earth_theology:scripture_scroll",
                            Optional.empty(),
                            authorName == null ? "" : authorName,
                            0
                    ));
            return stack;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            InteractionResult res = super.use(level, player, hand);
            if (res.consumesAction()) {
                if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                    serverLevel.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.75F, 1.05F);
                    serverLevel.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.55F, 1.2F);
                    TheologyBlocks.emitSacredParticles(serverLevel, sp.blockPosition(), 4, 2, 4);
                }
                return InteractionResult.SUCCESS;
            }
            return res;
        }
    }
}

