package com.xxsx.earththeology;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Tags for Earth: Theology (18 册 §4, §7, §12).
 */
public final class TheologyTags {
    public static final TagKey<Item> OFFERINGS = TagKey.create(Registries.ITEM, EarthTheology.id("offerings"));
    public static final TagKey<Item> OFFERINGS_COMMON = TagKey.create(Registries.ITEM, EarthTheology.id("offerings/common"));
    public static final TagKey<Item> OFFERINGS_FINE = TagKey.create(Registries.ITEM, EarthTheology.id("offerings/fine"));
    public static final TagKey<Item> OFFERINGS_PRECIOUS = TagKey.create(Registries.ITEM, EarthTheology.id("offerings/precious"));
    public static final TagKey<Structure> PILGRIMAGE_SITES = TagKey.create(Registries.STRUCTURE, EarthTheology.id("pilgrimage_sites"));

    private TheologyTags() {
    }

    public static boolean isOffering(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(OFFERINGS) || offeringValue(stack) > 0);
    }

    public static int offeringValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(OFFERINGS_PRECIOUS)) {
            return 3;
        }
        if (stack.is(OFFERINGS_FINE)) {
            return 2;
        }
        if (stack.is(OFFERINGS_COMMON) || stack.is(OFFERINGS)) {
            return 1;
        }
        return 0;
    }

    public static boolean isUndead(Entity entity) {
        return entity != null && entity.typeHolder().is(EntityTypeTags.UNDEAD);
    }
}

