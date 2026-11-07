package com.xxsx.earththeology.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xxsx.earthlore.client.LoreTreeScreen;
import com.xxsx.earththeology.EarthTheology;
import com.xxsx.earththeology.PewSeatEntity;
import com.xxsx.earththeology.TheologyLoreIntegration;
import com.xxsx.earththeology.TheologyMath;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class EarthTheologyClient {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(EarthTheology.id("controls"));
    public static final KeyMapping OPEN_THEOLOGY_KEY = new KeyMapping(
            "key.earth_theology.tree",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    private EarthTheologyClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EarthTheologyClient::registerEntityRenderers);
        modBus.addListener(EarthTheologyClient::registerGuiLayers);
        modBus.addListener(EarthTheologyClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(EarthTheologyClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(EarthTheologyClient::onRenderPlayerPre);
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EarthTheology.SEAT_ENTITY.get(), SeatRenderer::new);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, EarthTheology.id("piety_hud"), EarthTheologyClient::renderPietyHud);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_THEOLOGY_KEY);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_THEOLOGY_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new LoreTreeScreen(TheologyLoreIntegration.DISCIPLINE_THE));
            }
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        boolean praying = player.isUsingItem() && player.getUseItem().is(EarthTheology.PRAYER_BEADS.get());
        boolean seatedOnPew = player.getVehicle() instanceof PewSeatEntity;
        boolean blessed = player.hasEffect(EarthTheology.BLESSED);
        if (praying && player.tickCount % 4 == 0) {
            double angle = (player.tickCount * 0.35D) % (Math.PI * 2.0D);
            double px = player.getX() + Math.cos(angle) * 0.45D;
            double py = player.getY() + 0.9D + (minecraft.level.getRandom().nextDouble() - 0.5D) * 0.3D;
            double pz = player.getZ() + Math.sin(angle) * 0.45D;
            minecraft.level.addParticle(ParticleTypes.END_ROD, px, py, pz, 0.0D, 0.015D, 0.0D);
        } else if ((seatedOnPew || blessed) && player.tickCount % 16 == 0) {
            double px = player.getX() + (minecraft.level.getRandom().nextDouble() - 0.5D) * 0.7D;
            double py = player.getY() + 0.6D + minecraft.level.getRandom().nextDouble() * 0.9D;
            double pz = player.getZ() + (minecraft.level.getRandom().nextDouble() - 0.5D) * 0.7D;
            minecraft.level.addParticle(ParticleTypes.WAX_ON, px, py, pz, 0.0D, 0.02D, 0.0D);
        }
    }

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(event.getRenderState().id);
        if (!(entity instanceof Player player)) {
            return;
        }
        PlayerModel model = event.getRenderer().getModel();
        boolean praying = player.isUsingItem() && player.getUseItem().is(EarthTheology.PRAYER_BEADS.get());
        boolean seatedOnPew = player.getVehicle() instanceof PewSeatEntity;
        if (praying) {
            float pulse = Mth.sin((player.tickCount + event.getPartialTick()) * 0.18F);
            model.rightArm.xRot = -1.05F - pulse * 0.08F;
            model.leftArm.xRot = -1.05F - pulse * 0.08F;
            model.rightArm.yRot = -0.38F;
            model.leftArm.yRot = 0.38F;
            model.rightArm.zRot = 0.12F;
            model.leftArm.zRot = -0.12F;
            model.head.xRot += 0.26F + pulse * 0.04F;
            model.body.xRot += 0.08F;
            copyPose(model.rightArm, model.rightSleeve);
            copyPose(model.leftArm, model.leftSleeve);
            copyPose(model.body, model.jacket);
        } else if (seatedOnPew) {
            model.rightArm.xRot = -0.82F;
            model.leftArm.xRot = -0.82F;
            model.rightArm.yRot = -0.28F;
            model.leftArm.yRot = 0.28F;
            model.head.xRot += 0.16F;
            copyPose(model.rightArm, model.rightSleeve);
            copyPose(model.leftArm, model.leftSleeve);
        } else if (player.getMainHandItem().is(EarthTheology.PILGRIM_STAFF.get())) {
            model.rightArm.xRot = -0.75F;
            model.rightArm.yRot = -0.10F;
            copyPose(model.rightArm, model.rightSleeve);
        } else if (player.getMainHandItem().is(EarthTheology.RELIC.get())) {
            model.rightArm.xRot = -0.88F;
            model.leftArm.xRot = -0.88F;
            model.rightArm.yRot = -0.30F;
            model.leftArm.yRot = 0.30F;
            copyPose(model.rightArm, model.rightSleeve);
            copyPose(model.leftArm, model.leftSleeve);
        }
    }

    private static void copyPose(ModelPart source, ModelPart target) {
        target.x = source.x;
        target.y = source.y;
        target.z = source.z;
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }

    private static void renderPietyHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null || player.isSpectator()) {
            return;
        }
        boolean praying = player.isUsingItem() && player.getUseItem().is(EarthTheology.PRAYER_BEADS.get());
        boolean seatedOnPew = player.getVehicle() instanceof PewSeatEntity;
        MobEffectInstance blessed = player.getEffect(EarthTheology.BLESSED);
        MobEffectInstance serenity = player.getEffect(EarthTheology.SERENITY);
        boolean holdingSacred = isTheologyItem(player.getMainHandItem()) || isTheologyItem(player.getOffhandItem());
        if (!praying && !seatedOnPew && blessed == null && serenity == null && !holdingSacred) {
            return;
        }

        Font font = minecraft.font;
        int piety = TheologyMath.clampPiety(player.getData(EarthTheology.PIETY.get()).value());
        int x = 8;
        int y = 8;
        int width = 148;
        int height = praying ? 44 : 34;

        graphics.fill(x, y, x + width, y + height, 0xD8C4A042);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xCC181410);

        Component pietyText = Component.translatable("hud.earth_theology.piety", piety, TheologyMath.PIETY_MAX);
        graphics.text(font, pietyText, x + 6, y + 5, 0xFFF5E6C4, false);

        int barX = x + 6;
        int barY = y + 16;
        int barW = width - 12;
        graphics.fill(barX, barY, barX + barW, barY + 3, 0xFF3A3124);
        int fillW = Math.round(barW * (piety / (float) TheologyMath.PIETY_MAX));
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + 3, 0xFFE5C158);
        }

        Component subLine;
        if (praying) {
            int elapsed = EarthTheology.PRAYER_BEADS.get().getUseDuration(player.getUseItem(), player)
                    - player.getUseItemRemainingTicks();
            float progress = TheologyMath.computePrayerProgress(elapsed, TheologyMath.PRAYER_TICKS);
            int pct = Math.round(progress * 100.0F);
            subLine = Component.translatable("hud.earth_theology.praying", pct);
            int pBarY = y + 34;
            graphics.fill(barX, pBarY, barX + barW, pBarY + 4, 0xFF2B3530);
            graphics.fill(barX, pBarY, barX + Math.max(1, Math.round(barW * progress)), pBarY + 4, 0xFF8BE8B0);
        } else if (blessed != null || serenity != null) {
            int ticks = Math.max(blessed != null ? blessed.getDuration() : 0,
                    serenity != null ? serenity.getDuration() : 0);
            int secs = Math.max(1, (ticks + 19) / 20);
            subLine = Component.translatable("hud.earth_theology.grace", secs);
        } else {
            subLine = Component.translatable("hud.earth_theology.hint", OPEN_THEOLOGY_KEY.getTranslatedKeyMessage());
        }
        graphics.text(font, subLine, x + 6, y + 22, 0xFFD8CBB0, false);
    }

    private static boolean isTheologyItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && EarthTheology.MODID.equals(id.getNamespace());
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

