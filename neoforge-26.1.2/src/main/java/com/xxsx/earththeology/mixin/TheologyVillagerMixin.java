package com.xxsx.earththeology.mixin;

import com.xxsx.earththeology.TheologyEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
abstract class TheologyVillagerMixin {
    @Inject(method = "updateTrades", at = @At("HEAD"))
    private void earthTheology$addProfessionTrades(ServerLevel level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        TheologyEvents.addProfessionTrades(self, self.getVillagerData().level());
    }
}

