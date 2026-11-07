package com.xxsx.earththeology;

import com.xxsx.earthlore.api.Grade;

/**
 * Pure numeric and rule calculations for Earth: Theology (18 册 §1–§9).
 * Kept free of Minecraft level/entity imports so JUnit 5 tests can verify all formulas directly.
 */
public final class TheologyMath {
    public static final int SANCTUM_MAX = 100;
    public static final int ALTAR_REACH = 8;
    public static final int SANCTUM_SCAN_XZ = 8;
    public static final int SANCTUM_SCAN_Y = 4;
    public static final int SANCTUM_REFRESH_TICKS = 600;
    public static final double DESECRATION_MAX = 30.0D;
    public static final double DESECRATION_RECOVERY = 0.003D;
    public static final int PIETY_MAX = 100;
    public static final int PRAYER_TICKS = 100;
    public static final int PRAYER_COOLDOWN = 1200;
    public static final int OFFERING_DAILY_CAP = 3;
    public static final int OFFERING_FEAST_CAP = 4;
    public static final int BLESS_BASE_TICKS = 1200;
    public static final int WARD_RADIUS = 16;
    public static final int TEMPLE_BONUS = 25;
    public static final int PILGRIM_MIN_DISTANCE = 512;
    public static final int PILGRIM_STAMPS = 3;
    public static final int MIRACLE_INTERVAL_DAYS = 7;
    public static final int MAX_SANCTUARY_SLOPE_DELTA = 4;

    private TheologyMath() {
    }

    public static long gameDay(long dayTime) {
        return Math.max(0L, dayTime) / 24000L;
    }

    public static int moonPhase(long dayTime) {
        return (int) (gameDay(dayTime) % 8L);
    }

    public static boolean isFeastDay(long dayTime) {
        int phase = moonPhase(dayTime);
        return phase == 0 || phase == 4;
    }

    public static int daysUntilNextFeast(long dayTime) {
        int phase = moonPhase(dayTime);
        if (phase == 0 || phase == 4) {
            return 0;
        }
        return phase < 4 ? (4 - phase) : (8 - phase);
    }

    public static int clampPiety(int value) {
        return Math.max(0, Math.min(PIETY_MAX, value));
    }

    public static double clampDesecration(double value) {
        return Math.max(0.0D, Math.min(DESECRATION_MAX, value));
    }

    public static double recoverDesecration(double currentValue, long elapsedTicks) {
        if (elapsedTicks <= 0L || currentValue <= 0.0D) {
            return clampDesecration(currentValue);
        }
        double next = currentValue - DESECRATION_RECOVERY * (double) elapsedTicks;
        return next <= 0.0D ? 0.0D : Math.min(DESECRATION_MAX, next);
    }

    /**
     * T-FIELD-01 sanctum calculation.
     * Source caps: altar +15 (cap 15), lit candle +2 (cap 10), lit censer +5 (cap 10),
     * lectern with scroll +4 (cap 8), pew +1 (cap 8), bell +3 (cap 6),
     * reliquary with relic +6 (cap 12), active ward_stone +4 (cap 4).
     */
    public static int computeSanctumValue(
            int altars,
            int litCandles,
            int litCensers,
            int scrollLecterns,
            int pews,
            int bells,
            int filledReliquaries,
            int activeWardStones,
            boolean blessed,
            boolean templeBonusApplies,
            double desecration
    ) {
        int altarPts = Math.min(15, Math.max(0, altars) * 15);
        int candlePts = Math.min(10, Math.max(0, litCandles) * 2);
        int censerPts = Math.min(10, Math.max(0, litCensers) * 5);
        int lecternPts = Math.min(8, Math.max(0, scrollLecterns) * 4);
        int pewPts = Math.min(8, Math.max(0, pews));
        int bellPts = Math.min(6, Math.max(0, bells) * 3);
        int reliquaryPts = Math.min(12, Math.max(0, filledReliquaries) * 6);
        int wardPts = Math.min(4, Math.max(0, activeWardStones) * 4);
        int offerPts = blessed ? 5 : 0;
        int templePts = templeBonusApplies ? TEMPLE_BONUS : 0;
        double raw = (double) (altarPts + candlePts + censerPts + lecternPts + pewPts + bellPts + reliquaryPts + wardPts + offerPts + templePts)
                - clampDesecration(desecration);
        int floored = (int) Math.floor(raw);
        return Math.max(0, Math.min(SANCTUM_MAX, floored));
    }

    /** T-006 holy water grade from altar sanctum value (before knowledge cap). */
    public static Grade rawHolyWaterGrade(int sanctumValue) {
        if (sanctumValue >= 80) {
            return Grade.HEIRLOOM;
        }
        if (sanctumValue >= 60) {
            return Grade.MASTERWORK;
        }
        if (sanctumValue >= 40) {
            return Grade.FINE;
        }
        if (sanctumValue >= 20) {
            return Grade.COMMON;
        }
        return Grade.CRUDE;
    }

