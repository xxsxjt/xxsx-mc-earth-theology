package com.xxsx.earththeology;

import com.google.common.collect.ImmutableSet;
import com.xxsx.earththeology.client.EarthTheologyClient;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.EnumMap;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(EarthTheology.MODID)
public final class EarthTheology {
    public static final String MODID = "earth_theology";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ---- Sounds (06 册 §5.1) ----
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_BEADS_PRAY =
            SOUNDS.register("item.prayer_beads.pray", () -> SoundEvent.createVariableRangeEvent(id("item.prayer_beads.pray")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_ALTAR_OFFER =
            SOUNDS.register("block.shrine_altar.offer", () -> SoundEvent.createVariableRangeEvent(id("block.shrine_altar.offer")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_ALTAR_BLESS =
            SOUNDS.register("block.shrine_altar.bless", () -> SoundEvent.createVariableRangeEvent(id("block.shrine_altar.bless")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_ALTAR_EXORCISE =
            SOUNDS.register("block.shrine_altar.exorcise", () -> SoundEvent.createVariableRangeEvent(id("block.shrine_altar.exorcise")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_CENSER_BURN =
            SOUNDS.register("block.censer.burn", () -> SoundEvent.createVariableRangeEvent(id("block.censer.burn")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_TEMPLE_FORMED =
            SOUNDS.register("block.temple_keystone.formed", () -> SoundEvent.createVariableRangeEvent(id("block.temple_keystone.formed")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_WARD_CHARM_TRIGGER =
            SOUNDS.register("item.ward_charm.trigger", () -> SoundEvent.createVariableRangeEvent(id("item.ward_charm.trigger")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_MIRACLE_TRIGGER =
            SOUNDS.register("event.miracle.trigger", () -> SoundEvent.createVariableRangeEvent(id("event.miracle.trigger")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_WORK_PRIEST =
            SOUNDS.register("entity.villager.work_priest", () -> SoundEvent.createVariableRangeEvent(id("entity.villager.work_priest")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_WORK_SCRIBE =
            SOUNDS.register("entity.villager.work_scribe", () -> SoundEvent.createVariableRangeEvent(id("entity.villager.work_scribe")));

    // ---- Data Component & Attachment ----
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SanctityComponent>> SANCTITY =
            COMPONENTS.registerComponentType("sanctity", builder -> builder.persistent(SanctityComponent.CODEC)
                    .networkSynchronized(SanctityComponent.STREAM_CODEC));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PietyAttachment>> PIETY =
            ATTACHMENTS.register("piety", () -> AttachmentType.builder(PietyAttachment::new)
                    .serialize(PietyAttachment.CODEC).sync(PietyAttachment.SYNC_CODEC).copyOnDeath().build());

    // ---- Blocks (9) & BlockItems ----
    public static final DeferredBlock<TheologyBlocks.ShrineAltarBlock> SHRINE_ALTAR =
            BLOCKS.registerBlock("shrine_altar", TheologyBlocks.ShrineAltarBlock::new,
                    props -> props.mapColor(MapColor.STONE).strength(3.0F).sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> SHRINE_ALTAR_ITEM = ITEMS.registerSimpleBlockItem(SHRINE_ALTAR);

    public static final DeferredBlock<TheologyBlocks.PrayerCandleBlock> PRAYER_CANDLE =
            BLOCKS.registerBlock("prayer_candle", TheologyBlocks.PrayerCandleBlock::new,
                    props -> props.mapColor(MapColor.SAND).strength(0.1F).sound(SoundType.CANDLE).noOcclusion());
    public static final DeferredItem<BlockItem> PRAYER_CANDLE_ITEM = ITEMS.registerSimpleBlockItem(PRAYER_CANDLE);

    public static final DeferredBlock<TheologyBlocks.ScriptureLecternBlock> SCRIPTURE_LECTERN =
            BLOCKS.registerBlock("scripture_lectern", TheologyBlocks.ScriptureLecternBlock::new,
                    props -> props.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> SCRIPTURE_LECTERN_ITEM = ITEMS.registerSimpleBlockItem(SCRIPTURE_LECTERN);

    public static final DeferredBlock<TheologyBlocks.OfferingTrayBlock> OFFERING_TRAY =
            BLOCKS.registerBlock("offering_tray", TheologyBlocks.OfferingTrayBlock::new,
                    props -> props.mapColor(MapColor.GOLD).strength(1.5F).sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> OFFERING_TRAY_ITEM = ITEMS.registerSimpleBlockItem(OFFERING_TRAY);

    public static final DeferredBlock<TheologyBlocks.CenserBlock> CENSER =
            BLOCKS.registerBlock("censer", TheologyBlocks.CenserBlock::new,
                    props -> props.mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> CENSER_ITEM = ITEMS.registerSimpleBlockItem(CENSER);

    public static final DeferredBlock<TheologyBlocks.PewBlock> PEW =
            BLOCKS.registerBlock("pew", TheologyBlocks.PewBlock::new,
                    props -> props.mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> PEW_ITEM = ITEMS.registerSimpleBlockItem(PEW);

    public static final DeferredBlock<TheologyBlocks.ReliquaryBlock> RELIQUARY =
            BLOCKS.registerBlock("reliquary", TheologyBlocks.ReliquaryBlock::new,
                    props -> props.mapColor(MapColor.GOLD).strength(4.0F, 1200.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> RELIQUARY_ITEM = ITEMS.registerSimpleBlockItem(RELIQUARY);

    public static final DeferredBlock<TheologyBlocks.TempleKeystoneBlock> TEMPLE_KEYSTONE =
            BLOCKS.registerBlock("temple_keystone", TheologyBlocks.TempleKeystoneBlock::new,
                    props -> props.mapColor(MapColor.STONE).strength(5.0F, 1200.0F).sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> TEMPLE_KEYSTONE_ITEM = ITEMS.registerSimpleBlockItem(TEMPLE_KEYSTONE);

    public static final DeferredBlock<TheologyBlocks.WardStoneBlock> WARD_STONE =
            BLOCKS.registerBlock("ward_stone", TheologyBlocks.WardStoneBlock::new,
                    props -> props.mapColor(MapColor.COLOR_PURPLE).strength(5.0F).sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> WARD_STONE_ITEM = ITEMS.registerSimpleBlockItem(WARD_STONE);

    // ---- BlockEntityTypes (6) ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TheologyBlockEntities.ShrineAltarBlockEntity>> SHRINE_ALTAR_BE =
            BLOCK_ENTITY_TYPES.register("shrine_altar",
                    () -> new BlockEntityType<>(TheologyBlockEntities.ShrineAltarBlockEntity::new, SHRINE_ALTAR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TheologyBlockEntities.ScriptureLecternBlockEntity>> SCRIPTURE_LECTERN_BE =
            BLOCK_ENTITY_TYPES.register("scripture_lectern",
                    () -> new BlockEntityType<>(TheologyBlockEntities.ScriptureLecternBlockEntity::new, SCRIPTURE_LECTERN.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TheologyBlockEntities.OfferingTrayBlockEntity>> OFFERING_TRAY_BE =
            BLOCK_ENTITY_TYPES.register("offering_tray",
                    () -> new BlockEntityType<>(TheologyBlockEntities.OfferingTrayBlockEntity::new, OFFERING_TRAY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TheologyBlockEntities.CenserBlockEntity>> CENSER_BE =
            BLOCK_ENTITY_TYPES.register("censer",
                    () -> new BlockEntityType<>(TheologyBlockEntities.CenserBlockEntity::new, CENSER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TheologyBlockEntities.ReliquaryBlockEntity>> RELIQUARY_BE =
            BLOCK_ENTITY_TYPES.register("reliquary",
                    () -> new BlockEntityType<>(TheologyBlockEntities.ReliquaryBlockEntity::new, RELIQUARY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TheologyBlockEntities.WardStoneBlockEntity>> WARD_STONE_BE =
            BLOCK_ENTITY_TYPES.register("ward_stone",
                    () -> new BlockEntityType<>(TheologyBlockEntities.WardStoneBlockEntity::new, WARD_STONE.get()));

    // ---- Items (10) ----
    public static final ResourceKey<EquipmentAsset> VESTMENT_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, id("vestment"));
    public static final ArmorMaterial VESTMENT_MATERIAL = createVestmentMaterial();

    public static final DeferredItem<TheologyItems.PrayerBeadsItem> PRAYER_BEADS =
            ITEMS.registerItem("prayer_beads", TheologyItems.PrayerBeadsItem::new, props -> props.stacksTo(1));
    public static final DeferredItem<TheologyItems.HolyWaterItem> HOLY_WATER =
            ITEMS.registerItem("holy_water", TheologyItems.HolyWaterItem::new,
                    props -> props.stacksTo(16).component(SANCTITY.get(), SanctityComponent.DEFAULT));
    public static final DeferredItem<TheologyItems.FeastCalendarItem> FEAST_CALENDAR =
            ITEMS.registerItem("feast_calendar", TheologyItems.FeastCalendarItem::new, props -> props.stacksTo(1));
    public static final DeferredItem<TheologyItems.RelicItem> RELIC =
            ITEMS.registerItem("relic", TheologyItems.RelicItem::new, props -> props.stacksTo(1));
    public static final DeferredItem<Item> VESTMENT =
            ITEMS.registerItem("vestment", Item::new, props -> props.humanoidArmor(VESTMENT_MATERIAL, ArmorType.CHESTPLATE));
    public static final DeferredItem<TheologyItems.WardCharmItem> WARD_CHARM =
            ITEMS.registerItem("ward_charm", TheologyItems.WardCharmItem::new, props -> props.stacksTo(1));
    public static final DeferredItem<TheologyItems.PilgrimStaffItem> PILGRIM_STAFF =
            ITEMS.registerItem("pilgrim_staff", TheologyItems.PilgrimStaffItem::new, props -> props.stacksTo(1));
    public static final DeferredItem<Item> BLANK_SCROLL =
            ITEMS.registerSimpleItem("blank_scroll", props -> props.stacksTo(16));
    public static final DeferredItem<Item> INCENSE =
            ITEMS.registerSimpleItem("incense", props -> props.stacksTo(64));
    public static final DeferredItem<TheologyItems.ScriptureScrollItem> SCRIPTURE_SCROLL =
            ITEMS.registerItem("scripture_scroll", TheologyItems.ScriptureScrollItem::new, props -> props.stacksTo(1));

    // ---- EntityType (seat) ----
    public static final DeferredHolder<EntityType<?>, EntityType<PewSeatEntity>> SEAT_ENTITY =
            ENTITY_TYPES.registerEntityType("seat", PewSeatEntity::new, MobCategory.MISC,
                    builder -> builder.sized(0.01F, 0.01F).noSave().noSummon().clientTrackingRange(16).updateInterval(20));

    // ---- MobEffects (2) ----
    public static final DeferredHolder<MobEffect, MobEffect> SERENITY =
            MOB_EFFECTS.register("serenity", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xEFE7D2) {
                @Override
                public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
                    return tickCount > 0 && tickCount % 100 == 0;
                }

                @Override
                public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplification) {
                    if (!entity.hasEffect(MobEffects.REGENERATION)) {
                        entity.heal(1.0F);
                    }
                    return true;
                }
            });

    public static final DeferredHolder<MobEffect, MobEffect> BLESSED =
            MOB_EFFECTS.register("blessed", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xC4A042) {});

    // ---- POI & Villager Professions (TB-01 priest, TB-02 scribe) ----
    public static final DeferredHolder<PoiType, PoiType> PRIEST_POI =
            POI_TYPES.register("priest", () -> new PoiType(
                    Set.copyOf(SHRINE_ALTAR.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final DeferredHolder<PoiType, PoiType> SCRIBE_POI =
            POI_TYPES.register("scribe", () -> new PoiType(
                    Set.copyOf(SCRIPTURE_LECTERN.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> PRIEST =
            PROFESSIONS.register("priest", () -> new VillagerProfession(
                    Component.translatable("entity.minecraft.villager.earth_theology.priest"),
                    holder -> holder.is(PRIEST_POI.getKey()),
                    holder -> holder.is(PRIEST_POI.getKey()),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SOUND_WORK_PRIEST.get(),
                    Int2ObjectMap.ofEntries()
            ));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> SCRIBE =
            PROFESSIONS.register("scribe", () -> new VillagerProfession(
                    Component.translatable("entity.minecraft.villager.earth_theology.scribe"),
                    holder -> holder.is(SCRIBE_POI.getKey()),
                    holder -> holder.is(SCRIBE_POI.getKey()),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SOUND_WORK_SCRIBE.get(),
                    Int2ObjectMap.ofEntries()
            ));

    // ---- Creative Tab ----
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("earth_theology",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.earth_theology"))
                    .icon(() -> new ItemStack(PRAYER_BEADS.get()))
                    .displayItems((params, output) -> {
                        output.accept(PRAYER_BEADS.get());
                        output.accept(BLANK_SCROLL.get());
                        output.accept(INCENSE.get());
                        output.accept(HOLY_WATER.get());
                        output.accept(FEAST_CALENDAR.get());
                        output.accept(RELIC.get());
                        output.accept(VESTMENT.get());
                        output.accept(WARD_CHARM.get());
                        output.accept(PILGRIM_STAFF.get());
                        output.accept(SHRINE_ALTAR_ITEM.get());
                        output.accept(PRAYER_CANDLE_ITEM.get());
                        output.accept(SCRIPTURE_LECTERN_ITEM.get());
                        output.accept(OFFERING_TRAY_ITEM.get());
                        output.accept(CENSER_ITEM.get());
                        output.accept(PEW_ITEM.get());
                        output.accept(RELIQUARY_ITEM.get());
                        output.accept(TEMPLE_KEYSTONE_ITEM.get());
                        output.accept(WARD_STONE_ITEM.get());
                    })
                    .build());

    public EarthTheology(IEventBus modBus) {
        SOUNDS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        MOB_EFFECTS.register(modBus);
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        TABS.register(modBus);
        TheologyLoreIntegration.register(modBus);
        TheologyEvents.register();
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            EarthTheologyClient.register(modBus);
        }
        LOGGER.info("[earth_theology] loaded");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static ArmorMaterial createVestmentMaterial() {
        EnumMap<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.BOOTS, 1);
        defense.put(ArmorType.LEGGINGS, 2);
        defense.put(ArmorType.CHESTPLATE, 3);
        defense.put(ArmorType.HELMET, 1);
        defense.put(ArmorType.BODY, 3);
        return new ArmorMaterial(15, defense, 15, SoundEvents.ARMOR_EQUIP_GENERIC, 0.0F, 0.0F, ItemTags.WOOL, VESTMENT_ASSET);
    }
}

