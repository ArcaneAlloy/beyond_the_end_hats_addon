package com.endersjourney.btesimplehats.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;

/**
 * Dibuja un item en la GUI como ItemRenderer.renderGuiItem (1.19.2), pero
 * con escala y con la opcion de pintarlo como silueta negra.
 * renderGuiItem resetea el color del shader a blanco, por eso no vale
 * simplemente teñir antes de llamarlo.
 */
public final class ClientRender {

    public static void renderItem(ItemStack stack, int x, int y, float scale, boolean silhouette) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, null, 0);

        mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).setFilter(false, false);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (silhouette) {
            RenderSystem.setShaderColor(0.0f, 0.0f, 0.0f, 0.85f);
        } else {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.translate(x, y, 100.0f + itemRenderer.blitOffset);
        modelView.translate(8.0f * scale, 8.0f * scale, 0.0f);
        modelView.scale(1.0f, -1.0f, 1.0f);
        modelView.scale(16.0f * scale, 16.0f * scale, 16.0f * scale);
        RenderSystem.applyModelViewMatrix();

        PoseStack pose = new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean flat = !model.usesBlockLight();
        if (flat) {
            Lighting.setupForFlatItems();
        }
        itemRenderer.render(stack, ItemTransforms.TransformType.GUI, false, pose, buffers, 15728880, OverlayTexture.NO_OVERLAY, model);
        buffers.endBatch();
        RenderSystem.enableDepthTest();
        if (flat) {
            Lighting.setupFor3DItems();
        }

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private ClientRender() {}
}
