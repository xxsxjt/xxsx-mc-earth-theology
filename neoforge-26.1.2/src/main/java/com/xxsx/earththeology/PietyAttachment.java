package com.xxsx.earththeology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * T-PIETY-01 earth_theology:piety player attachment.
 * Kept on death (copyOnDeath), value synced to owner.
 */
public final class PietyAttachment {
    public static final MapCodec<PietyAttachment> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("value", 0).forGetter(PietyAttachment::value),
            Codec.LONG.optionalFieldOf("last_prayer_day", -1L).forGetter(PietyAttachment::lastPrayerDay),
            Codec.LONG.optionalFieldOf("offering_day", -1L).forGetter(PietyAttachment::offeringDay),
            Codec.INT.optionalFieldOf("offering_gained", 0).forGetter(PietyAttachment::offeringGained),
            Codec.LONG.optionalFieldOf("last_revelation_day", -1L).forGetter(PietyAttachment::lastRevelationDay),
            Codec.LONG.optionalFieldOf("last_miracle_day", -1000L).forGetter(PietyAttachment::lastMiracleDay),
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(PietyAttachment::schemaVersion)
    ).apply(instance, PietyAttachment::new));

    public static final StreamCodec<ByteBuf, PietyAttachment> SYNC_CODEC = ByteBufCodecs.VAR_INT.map(
            v -> new PietyAttachment(v, -1L, -1L, 0, -1L, -1000L, 1),
            PietyAttachment::value
    );

    private int value;
    private long lastPrayerDay = -1L;
    private long offeringDay = -1L;
    private int offeringGained;
    private long lastRevelationDay = -1L;
    private long lastMiracleDay = -1000L;
    private int schemaVersion = 1;

    public PietyAttachment() {
    }

    public PietyAttachment(
            int value,
            long lastPrayerDay,
            long offeringDay,
            int offeringGained,
            long lastRevelationDay,
            long lastMiracleDay,
            int schemaVersion
    ) {
        this.value = TheologyMath.clampPiety(value);
        this.lastPrayerDay = lastPrayerDay;
        this.offeringDay = offeringDay;
        this.offeringGained = Math.max(0, Math.min(4, offeringGained));
        this.lastRevelationDay = lastRevelationDay;
        this.lastMiracleDay = lastMiracleDay;
        this.schemaVersion = schemaVersion;
    }

    public int value() {
        return value;
    }

    public void setValue(int value) {
        this.value = TheologyMath.clampPiety(value);
    }

    public void addPiety(int delta) {
        this.value = TheologyMath.clampPiety(this.value + delta);
    }

    public long lastPrayerDay() {
        return lastPrayerDay;
    }

    public void setLastPrayerDay(long lastPrayerDay) {
        this.lastPrayerDay = lastPrayerDay;
    }

    public long offeringDay() {
        return offeringDay;
    }

    public void setOfferingDay(long offeringDay) {
        this.offeringDay = offeringDay;
    }

    public int offeringGained() {
        return offeringGained;
    }

    public void setOfferingGained(int offeringGained) {
        this.offeringGained = Math.max(0, Math.min(4, offeringGained));
    }

    public long lastRevelationDay() {
        return lastRevelationDay;
    }

    public void setLastRevelationDay(long lastRevelationDay) {
        this.lastRevelationDay = lastRevelationDay;
    }

    public long lastMiracleDay() {
        return lastMiracleDay;
    }

    public void setLastMiracleDay(long lastMiracleDay) {
        this.lastMiracleDay = lastMiracleDay;
    }

    public int schemaVersion() {
        return schemaVersion;
    }
}

