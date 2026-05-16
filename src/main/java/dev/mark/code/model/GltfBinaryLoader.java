package dev.mark.code.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("SpellCheckingInspection")
public class GltfBinaryLoader {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private static final int COMP_UBYTE = 5121;
    private static final int COMP_USHORT = 5123;
    private static final int COMP_UINT = 5125;
    private static final int COMP_FLOAT = 5126;

    private static final int PRIMITIVE_TRIANGLES = 4;

    public static GltfModel load(InputStream input) throws IOException {
        byte[] data = input.readAllBytes();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int magic = buf.getInt();
        if (magic != GLB_MAGIC) throw new IOException("Not a GLB: bad magic 0x" + Integer.toHexString(magic));
        int version = buf.getInt();
        if (version != 2) throw new IOException("Unsupported GLB version: " + version);
        buf.getInt();

        int jsonLength = buf.getInt();
        int jsonType = buf.getInt();
        if (jsonType != CHUNK_JSON) throw new IOException("Expected JSON chunk");
        byte[] jsonBytes = new byte[jsonLength];
        buf.get(jsonBytes);
        JsonObject root = JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8).trim()).getAsJsonObject();

        byte[] binary = new byte[0];
        if (buf.hasRemaining()) {
            int binLength = buf.getInt();
            int binType = buf.getInt();
            if (binType != CHUNK_BIN) throw new IOException("Expected BIN chunk");
            binary = new byte[binLength];
            buf.get(binary);
        }

        return parse(root, binary);
    }

    private static GltfModel parse(JsonObject root, byte[] binary) {
        JsonArray accessors = root.getAsJsonArray("accessors");
        JsonArray bufferViews = root.getAsJsonArray("bufferViews");
        JsonArray meshArray = optionalArray(root, "meshes");
        JsonArray materialArray = optionalArray(root, "materials");
        JsonArray textureArray = optionalArray(root, "textures");
        JsonArray imageArray = optionalArray(root, "images");

        List<byte[]> imageData = new ArrayList<>(imageArray.size());
        List<String> imageMime = new ArrayList<>(imageArray.size());
        for (JsonElement element : imageArray) {
            JsonObject image = element.getAsJsonObject();
            String mime = image.has("mimeType") ? image.get("mimeType").getAsString() : "image/png";
            imageMime.add(mime);
            if (image.has("bufferView")) {
                JsonObject view = bufferViews.get(image.get("bufferView").getAsInt()).getAsJsonObject();
                imageData.add(sliceBufferView(view, binary));
            } else {
                imageData.add(null);
            }
        }

        List<GltfMesh> meshes = new ArrayList<>();
        float[] aabbMin = {
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY},
                aabbMax = {
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY};

        for (JsonElement meshElement : meshArray) {
            for (JsonElement primElement : meshElement.getAsJsonObject().getAsJsonArray("primitives")) {
                JsonObject primitive = primElement.getAsJsonObject();
                int mode = primitive.has("mode") ? primitive.get("mode").getAsInt() : PRIMITIVE_TRIANGLES;
                if (mode != PRIMITIVE_TRIANGLES) continue;

                JsonObject attributes = primitive.getAsJsonObject("attributes");
                if (!attributes.has("POSITION")) continue;

                GltfMesh mesh = parsePrimitive(primitive, attributes, accessors, bufferViews, binary, materialArray, textureArray, imageData, imageMime);
                meshes.add(mesh);
                expandBounds(mesh.positions(), aabbMin, aabbMax);
            }
        }

        return new GltfModel(meshes, aabbMin, aabbMax);
    }

    private static GltfMesh parsePrimitive(JsonObject primitive, JsonObject attributes,
                                           JsonArray accessors, JsonArray bufferViews, byte[] binary,
                                           JsonArray materials, JsonArray textures,
                                           List<byte[]> imageData, List<String> imageMime) {
        float[] positions = readFloats(accessors.get(attributes.get("POSITION").getAsInt()).getAsJsonObject(), bufferViews, binary, 3);
        int vertexCount = positions.length / 3;

        float[] normals = attributes.has("NORMAL")
                ? readFloats(accessors.get(attributes.get("NORMAL").getAsInt()).getAsJsonObject(), bufferViews, binary, 3)
                : computeFlatNormals(positions);

        float[] texcoords = attributes.has("TEXCOORD_0")
                ? readFloats(accessors.get(attributes.get("TEXCOORD_0").getAsInt()).getAsJsonObject(), bufferViews, binary, 2)
                : new float[vertexCount * 2];

        float[] colors = null;
        if (attributes.has("COLOR_0")) {
            JsonObject colorAccessor = accessors.get(attributes.get("COLOR_0").getAsInt()).getAsJsonObject();
            int components = "VEC4".equals(colorAccessor.get("type").getAsString()) ? 4 : 3;
            float[] raw = readColors(colorAccessor, bufferViews, binary, components);
            colors = components == 4 ? raw : expandRgbToRgba(raw);
        }

        int[] indices;
        if (primitive.has("indices")) {
            indices = readIndices(accessors.get(primitive.get("indices").getAsInt()).getAsJsonObject(), bufferViews, binary);
        } else {
            indices = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) indices[i] = i;
        }

        GltfMaterial material = parseMaterial(primitive, materials, textures, imageData, imageMime);
        return new GltfMesh(positions, normals, texcoords, colors, indices, material);
    }

    private static GltfMaterial parseMaterial(JsonObject primitive, JsonArray materials, JsonArray textures, List<byte[]> imageData, List<String> imageMime) {
        if (!primitive.has("material")) return GltfMaterial.defaults();
        JsonObject material = materials.get(primitive.get("material").getAsInt()).getAsJsonObject();
        if (!material.has("pbrMetallicRoughness")) return GltfMaterial.defaults();

        JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
        float[] factor = {1f, 1f, 1f, 1f};
        if (pbr.has("baseColorFactor")) {
            JsonArray bc = pbr.getAsJsonArray("baseColorFactor");
            factor[0] = bc.get(0).getAsFloat();
            factor[1] = bc.get(1).getAsFloat();
            factor[2] = bc.get(2).getAsFloat();
            factor[3] = bc.size() > 3 ? bc.get(3).getAsFloat() : 1f;
        }

        byte[] image = null;
        String mime = null;
        if (pbr.has("baseColorTexture")) {
            int textureIndex = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
            JsonObject texture = textures.get(textureIndex).getAsJsonObject();
            if (texture.has("source")) {
                int imageIndex = texture.get("source").getAsInt();
                if (imageIndex >= 0 && imageIndex < imageData.size()) {
                    image = imageData.get(imageIndex);
                    mime = imageMime.get(imageIndex);
                }
            }
        }
        return new GltfMaterial(image, mime, factor);
    }

    private static byte[] sliceBufferView(JsonObject view, byte[] binary) {
        int offset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
        int length = view.get("byteLength").getAsInt();
        byte[] result = new byte[length];
        System.arraycopy(binary, offset, result, 0, length);
        return result;
    }

    private static float[] readFloats(JsonObject accessor, JsonArray bufferViews, byte[] binary, int components) {
        int count = accessor.get("count").getAsInt();
        int viewIndex = accessor.get("bufferView").getAsInt();
        int accessorOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        JsonObject view = bufferViews.get(viewIndex).getAsJsonObject();
        int viewOffset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : components * 4;

        float[] result = new float[count * components];
        ByteBuffer buf = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int base = viewOffset + accessorOffset + i * stride;
            for (int c = 0; c < components; c++) {
                result[i * components + c] = buf.getFloat(base + c * 4);
            }
        }
        return result;
    }

    private static int[] readIndices(JsonObject accessor, JsonArray bufferViews, byte[] binary) {
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();
        int viewIndex = accessor.get("bufferView").getAsInt();
        int accessorOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        JsonObject view = bufferViews.get(viewIndex).getAsJsonObject();
        int viewOffset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
        int componentSize = sizeOf(componentType);
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : componentSize;

        int[] result = new int[count];
        ByteBuffer buf = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int base = viewOffset + accessorOffset + i * stride;
            result[i] = switch (componentType) {
                case COMP_UBYTE -> buf.get(base) & 0xFF;
                case COMP_USHORT -> buf.getShort(base) & 0xFFFF;
                case COMP_UINT -> buf.getInt(base);
                default -> throw new IllegalArgumentException("Unsupported index type: " + componentType);
            };
        }
        return result;
    }

    private static float[] readColors(JsonObject accessor, JsonArray bufferViews, byte[] binary, int components) {
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();
        int viewIndex = accessor.get("bufferView").getAsInt();
        int accessorOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        JsonObject view = bufferViews.get(viewIndex).getAsJsonObject();
        int viewOffset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
        int componentSize = sizeOf(componentType);
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : componentSize * components;

        float[] result = new float[count * components];
        ByteBuffer buf = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int base = viewOffset + accessorOffset + i * stride;
            for (int c = 0; c < components; c++) {
                int offset = base + c * componentSize;
                result[i * components + c] = switch (componentType) {
                    case COMP_UBYTE -> (buf.get(offset) & 0xFF) / 255f;
                    case COMP_USHORT -> (buf.getShort(offset) & 0xFFFF) / 65535f;
                    case COMP_FLOAT -> buf.getFloat(offset);
                    default -> 0f;
                };
            }
        }
        return result;
    }

    private static float[] expandRgbToRgba(float[] rgb) {
        int n = rgb.length / 3;
        float[] rgba = new float[n * 4];
        for (int i = 0; i < n; i++) {
            rgba[i * 4] = rgb[i * 3];
            rgba[i * 4 + 1] = rgb[i * 3 + 1];
            rgba[i * 4 + 2] = rgb[i * 3 + 2];
            rgba[i * 4 + 3] = 1f;
        }
        return rgba;
    }

    private static float[] computeFlatNormals(float[] positions) {
        float[] result = new float[positions.length];
        for (int i = 0; i + 9 <= positions.length; i += 9) {
            float ax = positions[i + 3] - positions[i];
            float ay = positions[i + 4] - positions[i + 1];
            float az = positions[i + 5] - positions[i + 2];
            float bx = positions[i + 6] - positions[i];
            float by = positions[i + 7] - positions[i + 1];
            float bz = positions[i + 8] - positions[i + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 0f) { nx /= length; ny /= length; nz /= length; }
            for (int v = 0; v < 3; v++) {
                result[i + v * 3] = nx;
                result[i + v * 3 + 1] = ny;
                result[i + v * 3 + 2] = nz;
            }
        }
        return result;
    }

    private static void expandBounds(float[] positions, float[] min, float[] max) {
        for (int i = 0; i + 3 <= positions.length; i += 3) {
            if (positions[i]     < min[0]) min[0] = positions[i];
            if (positions[i + 1] < min[1]) min[1] = positions[i + 1];
            if (positions[i + 2] < min[2]) min[2] = positions[i + 2];
            if (positions[i]     > max[0]) max[0] = positions[i];
            if (positions[i + 1] > max[1]) max[1] = positions[i + 1];
            if (positions[i + 2] > max[2]) max[2] = positions[i + 2];
        }
    }

    private static int sizeOf(int componentType) {
        return switch (componentType) {
            case COMP_UBYTE -> 1;
            case COMP_USHORT -> 2;
            case COMP_UINT, COMP_FLOAT -> 4;
            default -> throw new IllegalArgumentException("Unsupported component type: " + componentType);
        };
    }

    private static JsonArray optionalArray(JsonObject parent, String name) {
        return parent.has(name) ? parent.getAsJsonArray(name) : new JsonArray();
    }
}
