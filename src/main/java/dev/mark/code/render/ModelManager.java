package dev.mark.code.render;

import dev.mark.code.Main;
import dev.mark.code.config.Config;
import dev.mark.code.model.GltfBinaryLoader;
import dev.mark.code.model.GltfModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

public class ModelManager {
    private static final Map<Config.Models, ModelRenderer> CACHE = new EnumMap<>(Config.Models.class);

    public static ModelRenderer get(Config.Models preset) {
        ModelRenderer cached = CACHE.get(preset);
        if (cached != null) return cached;

        Identifier resourceId = Identifier.of(Main.MOD_ID, "models/" + preset.resourcePath);
        try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(resourceId)) {
            GltfModel model = GltfBinaryLoader.load(stream);
            ModelRenderer renderer = new ModelRenderer(preset.name().toLowerCase(), model);
            CACHE.put(preset, renderer);
            return renderer;
        } catch (Exception ex) {
            Main.LOGGER.error("Failed to load model preset {}", preset, ex);
            return null;
        }
    }

    public static void unloadAll() {
        for (ModelRenderer renderer : CACHE.values()) {
            renderer.close();
        }
        CACHE.clear();
    }
}