    /** T-006 holy water grade after applying K-THE-06 and K-THE-17 soft/cap rules. */
    public static Grade resolveHolyWaterGrade(int sanctumValue, boolean knowsHolyWater, boolean knowsMastery) {
        Grade raw = rawHolyWaterGrade(sanctumValue);
        Grade cap = !knowsHolyWater ? Grade.COMMON : (knowsMastery ? Grade.HEIRLOOM : Grade.MASTERWORK);
        return raw.min(cap);
    }

    /** T-006 multiplier for holy water grade: crude 0.5, common 1.0, fine 1.25, masterwork 1.5, heirloom 2.0. */
    public static float holyWaterMultiplier(Grade grade) {
        if (grade == null) {
            return 1.0F;
        }
        return switch (grade) {
            case CRUDE -> 0.5F;
            case COMMON -> 1.0F;
            case FINE -> 1.25F;
            case MASTERWORK -> 1.5F;
            case HEIRLOOM -> 2.0F;
        };
    }

    /** T-OFFER-01 daily offering piety gain given item value (1..3), already gained today, and feast day flag. */
    public static int computeOfferingPietyGain(int maxOfferingValue, int alreadyGainedToday, boolean feastDay, boolean knowsOfferings) {
        if (!knowsOfferings || maxOfferingValue <= 0) {
            return 0;
        }
        int cap = feastDay ? OFFERING_FEAST_CAP : OFFERING_DAILY_CAP;
        int remaining = Math.max(0, cap - Math.max(0, alreadyGainedToday));
        return Math.min(Math.min(3, maxOfferingValue), remaining);
    }

    /** T-BLESS-01 duration in ticks. */
    public static int computeBlessDurationTicks(int piety, boolean feastDay, boolean wearingVestment) {
        double ticks = BLESS_BASE_TICKS + 24.0D * clampPiety(piety);
        if (feastDay) {
            ticks *= 1.5D;
        }
        if (wearingVestment) {
            ticks *= 1.25D;
        }
        return (int) Math.round(ticks);
    }

    /** T-EXORCISM-01 undead magic damage: 8 * multiplier. */
    public static float computeExorcismDamage(Grade grade) {
        return 8.0F * holyWaterMultiplier(grade);
    }

    /** T-006 direct undead splash damage: 4 * multiplier. */
    public static float computeHolyWaterSplashDamage(Grade grade) {
        return 4.0F * holyWaterMultiplier(grade);
    }

    /** T-HEAL-01 healing prayer amount: (4 + piety / 10) * multiplier. */
    public static float computeHealingPrayerAmount(int piety, Grade grade) {
        int base = 4 + (clampPiety(piety) / 10);
        return (float) base * holyWaterMultiplier(grade);
    }

    /** T-MIRACLE-01 success chance: min(0.80, 0.30 + 0.005 * piety). */
    public static double computeMiracleChance(int piety) {
        return Math.min(0.80D, 0.30D + 0.005D * clampPiety(piety));
    }

    /** T-016 horizontal distance check (>= 512 blocks). */
    public static boolean meetsPilgrimDistance(int startX, int startZ, int currentX, int currentZ) {
        long dx = (long) currentX - (long) startX;
        long dz = (long) currentZ - (long) startZ;
        return dx * dx + dz * dz >= (long) PILGRIM_MIN_DISTANCE * (long) PILGRIM_MIN_DISTANCE;
    }

    /** Worldgen terrain validation: rejects water/fluids, below-sea-level sites, and steep slopes (> 4 blocks). */
    public static boolean isValidSanctuaryTerrain(int minY, int maxY, int seaLevel, boolean hasFluid) {
        if (hasFluid || minY <= seaLevel || maxY < minY) {
            return false;
        }
        return (maxY - minY) <= MAX_SANCTUARY_SLOPE_DELTA;
    }

    /** Novice/clergy Priest exorcism damage scaling by villager level (1..5). */
    public static float computePriestExorcismDamage(int villagerLevel) {
        int clamped = Math.max(1, Math.min(5, villagerLevel));
        return 6.0F + clamped * 2.0F;
    }

    /** Novice/clergy Priest healing amount by villager level (1..5). */
    public static float computePriestHealAmount(int villagerLevel) {
        int clamped = Math.max(1, Math.min(5, villagerLevel));
        return 4.0F + clamped * 2.0F;
    }

    /** Novice/clergy Priest blessing duration in ticks by villager level (1..5). */
    public static int computePriestBlessTicks(int villagerLevel) {
        int clamped = Math.max(1, Math.min(5, villagerLevel));
        return 1200 + clamped * 400;
    }

    /** Normalized [0..1] progress for prayer channeling HUD. */
    public static float computePrayerProgress(int elapsedTicks, int requiredTicks) {
        if (requiredTicks <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) elapsedTicks / (float) requiredTicks));
    }
}

