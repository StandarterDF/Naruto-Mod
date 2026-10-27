package com.sekwah.narutomod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sekwah.narutomod.abilities.jutsus.SixPathsModeAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.client.model.jutsu.TruthSeekerOrbModel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.sekwah.narutomod.NarutoMod;

/**
 * Draws the Truth-Seeking Orbs of the Six Paths mode as solid black 3D spheres arranged in a halo
 * behind the caster's back, parallel to the back plane.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SixPathsOrbRenderer {

    private SixPathsOrbRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick();
        long gameTime = mc.level.getGameTime();

        for (Player player : mc.level.players()) {
            INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
            if (ninjaData == null) {
                continue;
            }
            if (!ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(SixPathsModeAbility.ID)) {
                continue;
            }
            int orbs = ninjaData.getSixPathsOrbs();
            if (orbs <= 0) {
                orbs = SixPathsModeAbility.orbs(player);
            }
            if (orbs <= 0) {
                continue;
            }
            renderOrbs(player, orbs, camera, poseStack, buffer, partialTick, gameTime);
        }
        buffer.endBatch();
    }

    private static void renderOrbs(Player player, int count, Camera camera, PoseStack poseStack,
                                  MultiBufferSource.BufferSource buffer, float partialTick, long gameTime) {
        double x = player.xo + (player.getX() - player.xo) * partialTick;
        double y = player.yo + (player.getY() - player.yo) * partialTick;
        double z = player.zo + (player.getZ() - player.zo) * partialTick;
        float yaw = player.yRotO + (player.getYRot() - player.yRotO) * partialTick;

        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double height = player.getBbHeight() * 0.72d;
        double behind = 0.75d;
        double ringRadius = 0.9d;

        net.minecraft.world.phys.Vec3 camPos = camera.getPosition();

        double centerX = x - forwardX * behind;
        double centerY = y + height;
        double centerZ = z - forwardZ * behind;

        VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(new net.minecraft.resources.ResourceLocation("minecraft", "textures/block/black_concrete.png")));

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        double orbRadius = 0.18d;
        double bob = Math.sin(gameTime * 0.05d) * 0.04d;

        // latSegments/lonSegments now live in TruthSeekerOrbModel.

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0d * i / count) + (gameTime + partialTick) * 0.03d;
            double cos = Math.cos(angle) * ringRadius;
            double sin = Math.sin(angle) * ringRadius;
            double px = centerX + rightX * cos;
            double py = centerY + sin + bob;
            double pz = centerZ + rightZ * cos;

            TruthSeekerOrbModel.renderSphere(poseStack, consumer, px, py, pz, orbRadius, 0xF000F0);
        }

        poseStack.popPose();
    }
}