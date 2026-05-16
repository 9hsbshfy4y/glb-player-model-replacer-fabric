package dev.mark.code.model;

public record GltfMesh(float[] positions, float[] normals, float[] texcoords, float[] colors, int[] indices, GltfMaterial material) {}
