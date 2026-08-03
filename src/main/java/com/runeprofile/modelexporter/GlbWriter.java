package com.runeprofile.modelexporter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Serialises a {@link MeshData} to a binary glTF (.glb) file.
 * <p>
 * The output is one mesh whose primitives all share a single vertex buffer,
 * one per group of faces that can be drawn with the same material. Materials
 * are unlit, because the game does no lighting: the colour you see is the
 * vertex colour multiplied by the texel, which is exactly what glTF's base
 * colour factor, base colour texture and COLOR_0 attribute multiply out to.
 * <p>
 * Textures are either embedded in the file, which makes a single self contained
 * model that any glTF viewer will open, or referenced by URL so they can be
 * served once from a CDN and shared across every profile.
 */
public final class GlbWriter {
    private static final int GLB_MAGIC = 0x46546C67; // "glTF"
    private static final int GLB_VERSION = 2;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private static final int TARGET_ARRAY_BUFFER = 34962;
    private static final int TARGET_ELEMENT_ARRAY_BUFFER = 34963;

    private static final int COMPONENT_UNSIGNED_BYTE = 5121;
    private static final int COMPONENT_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_UNSIGNED_INT = 5125;
    private static final int COMPONENT_FLOAT = 5126;

    private static final int FILTER_NEAREST = 9728;
    private static final int FILTER_LINEAR_MIPMAP_LINEAR = 9987;
    private static final int WRAP_REPEAT = 10497;

    /**
     * Game models are around 128 units per tile. Scaling the root node rather
     * than the vertex data keeps positions as exact small integers and still
     * gives a roughly human sized character in any viewer that assumes metres.
     */
    private static final float UNITS_PER_METRE = 128f;

    /** UNSIGNED_SHORT indices stop here; 65535 is reserved as a restart value. */
    private static final int MAX_SHORT_INDEX = 65535;

    /** How the file should refer to its textures. */
    public static final class Options {
        private boolean embedTextures = true;
        private String textureUrlTemplate;
        private String modelName = "model";
        private Map<String, Object> extras;

        /** Embed texture PNGs in the file. Self contained but not shared between models. */
        public Options embedTextures() {
            this.embedTextures = true;
            this.textureUrlTemplate = null;
            return this;
        }

        /**
         * Reference textures by URL instead of embedding them. The template
         * takes the texture id, e.g. {@code https://cdn.example.com/texture/%d.png}.
         */
        public Options textureUrls(@NonNull String template) {
            this.embedTextures = false;
            this.textureUrlTemplate = template;
            return this;
        }

        public Options modelName(@NonNull String modelName) {
            this.modelName = modelName;
            return this;
        }

        /** Arbitrary metadata to attach to the glTF asset. */
        public Options extras(@Nullable Map<String, Object> extras) {
            this.extras = extras;
            return this;
        }
    }

    private GlbWriter() {
    }

