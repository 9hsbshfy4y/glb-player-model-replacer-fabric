package dev.mark.code.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mark.code.Main;
import dev.mark.code.model.GltfMaterial;
import dev.mark.code.model.GltfMesh;
import dev.mark.code.model.GltfModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public class ModelRenderer implements AutoCloseable {

    private static final int FULL_LIGHT = LightmapTextureManager.pack(15, 15);
    private static final int NO_OVERLAY = OverlayTexture.DEFAULT_UV;

    private final List<UploadedMesh> meshes = new ArrayList<>();
    private final List<Identifier> registeredTextures = new ArrayList<>();
    private final float modelScale;
    private final float pivotOffsetY;

    public ModelRenderer(String idPrefix, GltfModel model) {
        float maxDim = Math.max(model.aabbMax()[0] - model.aabbMin()[0], Math.max(model.aabbMax()[1] - model.aabbMin()[1], model.aabbMax()[2] - model.aabbMin()[2]));
        this.modelScale = maxDim > 0f ? 1.8f / maxDim : 1f;
        this.pivotOffsetY = -model.aabbMin()[1];

        for (int i = 0; i < model.meshes().size(); i++) {
            GltfMesh mesh = model.meshes().get(i);
            Identifier textureId = registerTexture(idPrefix, i, mesh.material());
            VertexBuffer buffer = uploadMesh(mesh);
            RenderLayer layer = textureId != null
                    ? RenderLayer.getEntityCutout(textureId)
                    : RenderLayer.getEntitySolid(Identifier.ofVanilla("textures/misc/white.png"));
            meshes.add(new UploadedMesh(buffer, layer));
        }
    }

    public void render(MatrixStack matrices, float bodyYawDeg, float forwardOffset, float leftOffset, float yawOffsetDeg, float scaleMultiplier) {
        matrices.push();
        applyWorldOffset(matrices, bodyYawDeg, forwardOffset, leftOffset);
        float effectiveScale = modelScale * scaleMultiplier;
        matrices.scale(effectiveScale, effectiveScale, effectiveScale);
        matrices.translate(0f, pivotOffsetY, 0f);
        matrices.multiply(new Quaternionf().rotateY((float) Math.toRadians(yawOffsetDeg - bodyYawDeg)));

        Matrix4fStack viewStack = RenderSystem.getModelViewStack();
        viewStack.pushMatrix();
        viewStack.mul(matrices.peek().getPositionMatrix());

        for (UploadedMesh mesh : meshes) {
            mesh.buffer.bind();
            mesh.buffer.draw(mesh.layer);
        }
        VertexBuffer.unbind();

        viewStack.popMatrix();
        matrices.pop();
    }

    private static void applyWorldOffset(MatrixStack matrices, float bodyYawDeg, float forward, float left) {
        if (forward == 0f && left == 0f) return;
        float yawRad = (float) Math.toRadians(bodyYawDeg);
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);
        matrices.translate(-sin * forward - cos * left, 0f, cos * forward - sin * left);
    }

    private Identifier registerTexture(String idPrefix, int meshIndex, GltfMaterial material) {
        if (material.baseColorImage() == null) return null;
        try {
            NativeImage image = ImageDecoder.decode(material.baseColorImage());
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier id = Identifier.of(Main.MOD_ID, idPrefix + "_tex_" + meshIndex);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            registeredTextures.add(id);
            return id;
        } catch (Exception ex) {
            Main.LOGGER.warn("Failed to decode texture for mesh {}", meshIndex, ex);
            return null;
        }
    }

    private static VertexBuffer uploadMesh(GltfMesh mesh) {
        return VertexBuffer.createAndUpload(
                VertexFormat.DrawMode.TRIANGLES,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                consumer -> {
                    float[] factor = mesh.material().baseColorFactor();
                    int fr = clampToByte(factor[0]);
                    int fg = clampToByte(factor[1]);
                    int fb = clampToByte(factor[2]);
                    int fa = clampToByte(factor[3]);
                    boolean hasVertexColors = mesh.colors() != null;

                    for (int index : mesh.indices()) {
                        int r = fr, g = fg, b = fb, a = fa;
                        if (hasVertexColors) {
                            r = clampToByte(mesh.colors()[index * 4]);
                            g = clampToByte(mesh.colors()[index * 4 + 1]);
                            b = clampToByte(mesh.colors()[index * 4 + 2]);
                            a = clampToByte(mesh.colors()[index * 4 + 3]);
                        }
                        consumer.vertex(
                                        mesh.positions()[index * 3],
                                        mesh.positions()[index * 3 + 1],
                                        mesh.positions()[index * 3 + 2])
                                .color(r, g, b, a)
                                .texture(mesh.texcoords()[index * 2], mesh.texcoords()[index * 2 + 1])
                                .overlay(NO_OVERLAY)
                                .light(FULL_LIGHT)
                                .normal(
                                        mesh.normals()[index * 3],
                                        mesh.normals()[index * 3 + 1],
                                        mesh.normals()[index * 3 + 2]);
                    }
                });
    }

    private static int clampToByte(float v) {
        if (v <= 0f) return 0;
        if (v >= 1f) return 255;
        return (int) (v * 255f);
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (UploadedMesh mesh : meshes) {
            mesh.buffer.close();
        }
        meshes.clear();
        for (Identifier id : registeredTextures) {
            client.getTextureManager().destroyTexture(id);
        }
        registeredTextures.clear();
    }

    private record UploadedMesh(VertexBuffer buffer, RenderLayer layer) {}
}
