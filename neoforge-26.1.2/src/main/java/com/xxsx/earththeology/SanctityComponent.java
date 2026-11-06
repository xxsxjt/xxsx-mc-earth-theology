package com.xxsx.earththeology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xxsx.earthlore.api.Grade;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * T-006 earth_theology:sanctity data component on holy_water.
 */
public record SanctityComponent(Grade grade, int sanctumAtMake) {
    public static final SanctityComponent DEFAULT = new SanctityComponent(Grade.COMMON, 10);
    public static final Codec<Grade> GRADE_CODEC = Codec.STRING.xmap(
            SanctityComponent::parseGrade, Grade::id);

    public SanctityComponent {
        grade = grade == null ? Grade.COMMON : grade;
        sanctumAtMake = Math.max(10, Math.min(100, sanctumAtMake));
    }

    public static final Codec<SanctityComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GRADE_CODEC.optionalFieldOf("grade", Grade.COMMON).forGetter(SanctityComponent::grade),
            Codec.INT.optionalFieldOf("sanctum_at_make", 10).forGetter(SanctityComponent::sanctumAtMake)
    ).apply(instance, SanctityComponent::new));

    public static final StreamCodec<ByteBuf, SanctityComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(SanctityComponent::parseGrade, Grade::id), SanctityComponent::grade,
            ByteBufCodecs.VAR_INT, SanctityComponent::sanctumAtMake,
            SanctityComponent::new
    );

    private static Grade parseGrade(String id) {
        for (Grade g : Grade.values()) {
            if (g.id().equalsIgnoreCase(id)) {
                return g;
            }
        }
        return Grade.COMMON;
    }
}