    public static byte[] write(@NonNull MeshData mesh, @NonNull TextureLookup textures,
                               @NonNull Options options) throws IOException {
        if (mesh.isEmpty()) {
            throw new IOException("Refusing to write an empty model");
        }

        final Gson gson = new Gson();
        final BinaryChunk bin = new BinaryChunk();

        final JsonArray bufferViews = new JsonArray();
        final JsonArray accessors = new JsonArray();
        final JsonArray materials = new JsonArray();
        final JsonArray gltfTextures = new JsonArray();
        final JsonArray images = new JsonArray();
        // Several primitives can share a texture: the same cape is split by
        // translucency. Each needs its own material but they must all point at
        // one image, or the PNG is embedded once per group.
        final Map<Integer, Integer> textureIndexById = new HashMap<>();

        // Vertex attributes, shared by every primitive.
        final int positionAccessor = addAccessor(bufferViews, accessors, bin,
                floatsToBytes(mesh.getPositions()), TARGET_ARRAY_BUFFER,
                COMPONENT_FLOAT, "VEC3", mesh.getVertexCount(), mesh.getMin(), mesh.getMax(), false);

        final int colorAccessor = addAccessor(bufferViews, accessors, bin,
                mesh.getColors(), TARGET_ARRAY_BUFFER,
                COMPONENT_UNSIGNED_BYTE, "VEC4", mesh.getVertexCount(), null, null, true);

        final int uvAccessor = mesh.getUvs() == null ? -1 : addAccessor(bufferViews, accessors, bin,
                floatsToBytes(mesh.getUvs()), TARGET_ARRAY_BUFFER,
                COMPONENT_FLOAT, "VEC2", mesh.getVertexCount(), null, null, false);

        // Face render priority travels per vertex under a custom attribute name,
        // so a renderer can offset by it without the exporter having to split
        // primitives - which translucency cannot afford, since all of it has to
        // share one mesh to be sorted.
        final int priorityAccessor = mesh.getPriorities() == null ? -1
                : addAccessor(bufferViews, accessors, bin,
                        floatsToBytes(mesh.getPriorities()), TARGET_ARRAY_BUFFER,
                        COMPONENT_FLOAT, "SCALAR", mesh.getVertexCount(), null, null, false);

        // One index buffer view for the whole mesh; each primitive gets an
        // accessor pointing at its own slice of it.
        final boolean wideIndices = mesh.getVertexCount() > MAX_SHORT_INDEX;
        final int indexComponentType = wideIndices ? COMPONENT_UNSIGNED_INT : COMPONENT_UNSIGNED_SHORT;
        final int indexStride = wideIndices ? 4 : 2;
        final int indexBufferView = addBufferView(bufferViews, bin,
                indicesToBytes(mesh.getIndices(), wideIndices), TARGET_ELEMENT_ARRAY_BUFFER, 0);

        final JsonArray primitives = new JsonArray();

        for (MeshData.Primitive primitive : mesh.getPrimitives()) {
            final GameTextures.TextureData texture = primitive.isTextured()
                    ? textures.get(primitive.getTextureId())
                    : null;

            final JsonObject accessor = new JsonObject();
            accessor.addProperty("bufferView", indexBufferView);
            accessor.addProperty("byteOffset", primitive.getIndexOffset() * indexStride);
            accessor.addProperty("componentType", indexComponentType);
            accessor.addProperty("count", primitive.getIndexCount());
            accessor.addProperty("type", "SCALAR");
            accessors.add(accessor);
            final int indexAccessor = accessors.size() - 1;

            // A texture whose pixels could not be read still contributes its
            // average colour to the material, so the surface degrades to a flat
            // approximation instead of the bare grey the lightness alone gives.
            final boolean hasImage = texture != null
                    && (options.embedTextures ? texture.getPng() != null : true);

            final JsonObject attributes = new JsonObject();
            attributes.addProperty("POSITION", positionAccessor);
            attributes.addProperty("COLOR_0", colorAccessor);
            // Only textured primitives declare UVs, even though the accessor is
            // shared, so an untextured primitive costs nothing extra.
            if (hasImage && uvAccessor != -1) {
                attributes.addProperty("TEXCOORD_0", uvAccessor);
            }
            if (priorityAccessor != -1) {
                attributes.addProperty("_PRIORITY", priorityAccessor);
            }

            final JsonObject gltfPrimitive = new JsonObject();
            gltfPrimitive.add("attributes", attributes);
            gltfPrimitive.addProperty("indices", indexAccessor);
            gltfPrimitive.addProperty("material", materials.size());
            primitives.add(gltfPrimitive);

            int textureIndex = -1;
            if (hasImage) {
                Integer existing = textureIndexById.get(texture.getId());
                if (existing == null) {
                    gltfTextures.add(buildTexture(images.size()));
                    images.add(buildImage(texture, options, bufferViews, bin));
                    existing = gltfTextures.size() - 1;
                    textureIndexById.put(texture.getId(), existing);
                }
                textureIndex = existing;
            }

            materials.add(buildMaterial(primitive, texture, hasImage, textureIndex));
        }

        final JsonObject mesh0 = new JsonObject();
        mesh0.add("primitives", primitives);
        mesh0.addProperty("name", options.modelName);

        final float scale = 1f / UNITS_PER_METRE;
        final JsonObject node0 = new JsonObject();
        node0.addProperty("mesh", 0);
        node0.addProperty("name", options.modelName);
        node0.add("scale", jsonArray(scale, scale, scale));

        final JsonObject asset = new JsonObject();
        asset.addProperty("version", "2.0");
        asset.addProperty("generator", "RuneProfile");
        if (options.extras != null && !options.extras.isEmpty()) {
            asset.add("extras", gson.toJsonTree(options.extras));
        }

        final JsonObject gltf = new JsonObject();
        gltf.add("asset", asset);
        gltf.addProperty("scene", 0);
        gltf.add("scenes", singletonArray(sceneWithNode()));
        gltf.add("nodes", singletonArray(node0));
        gltf.add("meshes", singletonArray(mesh0));
        gltf.add("materials", materials);
        if (gltfTextures.size() > 0) {
            gltf.add("textures", gltfTextures);
            gltf.add("images", images);
            gltf.add("samplers", singletonArray(buildSampler()));
        }
        gltf.add("accessors", accessors);
        gltf.add("bufferViews", bufferViews);

        final JsonObject buffer = new JsonObject();
        buffer.addProperty("byteLength", bin.size());
        gltf.add("buffers", singletonArray(buffer));

        final JsonArray extensionsUsed = new JsonArray();
        extensionsUsed.add("KHR_materials_unlit");
        gltf.add("extensionsUsed", extensionsUsed);

        return assemble(gson.toJson(gltf), bin.toByteArray());
    }

