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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, TheologyEvents::onRightClickBlock);
        bus.addListener(PlayerInteractEvent.EntityInteract.class, TheologyEvents::onEntityInteract);
        bus.addListener(PlayerTickEvent.Post.class, TheologyEvents::onPlayerTickPost);
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
                    villager.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.REGENERATION, 100, 0, true, false, true));
                    attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 0, false, true, true));
                    attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.GLOWING, 120, 0, false, true, true));
                    for (net.minecraft.world.entity.animal.golem.IronGolem golem : vLevel.getEntitiesOfClass(
                            net.minecraft.world.entity.animal.golem.IronGolem.class,
                            villager.getBoundingBox().inflate(24.0D))) {
                        if (golem.isAlive() && !golem.isPlayerCreated()) {
                            golem.setTarget(attacker);
                        }
                    }
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
                villager.spawnAtLocation(serverLevel, TheologyItems.HolyWaterItem.create(com.xxsx.earthlore.api.Grade.COMMON, 20));
                villager.spawnAtLocation(serverLevel, new ItemStack(EarthTheology.PRAYER_CANDLE_ITEM.get(), 2));
            } else if (prof.is(EarthTheology.SCRIBE.getId())) {
                villager.spawnAtLocation(serverLevel, new ItemStack(EarthTheology.BLANK_SCROLL.get(), 2));
                villager.spawnAtLocation(serverLevel,
                        TheologyItems.ScriptureScrollItem.create(TheologyLoreIntegration.NODE_PRAYER, "Scribe"));
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

    /** Sacred bell resonance when ringing a bell (especially near a sanctum or temple). */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (!state.is(Blocks.BELL) && !(state.getBlock() instanceof BellBlock)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
        Optional<SanctumFieldSavedData.AltarRecord> nearAltar = field.nearestAltar(pos, 12);
        int sanctum = nearAltar.map(r -> field.rescanAltar(serverLevel, r).total()).orElse(0);

        serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.85F, 1.15F);
        if (sanctum >= 20) {
            serverLevel.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.55F, 1.25F);
        }
        TheologyBlocks.emitSacredParticles(serverLevel, pos, sanctum >= 10 ? 12 : 6, sanctum >= 10 ? 8 : 3, sanctum >= 10 ? 10 : 5);

        if (sanctum >= 10) {
            AABB allyBox = new AABB(pos).inflate(12.0D);
            for (LivingEntity ally : serverLevel.getEntitiesOfClass(LivingEntity.class, allyBox,
                    e -> e instanceof Player || e instanceof Villager)) {
                ally.addEffect(new MobEffectInstance(EarthTheology.SERENITY, 400, 0, false, true, true));
                if (sanctum >= 30) {
                    ally.addEffect(new MobEffectInstance(EarthTheology.BLESSED, 240, 0, false, true, true));
                }
            }
            AABB undeadBox = new AABB(pos).inflate(16.0D);
            for (LivingEntity undead : serverLevel.getEntitiesOfClass(LivingEntity.class, undeadBox, TheologyTags::isUndead)) {
                undead.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, true, true));
                TheologyBlocks.emitSacredParticles(serverLevel, undead.blockPosition(), 4, 2, 4);
            }
        }
    }

    /** Priest & Scribe interactive ministry loop (blessing, exorcism, injury healing, scroll inscription). */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager) || !villager.isAlive()) {
            return;
        }
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack held = player.getItemInHand(hand);
        var prof = villager.getVillagerData().profession();

        if (prof.is(EarthTheology.PRIEST.getId())) {
            boolean ritualTrigger = player.isShiftKeyDown()
                    || held.is(EarthTheology.INCENSE.get())
                    || held.is(EarthTheology.PRAYER_BEADS.get());
            if (!ritualTrigger) {
                return;
            }
            if (event.getLevel() instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                long now = serverLevel.getGameTime();
                long last = sp.getPersistentData().getLongOr("EarthTheologyPriestMinistryTick", -1000L);
                boolean offeredIncense = held.is(EarthTheology.INCENSE.get());
                if (!offeredIncense && now - last < 160L) {
                    sp.sendOverlayMessage(TheologyBlocks.failMsg("cooldown"));
                    event.setCancellationResult(InteractionResult.FAIL);
                    event.setCanceled(true);
                    return;
                }
                sp.getPersistentData().putLong("EarthTheologyPriestMinistryTick", now);
                if (offeredIncense && !sp.getAbilities().instabuild) {
                    held.shrink(1);
                }
                performPriestMinistry(serverLevel, villager, sp, hand, offeredIncense);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } else if (prof.is(EarthTheology.SCRIBE.getId())) {
            if (!player.isShiftKeyDown() || !held.is(EarthTheology.BLANK_SCROLL.get())) {
                return;
            }
            if (event.getLevel() instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
                if (!sp.getAbilities().instabuild) {
                    held.shrink(1);
                }
                var node = !com.xxsx.earthlore.api.LoreApi.knows(sp, TheologyLoreIntegration.NODE_PRAYER)
                        ? TheologyLoreIntegration.NODE_PRAYER
                        : (!com.xxsx.earthlore.api.LoreApi.knows(sp, TheologyLoreIntegration.NODE_SANCTUM)
                                ? TheologyLoreIntegration.NODE_SANCTUM
                                : TheologyLoreIntegration.NODE_SCRIPTURES);
                ItemStack scroll = TheologyItems.ScriptureScrollItem.create(node, "Scribe");
                if (!sp.getInventory().add(scroll)) {
                    sp.drop(scroll, false);
                }
                villager.swing(InteractionHand.MAIN_HAND);
                sp.swing(hand, true);
                serverLevel.playSound(null, villager.blockPosition(), EarthTheology.SOUND_WORK_SCRIBE.get(), SoundSource.NEUTRAL, 0.9F, 1.05F);
                serverLevel.playSound(null, villager.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.8F, 1.1F);
                serverLevel.playSound(null, villager.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.2F);
                TheologyBlocks.emitSacredParticles(serverLevel, villager.blockPosition(), 8, 4, 8);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static void performPriestMinistry(
            ServerLevel serverLevel,
            Villager priest,
            ServerPlayer player,
            InteractionHand hand,
            boolean offeredIncense
    ) {
        int vLevel = Math.max(1, priest.getVillagerData().level());
        int blessTicks = TheologyMath.computePriestBlessTicks(vLevel);
        if (offeredIncense) {
            blessTicks = (int) Math.round(blessTicks * 1.5D);
        }
        int serenityTicks = 800 + vLevel * 200;
        float healAmount = TheologyMath.computePriestHealAmount(vLevel);
        float exorcismDmg = TheologyMath.computePriestExorcismDamage(vLevel);

        // 1. Heal player injuries & status effects
        player.heal(healAmount);
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.DARKNESS);
        TheologyItems.treatEarthHumanInjuries(player, 0.65D, 4.0D, 35.0D, 15.0D);

        // 2. Bestow Blessing & Serenity Grace to player and nearby allies
        AABB allyBox = priest.getBoundingBox().inflate(6.0D);
        for (LivingEntity ally : serverLevel.getEntitiesOfClass(LivingEntity.class, allyBox,
                e -> e instanceof Player || e instanceof Villager || (e instanceof TamableAnimal ta && ta.isTame()))) {
            ally.addEffect(new MobEffectInstance(EarthTheology.BLESSED, blessTicks, 0, false, true, true));
            ally.addEffect(new MobEffectInstance(EarthTheology.SERENITY, serenityTicks, 0, false, true, true));
        }

        // 3. Exorcise nearby undead and purify sanctum desecration
        boolean purgedUndead = false;
        AABB undeadBox = priest.getBoundingBox().inflate(12.0D);
        for (LivingEntity undead : serverLevel.getEntitiesOfClass(LivingEntity.class, undeadBox, TheologyTags::isUndead)) {
            undead.hurtServer(serverLevel, serverLevel.damageSources().magic(), exorcismDmg);
            undead.igniteForTicks(100);
            Vec3 knock = undead.position().subtract(priest.position());
            if (knock.lengthSqr() > 0.001D) {
                Vec3 push = knock.normalize().scale(0.65D);
                undead.push(push.x, 0.25D, push.z);
            }
            TheologyBlocks.emitSacredParticles(serverLevel, undead.blockPosition(), 8, 4, 6);
            purgedUndead = true;
        }
        SanctumFieldSavedData field = SanctumFieldSavedData.get(serverLevel);
        field.nearestAltar(priest.blockPosition(), 12).ifPresent(altar -> field.clearDesecrationAtAltar(serverLevel, altar.pos()));

        if (offeredIncense) {
            PietyAttachment piety = player.getData(EarthTheology.PIETY.get());
            piety.addPiety(1);
            player.setData(EarthTheology.PIETY.get(), piety);
        }

        priest.swing(InteractionHand.MAIN_HAND);
        player.swing(hand, true);
        player.sendOverlayMessage(Component.translatable("theology.earth_theology.priest.ministry").withStyle(ChatFormatting.GOLD));
        serverLevel.playSound(null, priest.blockPosition(), EarthTheology.SOUND_ALTAR_BLESS.get(), SoundSource.NEUTRAL, 0.9F, 1.05F);
        serverLevel.playSound(null, priest.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.8F, 1.15F);
        serverLevel.playSound(null, priest.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.85F, 1.2F);
        if (purgedUndead) {
            serverLevel.playSound(null, priest.blockPosition(), EarthTheology.SOUND_ALTAR_EXORCISE.get(), SoundSource.NEUTRAL, 0.85F, 1.1F);
        }
        TheologyBlocks.emitSacredParticles(serverLevel, priest.blockPosition(), 12, 8, 10);
        TheologyBlocks.emitSacredParticles(serverLevel, player.blockPosition(), 12, 8, 10);
    }

    /** Periodic autonomous Priest ministry in sanctums (novice exorcism & healing support). */
    private static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (player.tickCount % 80 != 0) {
            return;
        }
        AABB search = player.getBoundingBox().inflate(10.0D);
        List<Villager> priests = serverLevel.getEntitiesOfClass(Villager.class, search,
                v -> v.isAlive() && v.getVillagerData().profession().is(EarthTheology.PRIEST.getId()));
        if (priests.isEmpty()) {
            return;
        }
        Villager priest = priests.get(0);
        int vLevel = Math.max(1, priest.getVillagerData().level());

        // Autonomous undead exorcism around the Priest
        List<LivingEntity> undeadList = serverLevel.getEntitiesOfClass(
                LivingEntity.class, priest.getBoundingBox().inflate(10.0D), TheologyTags::isUndead);
        if (!undeadList.isEmpty()) {
            float dmg = TheologyMath.computePriestExorcismDamage(vLevel) * 0.75F;
            for (LivingEntity undead : undeadList) {
                undead.hurtServer(serverLevel, serverLevel.damageSources().magic(), dmg);
                undead.igniteForTicks(80);
                TheologyBlocks.emitSacredParticles(serverLevel, undead.blockPosition(), 6, 3, 5);
            }
            priest.swing(InteractionHand.MAIN_HAND);
            serverLevel.playSound(null, priest.blockPosition(), EarthTheology.SOUND_ALTAR_EXORCISE.get(), SoundSource.NEUTRAL, 0.75F, 1.15F);
            serverLevel.playSound(null, priest.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.7F, 1.2F);
            TheologyBlocks.emitSacredParticles(serverLevel, priest.blockPosition(), 8, 4, 6);
        }

        // Autonomous novice blessing & healing when player is injured or inside a sanctum
        long now = serverLevel.getGameTime();
        long lastAuto = priest.getPersistentData().getLongOr("EarthTheologyAutoMinistryTick", -1000L);
        boolean needsHeal = player.getHealth() < player.getMaxHealth() * 0.85F
                || player.hasEffect(MobEffects.POISON)
                || player.hasEffect(MobEffects.WITHER);
        boolean inSanctum = SanctumFieldSavedData.get(serverLevel).getSanctumAt(serverLevel, priest.blockPosition()) >= 10;
        if (now - lastAuto >= 300L && (needsHeal || (inSanctum && !player.hasEffect(EarthTheology.SERENITY)))) {
            priest.getPersistentData().putLong("EarthTheologyAutoMinistryTick", now);
            player.heal(3.0F);
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.WITHER);
            TheologyItems.treatEarthHumanInjuries(player, 0.35D, 2.0D, 20.0D, 8.0D);
            player.addEffect(new MobEffectInstance(EarthTheology.SERENITY, 400, 0, false, true, true));
            player.addEffect(new MobEffectInstance(EarthTheology.BLESSED, 400, 0, false, true, true));
            priest.swing(InteractionHand.MAIN_HAND);
            serverLevel.playSound(null, priest.blockPosition(), EarthTheology.SOUND_ALTAR_BLESS.get(), SoundSource.NEUTRAL, 0.7F, 1.15F);
            serverLevel.playSound(null, priest.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.65F, 1.2F);
            TheologyBlocks.emitSacredParticles(serverLevel, priest.blockPosition(), 6, 4, 6);
            TheologyBlocks.emitSacredParticles(serverLevel, player.blockPosition(), 6, 4, 6);
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

