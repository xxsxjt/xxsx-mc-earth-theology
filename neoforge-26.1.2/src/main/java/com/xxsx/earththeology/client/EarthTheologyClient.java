package com.xxsx.earththeology.client;

import com.xxsx.earththeology.EarthTheology;
import com.xxsx.earththeology.PewSeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class EarthTheologyClient {
    private EarthTheologyClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EarthTheologyClient::registerEntityRenderers);
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EarthTheology.SEAT_ENTITY.get(), SeatRenderer::new);
    }

    private static final class SeatRenderer extends EntityRenderer<PewSeatEntity, EntityRenderState> {
        SeatRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.0F;
            this.shadowStrength = 0.0F;
        }

        @Override
        public EntityRenderState createRenderState() {
            return new EntityRenderState();
        }
    }
}