    /** Resolves a texture id to its pixels; lets the writer stay free of client types. */
    public interface TextureLookup {
        @Nullable
        GameTextures.TextureData get(int textureId);
    }

    private static JsonObject sceneWithNode() {
        final JsonArray nodes = new JsonArray();
        nodes.add(0);
        final JsonObject scene = new JsonObject();
        scene.add("nodes", nodes);
        return scene;
    }

    private static JsonObject buildMaterial(MeshData.Primitive primitive,
                                            @Nullable GameTextures.TextureData texture,
                                            boolean hasImage,
                                            int textureIndex) {
        final JsonObject pbr = new JsonObject();
        if (hasImage) {
            pbr.add("baseColorFactor", jsonArray(1f, 1f, 1f, 1f));
            final JsonObject baseColorTexture = new JsonObject();
            baseColorTexture.addProperty("index", textureIndex);
            pbr.add("baseColorTexture", baseColorTexture);
        } else if (texture != null) {
            final int average = texture.getAverageColor();
            pbr.add("baseColorFactor", jsonArray(
                    ((average >> 16) & 0xff) / 255f,
                    ((average >> 8) & 0xff) / 255f,
                    (average & 0xff) / 255f,
                    1f));
        } else {
            pbr.add("baseColorFactor", jsonArray(1f, 1f, 1f, 1f));
        }
        pbr.addProperty("metallicFactor", 0f);
        pbr.addProperty("roughnessFactor", 1f);

        final JsonObject material = new JsonObject();
        material.addProperty("name", texture == null
                ? "untextured"
                : "texture-" + texture.getId());
        material.add("pbrMetallicRoughness", pbr);
        // The game does not light models, so an unlit material is not a
        // simplification here, it is the accurate one.
        material.add("extensions", unlitExtension());
        // Game models are routinely viewed from both sides and their winding is
        // not reliably outward, so culling would punch holes in them.
        material.addProperty("doubleSided", true);

        if (primitive.isTranslucent()) {
            material.addProperty("alphaMode", "BLEND");
        } else if (hasImage) {
            // Texture transparency is all or nothing in game: the renderer
            // discards fully transparent texels rather than blending them.
            material.addProperty("alphaMode", "MASK");
            material.addProperty("alphaCutoff", 0.5f);
        } else {
            material.addProperty("alphaMode", "OPAQUE");
        }

        // Neither scrolling UVs nor a depth bias is something glTF can express,
        // so both ride along as metadata for a renderer that knows to look.
        final JsonObject runeprofile = new JsonObject();
        if (hasImage && texture.isAnimated()) {
            runeprofile.addProperty("textureId", texture.getId());
            runeprofile.addProperty("scrollU", texture.getScrollU());
            runeprofile.addProperty("scrollV", texture.getScrollV());
        }
        if (runeprofile.size() > 0) {
            final JsonObject extras = new JsonObject();
            extras.add("runeprofile", runeprofile);
            material.add("extras", extras);
        }

        return material;
    }

    private static JsonObject unlitExtension() {
        final JsonObject extensions = new JsonObject();
        extensions.add("KHR_materials_unlit", new JsonObject());
        return extensions;
    }

    private static JsonObject buildTexture(int imageIndex) {
        final JsonObject texture = new JsonObject();
        texture.addProperty("sampler", 0);
        texture.addProperty("source", imageIndex);
        return texture;
    }

    private static JsonObject buildImage(GameTextures.TextureData texture, Options options,
                                         JsonArray bufferViews, BinaryChunk bin) throws IOException {
        final JsonObject image = new JsonObject();
        image.addProperty("name", "texture-" + texture.getId());
        if (options.embedTextures) {
            image.addProperty("bufferView", addBufferView(bufferViews, bin, texture.getPng(), -1, 0));
            image.addProperty("mimeType", "image/png");
        } else {
            image.addProperty("uri", String.format(options.textureUrlTemplate, texture.getId()));
        }
        return image;
    }

