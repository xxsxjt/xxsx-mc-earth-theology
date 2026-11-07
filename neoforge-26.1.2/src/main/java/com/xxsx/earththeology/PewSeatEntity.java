package com.xxsx.earththeology;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * T-008 earth_theology:seat invisible seat entity for pews.
 */
public final class PewSeatEntity extends Entity {
    private BlockPos sourcePos = BlockPos.ZERO;

    public PewSeatEntity(EntityType<? extends PewSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public void bindTo(BlockPos pos) {
        this.sourcePos = pos.immutable();
        this.setPos(pos.getX() + 0.5D, pos.getY() + 0.35D, pos.getZ() + 0.5D);
    }

    public BlockPos sourcePos() {
        return sourcePos;
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.discard();
            return;
        }
        BlockState state = serverLevel.getBlockState(this.sourcePos);
        if (!state.is(EarthTheology.PEW.get()) || this.getPassengers().isEmpty()) {
            this.discard();
            return;
        }
        if (this.tickCount % 60 == 0 && this.getFirstPassenger() instanceof ServerPlayer player) {
            int sanctum = SanctumFieldSavedData.get(serverLevel).getSanctumAt(serverLevel, this.sourcePos);
            if (sanctum >= 10) {
                player.addEffect(new MobEffectInstance(EarthTheology.SERENITY, 160, 0, false, true, true));
                TheologyItems.treatEarthHumanInjuries(player, 0.0D, 0.5D, 5.0D, 3.0D);
                TheologyBlocks.emitSacredParticles(serverLevel, this.sourcePos, 2, 1, 2);
                serverLevel.playSound(null, this.sourcePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.25F, 1.25F);
            }
        }
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return new Vec3(this.getX(), this.getY() + 0.15D, this.getZ());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.sourcePos = new BlockPos(
                input.getIntOr("SourceX", 0),
                input.getIntOr("SourceY", 0),
                input.getIntOr("SourceZ", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("SourceX", this.sourcePos.getX());
        output.putInt("SourceY", this.sourcePos.getY());
        output.putInt("SourceZ", this.sourcePos.getZ());
    }
}

