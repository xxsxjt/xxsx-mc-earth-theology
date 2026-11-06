package com.xxsx.earththeology;

import com.xxsx.earthlore.api.ConditionProvider;
import com.xxsx.earthlore.api.Grade;
import com.xxsx.earthlore.api.GradeAccess;
import com.xxsx.earthlore.api.LoreApi;
import com.xxsx.earthlore.api.LoreLearnedEvent;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Bridges Earth: Theology with Earth: Lore (18 册 §2, 10 册 A-010/A-011/A-014).
 */
public final class TheologyLoreIntegration {
    public static final Identifier DISCIPLINE_THE = Identifier.fromNamespaceAndPath("earth_lore", "the");

    public static final Identifier NODE_PRAYER = EarthTheology.id("the/prayer");
    public static final Identifier NODE_SANCTUM = EarthTheology.id("the/sanctum");
    public static final Identifier NODE_SCRIPTURES = EarthTheology.id("the/scriptures");
    public static final Identifier NODE_OFFERINGS = EarthTheology.id("the/offerings");
    public static final Identifier NODE_BLESSINGS = EarthTheology.id("the/blessings");
    public static final Identifier NODE_HOLY_WATER = EarthTheology.id("the/holy_water");
    public static final Identifier NODE_EXORCISM = EarthTheology.id("the/exorcism");
    public static final Identifier NODE_HEALING_PRAYER = EarthTheology.id("the/healing_prayer");
    public static final Identifier NODE_CALENDAR = EarthTheology.id("the/liturgical_calendar");
    public static final Identifier NODE_RELICS = EarthTheology.id("the/relics");
    public static final Identifier NODE_TEMPLE = EarthTheology.id("the/temple");
    public static final Identifier NODE_ORDINATION = EarthTheology.id("the/ordination");
    public static final Identifier NODE_REVELATION = EarthTheology.id("the/revelation");
    public static final Identifier NODE_SANCTUARY_WARD = EarthTheology.id("the/sanctuary_ward");
    public static final Identifier NODE_PILGRIMAGE = EarthTheology.id("the/pilgrimage");
    public static final Identifier NODE_MIRACLES = EarthTheology.id("the/miracles");
    public static final Identifier NODE_MASTERY = EarthTheology.id("the/theology_mastery");

    private TheologyLoreIntegration() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(TheologyLoreIntegration::onCommonSetup);
        NeoForge.EVENT_BUS.addListener(TheologyLoreIntegration::onLoreLearned);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LoreApi.registerCondition("sanctum", (level, pos) ->
                    SanctumFieldSavedData.get(level).getSanctumAt(level, pos));

            LoreApi.registerGradeAccess(new GradeAccess() {
                @Override
                public Optional<Grade> read(ItemStack stack) {
                    SanctityComponent comp = stack.get(EarthTheology.SANCTITY.get());
                    return comp == null ? Optional.empty() : Optional.of(comp.grade());
                }

                @Override
                public ItemStack write(ItemStack stack, Grade grade) {
                    SanctityComponent comp = stack.get(EarthTheology.SANCTITY.get());
                    if (comp != null) {
                        stack.set(EarthTheology.SANCTITY.get(), new SanctityComponent(grade, comp.sanctumAtMake()));
                    }
                    return stack;
                }
            });
        });
    }

    /**
     * T-022 / T-PIETY-01 rule 1: when a player learns a node via STUDY from a scripture_scroll
     * (either held in hand or read on a nearby scripture_lectern), piety +1.
     */
    private static void onLoreLearned(LoreLearnedEvent event) {
        if (event.source() != LoreLearnedEvent.Source.STUDY) {
            return;
        }
        ServerPlayer player = event.player();
        if (isReadingScripture(player, event.node())) {
            PietyAttachment piety = player.getData(EarthTheology.PIETY.get());
            piety.addPiety(1);
            player.setData(EarthTheology.PIETY.get(), piety);
        }
    }

    private static boolean isReadingScripture(ServerPlayer player, Identifier learnedNode) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(EarthTheology.SCRIPTURE_SCROLL.get())) {
                return true;
            }
        }
        // Also check if the player just studied from a scripture_lectern within 6 blocks holding this node
        var level = player.level();
        var origin = player.blockPosition();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    var p = origin.offset(dx, dy, dz);
                    if (level.getBlockEntity(p) instanceof TheologyBlockEntities.ScriptureLecternBlockEntity lecternBe) {
                        ItemStack scroll = lecternBe.scroll();
                        if (scroll.is(EarthTheology.SCRIPTURE_SCROLL.get())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}