    private static JsonObject buildSampler() {
        final JsonObject sampler = new JsonObject();
        // Nearest magnification keeps the chunky look the game has; mipmapped
        // minification stops distant textures shimmering.
        sampler.addProperty("magFilter", FILTER_NEAREST);
        sampler.addProperty("minFilter", FILTER_LINEAR_MIPMAP_LINEAR);
        // Game UVs run outside 0..1 and rely on the texture repeating.
        sampler.addProperty("wrapS", WRAP_REPEAT);
        sampler.addProperty("wrapT", WRAP_REPEAT);
        return sampler;
    }

    private static int addAccessor(JsonArray bufferViews, JsonArray accessors, BinaryChunk bin,
                                   byte[] data, int target, int componentType, String type,
                                   int count, @Nullable float[] min, @Nullable float[] max,
                                   boolean normalized) throws IOException {
        final int bufferView = addBufferView(bufferViews, bin, data, target, 0);

        final JsonObject accessor = new JsonObject();
        accessor.addProperty("bufferView", bufferView);
        accessor.addProperty("componentType", componentType);
        accessor.addProperty("count", count);
        accessor.addProperty("type", type);
        if (normalized) {
            accessor.addProperty("normalized", true);
        }
        if (min != null) {
            accessor.add("min", jsonArray(min));
        }
        if (max != null) {
            accessor.add("max", jsonArray(max));
        }
        accessors.add(accessor);
        return accessors.size() - 1;
    }

    private static int addBufferView(JsonArray bufferViews, BinaryChunk bin, byte[] data,
                                     int target, int byteStride) throws IOException {
        final int offset = bin.append(data);

        final JsonObject bufferView = new JsonObject();
        bufferView.addProperty("buffer", 0);
        bufferView.addProperty("byteOffset", offset);
        bufferView.addProperty("byteLength", data.length);
        if (byteStride > 0) {
            bufferView.addProperty("byteStride", byteStride);
        }
        if (target > 0) {
            bufferView.addProperty("target", target);
        }
        bufferViews.add(bufferView);
        return bufferViews.size() - 1;
    }

    private static byte[] floatsToBytes(float[] values) {
        final ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private static byte[] indicesToBytes(int[] indices, boolean wide) {
        final ByteBuffer buffer = ByteBuffer
                .allocate(indices.length * (wide ? 4 : 2))
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int index : indices) {
            if (wide) {
                buffer.putInt(index);
            } else {
                buffer.putShort((short) index);
            }
        }
        return buffer.array();
    }

    private static JsonArray jsonArray(float... values) {
        final JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray singletonArray(JsonObject value) {
        final JsonArray array = new JsonArray();
        array.add(value);
        return array;
    }

    /**
     * Wraps the JSON and binary payloads in the GLB container. Both chunks are
     * padded to a four byte boundary, JSON with spaces and binary with zeroes,
     * as the format requires.
     */
    private static byte[] assemble(String json, byte[] binary) {
        final byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        final int jsonPadding = padding(jsonBytes.length);
        final int binaryPadding = padding(binary.length);

        final int jsonChunkLength = jsonBytes.length + jsonPadding;
        final int binaryChunkLength = binary.length + binaryPadding;
        final int totalLength = 12 + 8 + jsonChunkLength + 8 + binaryChunkLength;

        final ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(GLB_MAGIC);
        buffer.putInt(GLB_VERSION);
        buffer.putInt(totalLength);

        buffer.putInt(jsonChunkLength);
        buffer.putInt(CHUNK_JSON);
        buffer.put(jsonBytes);
        for (int i = 0; i < jsonPadding; i++) {
            buffer.put((byte) ' ');
        }

        buffer.putInt(binaryChunkLength);
        buffer.putInt(CHUNK_BIN);
        buffer.put(binary);
        for (int i = 0; i < binaryPadding; i++) {
            buffer.put((byte) 0);
        }

        return buffer.array();
    }

    private static int padding(int length) {
        return (4 - (length % 4)) % 4;
    }

    /**
     * The single binary buffer every buffer view points into. Appends are kept
     * four byte aligned, which every accessor component type we emit requires.
     */
    private static final class BinaryChunk {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        int append(byte[] data) throws IOException {
            for (int i = padding(out.size()); i > 0; i--) {
                out.write(0);
            }
            final int offset = out.size();
            out.write(data);
            return offset;
        }

        int size() {
            return out.size();
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
