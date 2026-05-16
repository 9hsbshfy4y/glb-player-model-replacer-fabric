package dev.mark.code.model;

import java.util.List;

public record GltfModel(List<GltfMesh> meshes, float[] aabbMin, float[] aabbMax) {}
