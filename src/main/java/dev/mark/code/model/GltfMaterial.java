package dev.mark.code.model;

public record GltfMaterial(byte[] baseColorImage, String baseColorMime, float[] baseColorFactor) {
    public static GltfMaterial defaults() {
        return new GltfMaterial(null, null, new float[]{1f, 1f, 1f, 1f});
    }
}
