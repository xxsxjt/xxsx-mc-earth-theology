package com.xxsx.earththeology;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Gameplay event handlers and /theology command for Earth: Theology (18 册 §3–§10).
 */
public final class TheologyEvents {
    private static final Set<UUID> COUNTED_UNDEAD = new HashSet<>();

    private TheologyEvents() {
    }

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterCommandsEvent.class, event -> registerCommands(event.getDispatcher()));
        bus.addListener(LivingDamageEvent.Pre.class, TheologyEvents::onLivingDamagePre);
        bus.addListener(LivingDamageEvent.Post.class, TheologyEvents::onLivingDamagePost);
        bus.addListener(LivingDeathEvent.class, TheologyEvents::onLivingDeath);
        bus.addListener(EntityJoinLevelEvent.class, TheologyEvents::onEntityJoinLevel);
        bus.addListener(MobSpawnEvent.PositionCheck.class, TheologyEvents::onSpawnPositionCheck);
        bus.addListener(LivingChangeTargetEvent.class, TheologyEvents::onLivingChangeTarget);
        bus.addListener(PlayerXpEvent.XpChange.class, TheologyEvents::onXpChange);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("theology")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("piety")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                                .executes(ctx -> queryPiety(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("n", IntegerArgumentType.integer(0, TheologyMath.PIETY_MAX))
                                                .executes(ctx -> setPiety(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "n")
                                                )))))));
    }

    private static int queryPiety(CommandSourceStack source, String playerName) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(TheologyBlocks.failMsg("theology_no_player"));
            return 0;
        }
        int val = target.getData(EarthTheology.PIETY.get()).value();
        source.sendSuccess(() -> Component.translatable("theology.earth_theology.command.piety", target.getScoreboardName(), val), false);
        return val;
    }

    private static int setPiety(CommandSourceStack source, String playerName, int n) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(TheologyBlocks.failMsg("theology_no_player"));
            return 0;
        }
        PietyAttachment piety = target.getData(EarthTheology.PIETY.get());
        piety.setValue(n);
        target.setData(EarthTheology.PIETY.get(), piety);
        source.sendSuccess(() -> Component.translatable("theology.earth_theology.command.piety_set", target.getScoreboardName(), piety.value()), true);
        return 1;
    }

    private static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        float dmg = event.getNewDamage();
        if (dmg <= 0.0F) {
            return;
        }
        // T-BLESS-01 blessed effect: incoming damage -10%
        if (victim.hasEffect(EarthTheology.BLESSED)) {
            dmg *= 0.90F;
        }
        // T-014 ward_charm: when in player inventory, damage from #minecraft:undead -20% and durability -1
        Entity attacker = event.getSource().getEntity();
        if (victim instanceof ServerPlayer player && TheologyTags.isUndead(attacker)) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(EarthTheology.WARD_CHARM.get())) {
                    dmg *= 0.80F;
                    int nextDmg = s.getDamageValue() + 1;
                    if (nextDmg >= s.getMaxDamage()) {
                        s.shrink(1);
                    } else {
                        s.setDamageValue(nextDmg);
                    }
                    player.level().playSound(null, player.blockPosition(), EarthTheology.SOUND_WARD_CHARM_TRIGGER.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
                    break;
                }
            }
        }
        event.setNewDamage(dmg);
    }

    private static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        // T-001: taking damage interrupts active prayer
        if (victim instanceof ServerPlayer player && player.isUsingItem() && player.getUseItem().is(EarthTheology.PRAYER_BEADS.get())) {
            player.stopUsingItem();
        }
        // Clergy (Priest / Scribe) divine smite counter-attack when attacked
        if (victim instanceof Villager villager && victim.isAlive() && victim.level() instanceof ServerLevel vLevel) {
            var prof = villager.getVillagerData().profession();
            if (prof.is(EarthTheology.PRIEST.getId()) || prof.is(EarthTheology.SCRIBE.getId())) {
                Entity src = event.getSource().getEntity();
                if (src instanceof LivingEntity attacker && attacker != villager) {
                    villager.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.RESISTANCE, 80, 0, true, false, true));
                    attacker.hurtServer(vLevel, vLevel.damageSources().magic(), 5.0F);
                    attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 0, false, true, true));
                    vLevel.playSound(null, villager.blockPosition(), EarthTheology.SOUND_ALTAR_EXORCISE.get(), SoundSource.NEUTRAL, 0.8F, 1.2F);
                }
            }
        }
        // T-PIETY-01 rule 2: attacking villager or tamed animal at sanctum >= 1 reduces piety by 5
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.level() instanceof ServerLevel serverLevel) {
            if (victim instanceof Villager || (victim instanceof TamableAnimal ta && ta.isTame())) {
                int sanctum = SanctumFieldSavedData.get(serverLevel).getSanctumAt(serverLevel, victim.blockPosition());
                if (sanctum >= 1) {
                    PietyAttachment piety = attacker.getData(EarthTheology.PIETY.get());
                    piety.addPiety(-5);
                    attacker.setData(EarthTheology.PIETY.get(), piety);
                }
            }
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        boolean killedByPlayerOrUndead = killer instanceof Player || TheologyTags.isUndead(killer);
        boolean protectedVictim = victim instanceof Villager || victim instanceof Player || (victim instanceof TamableAnimal ta && ta.isTame());
        if (killedByPlayerOrUndead && protectedVictim) {
            SanctumFieldSavedData.get(serverLevel).addDesecrationNearAltar(serverLevel, victim.blockPosition(), 10.0D);
        }
        if (victim instanceof Villager villager) {
            var prof = villager.getVillagerData().profession();
            if (prof.is(EarthTheology.PRIEST.getId())) {
                villager.spawnAtLocation(serverLevel, new ItemStack(EarthTheology.INCENSE.get(), 2));
                villager.spawnAtLocation(serverLevel, TheologyItems.HolyWaterItem.create(com.xxsx.earthlore.api.Grade.COMMON, 15));
            } else if (prof.is(EarthTheology.SCRIBE.getId())) {
                villager.spawnAtLocation(serverLevel, new ItemStack(EarthTheology.BLANK_SCROLL.get(), 2));
            }
        }
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity entity = event.getEntity();
        if (TheologyTags.isUndead(entity) && COUNTED_UNDEAD.add(entity.getUUID())) {
            SanctumFieldSavedData.get(serverLevel).addDesecrationNearAltar(serverLevel, entity.blockPosition(), 2.0D);
        }
    }

    private static void onSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != EntitySpawnReason.NATURAL && event.getSpawnType() != EntitySpawnReason.CHUNK_GENERATION) {
            return;
        }
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (isProtectedFromHostileSpawn(level, pos)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    public static boolean isProtectedFromHostileSpawn(ServerLevel level, BlockPos pos) {
        SanctumFieldSavedData field = SanctumFieldSavedData.get(level);
        Optional<SanctumFieldSavedData.AltarRecord> near = field.nearestAltar(pos, TheologyMath.WARD_RADIUS);
        if (near.isEmpty()) {
            return false;
        }
        SanctumFieldSavedData.AltarRecord altar = near.get();
        if (altar.temple() && pos.distSqr(altar.pos()) <= 16.0D * 16.0D) {
            return true;
        }
        BlockPos center = altar.pos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (p.distSqr(center) <= 64.0D && level.isLoaded(p)) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be instanceof TheologyBlockEntities.WardStoneBlockEntity wardBe && wardBe.active()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Phantom) || !(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level() instanceof ServerLevel serverLevel && isProtectedFromHostileSpawn(serverLevel, player.blockPosition())) {
            event.setCanceled(true);
        }
    }

    private static void onXpChange(PlayerXpEvent.XpChange event) {
        if (event.getAmount() > 0 && event.getEntity().hasEffect(EarthTheology.BLESSED)) {
            int boosted = (int) Math.round(event.getAmount() * 1.10D);
            event.setAmount(Math.max(event.getAmount() + 1, boosted));
        }
    }

    /** TB-01 priest and TB-02 scribe custom trades (called from TheologyVillagerMixin on updateTrades). */
    public static void addProfessionTrades(Villager villager, int level) {
        var profHolder = villager.getVillagerData().profession();
        MerchantOffers offers = villager.getOffers();
        if (profHolder.is(EarthTheology.PRIEST.getId())) {
            addPriestTrades(offers, level);
        } else if (profHolder.is(EarthTheology.SCRIBE.getId())) {
            addScribeTrades(offers, level);
        }
    }

    public static void addPriestTrades(MerchantOffers offers, int level) {
        switch (level) {
            case 1 -> {
                offers.add(new MerchantOffer(new ItemCost(Items.CANDLE, 4), new ItemStack(Items.EMERALD, 1), 16, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(EarthTheology.INCENSE.get(), 4), 16, 2, 0.05F));
            }
            case 2 -> {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(EarthTheology.PRAYER_BEADS.get(), 1), 12, 5, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.GOLD_NUGGET, 8), new ItemStack(Items.EMERALD, 1), 16, 5, 0.05F));
            }
            case 3 -> {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(EarthTheology.PRAYER_CANDLE_ITEM.get(), 4), 12, 10, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(EarthTheology.FEAST_CALENDAR.get(), 1), 12, 10, 0.05F));
            }
            case 4 -> {
                ItemStack hw = TheologyItems.HolyWaterItem.create(com.xxsx.earthlore.api.Grade.COMMON, 20);
                hw.setCount(2);
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), hw, 12, 15, 0.05F));
            }
            case 5 -> {
                ItemStack relic = TheologyItems.RelicItem.create("Priest", 3);
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 32), Optional.of(new ItemCost(Items.GOLD_BLOCK, 1)), relic, 1, 30, 0.05F));
            }
        }
    }

    public static void addScribeTrades(MerchantOffers offers, int level) {
        switch (level) {
            case 1 -> {
                offers.add(new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD, 1), 16, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(EarthTheology.BLANK_SCROLL.get(), 4), 16, 2, 0.05F));
            }
            case 2 -> offers.add(new MerchantOffer(new ItemCost(Items.INK_SAC, 5), new ItemStack(Items.EMERALD, 1), 16, 5, 0.05F));
            case 3 -> offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(EarthTheology.SCRIPTURE_LECTERN_ITEM.get(), 1), 12, 10, 0.05F));
            case 4 -> offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(EarthTheology.FEAST_CALENDAR.get(), 1), 12, 15, 0.05F));
            case 5 -> offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(com.xxsx.earthlore.registry.LoreContent.BLANK_NOTES.get(), 8), 12, 20, 0.05F));
        }
    }
}

