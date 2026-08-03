package com.runeprofile.modelexporter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlbWriterTest {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    /** A quad split into two triangles: one plain, one mapped to texture 5. */
    private static MeshData sampleMesh() {
        final float[] positions = {
                0, 0, 0, 10, 0, 0, 0, 10, 0,
                10, 0, 0, 10, 10, 0, 0, 10, 0,
        };
        final byte[] colors = new byte[6 * 4];
        for (int i = 0; i < 6; i++) {
            colors[i * 4] = (byte) 200;
            colors[i * 4 + 1] = (byte) 120;
            colors[i * 4 + 2] = (byte) 60;
            colors[i * 4 + 3] = (byte) 255;
        }
        final float[] uvs = {0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 0, 1};
        final int[] indices = {0, 1, 2, 3, 4, 5};

        final List<MeshData.Primitive> primitives = new ArrayList<>();
        primitives.add(new MeshData.Primitive(MeshData.NO_TEXTURE, 0, 3, false));
        primitives.add(new MeshData.Primitive(5, 3, 3, false));

        return new MeshData(positions, colors, null, uvs, indices, primitives,
                new float[]{0, 0, 0}, new float[]{10, 10, 0});
    }

    private static GameTextures.TextureData sampleTexture() throws IOException {
        final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                image.setRGB(x, y, 0xffff6600);
            }
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        // Scrolling upwards, like the animated textures on a fire cape.
        return new GameTextures.TextureData(5, out.toByteArray(), 0xff6600, 0f, 1.95f);
    }

    private static GlbWriter.TextureLookup lookup(GameTextures.TextureData texture) {
        return id -> id == 5 ? texture : null;
    }

    @Test
    public void writesAValidGlbContainer() throws IOException {
        final byte[] glb = GlbWriter.write(sampleMesh(), lookup(sampleTexture()),
                new GlbWriter.Options().modelName("test"));

        final ByteBuffer buffer = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(GLB_MAGIC, buffer.getInt());
        assertEquals(2, buffer.getInt());
        assertEquals("Header length must match the actual file size", glb.length, buffer.getInt());

        final int jsonLength = buffer.getInt();
        assertEquals(CHUNK_JSON, buffer.getInt());
        assertEquals("JSON chunk must be four byte aligned", 0, jsonLength % 4);
        buffer.position(buffer.position() + jsonLength);

        final int binLength = buffer.getInt();
        assertEquals(CHUNK_BIN, buffer.getInt());
        assertEquals("Binary chunk must be four byte aligned", 0, binLength % 4);
        assertEquals("Chunks must exactly fill the file",
                glb.length, buffer.position() + binLength);
    }

    @Test
    public void bufferViewsStayInsideTheBufferAndStayAligned() throws IOException {
        final JsonObject gltf = parseJson(GlbWriter.write(sampleMesh(), lookup(sampleTexture()),
                new GlbWriter.Options()));

        final int bufferLength = gltf.getAsJsonArray("buffers").get(0)
                .getAsJsonObject().get("byteLength").getAsInt();

        final JsonArray views = gltf.getAsJsonArray("bufferViews");
        assertTrue("Expected position, colour, UV, index and image views",
                views.size() >= 5);

        for (int i = 0; i < views.size(); i++) {
            final JsonObject view = views.get(i).getAsJsonObject();
            final int offset = view.get("byteOffset").getAsInt();
            final int length = view.get("byteLength").getAsInt();
            assertEquals("bufferView " + i + " must be four byte aligned", 0, offset % 4);
            assertTrue("bufferView " + i + " runs past the end of the buffer",
                    offset + length <= bufferLength);
        }
    }

    @Test
    public void onlyTexturedPrimitivesDeclareUvs() throws IOException {
        final JsonObject gltf = parseJson(GlbWriter.write(sampleMesh(), lookup(sampleTexture()),
                new GlbWriter.Options()));

        final JsonArray primitives = gltf.getAsJsonArray("meshes").get(0).getAsJsonObject()
                .getAsJsonArray("primitives");
        assertEquals(2, primitives.size());

        final JsonObject plain = primitives.get(0).getAsJsonObject().getAsJsonObject("attributes");
        assertTrue(plain.has("POSITION"));
        assertTrue(plain.has("COLOR_0"));
        assertFalse("An untextured primitive should not carry UVs", plain.has("TEXCOORD_0"));

        final JsonObject textured = primitives.get(1).getAsJsonObject().getAsJsonObject("attributes");
        assertTrue(textured.has("TEXCOORD_0"));
        // Both primitives share one vertex buffer.
        assertEquals(plain.get("POSITION"), textured.get("POSITION"));
    }

    @Test
    public void embedsTexturesByDefault() throws IOException {
        final JsonObject gltf = parseJson(GlbWriter.write(sampleMesh(), lookup(sampleTexture()),
                new GlbWriter.Options().embedTextures()));

        final JsonObject image = gltf.getAsJsonArray("images").get(0).getAsJsonObject();
        assertTrue("An embedded image lives in a bufferView", image.has("bufferView"));
        assertEquals("image/png", image.get("mimeType").getAsString());
        assertFalse(image.has("uri"));
    }

    @Test
    public void referencesTexturesByUrlWhenAsked() throws IOException {
        final JsonObject gltf = parseJson(GlbWriter.write(sampleMesh(), lookup(sampleTexture()),
                new GlbWriter.Options().textureUrls("https://cdn.runeprofile.com/texture/%d.png")));

        final JsonObject image = gltf.getAsJsonArray("images").get(0).getAsJsonObject();
        assertEquals("https://cdn.runeprofile.com/texture/5.png", image.get("uri").getAsString());
        assertFalse("A referenced image must not also be embedded", image.has("bufferView"));
    }

    @Test
    public void carriesScrollRateForAnimatedTextures() throws IOException {
        final JsonObject gltf = parseJson(GlbWriter.write(sampleMesh(), lookup(sampleTexture()),
                new GlbWriter.Options()));

        final JsonObject material = gltf.getAsJsonArray("materials").get(1).getAsJsonObject();
        final JsonObject extras = material.getAsJsonObject("extras").getAsJsonObject("runeprofile");
        assertEquals(5, extras.get("textureId").getAsInt());
        assertEquals(0f, extras.get("scrollU").getAsFloat(), 0.0001f);
        assertEquals(1.95f, extras.get("scrollV").getAsFloat(), 0.0001f);
    }

    @Test
    public void materialsAreUnlitBecauseTheGameDoesNotLightModels() throws IOException {
        final JsonObject gltf = parseJson(GlbWriter.write(sampleMesh(), lookup(sampleTexture()),
                new GlbWriter.Options()));

        for (int i = 0; i < gltf.getAsJsonArray("materials").size(); i++) {
            final JsonObject material = gltf.getAsJsonArray("materials").get(i).getAsJsonObject();
            assertTrue(material.getAsJsonObject("extensions").has("KHR_materials_unlit"));
            assertTrue(material.get("doubleSided").getAsBoolean());
        }
        assertEquals("KHR_materials_unlit",
                gltf.getAsJsonArray("extensionsUsed").get(0).getAsString());
    }

    @Test
    public void fallsBackToAverageColourWhenTheImageIsMissing() throws IOException {
        final GameTextures.TextureData broken =
                new GameTextures.TextureData(5, null, 0xff6600, 0f, 0f);

        final JsonObject gltf = parseJson(GlbWriter.write(sampleMesh(), lookup(broken),
                new GlbWriter.Options()));

        assertFalse("No image means no textures array", gltf.has("textures"));

        final JsonObject pbr = gltf.getAsJsonArray("materials").get(1).getAsJsonObject()
                .getAsJsonObject("pbrMetallicRoughness");
        assertFalse(pbr.has("baseColorTexture"));

        final JsonArray factor = pbr.getAsJsonArray("baseColorFactor");
        assertEquals(1f, factor.get(0).getAsFloat(), 0.01f);
        assertEquals(0x66 / 255f, factor.get(1).getAsFloat(), 0.01f);
        assertEquals(0f, factor.get(2).getAsFloat(), 0.01f);
    }

    /**
     * Depth bias and translucency both split a textured surface into several
     * primitives. Each needs its own material, but embedding the PNG once per
     * material would multiply the file size by the number of groups.
     */
    @Test
    public void embedsAsharedTextureOnlyOnce() throws IOException {
        final List<MeshData.Primitive> primitives = new ArrayList<>();
        primitives.add(new MeshData.Primitive(5, 0, 3, false));
        primitives.add(new MeshData.Primitive(5, 3, 3, false));

        final MeshData mesh = new MeshData(
                sampleMesh().getPositions(), sampleMesh().getColors(), null, sampleMesh().getUvs(),
                sampleMesh().getIndices(), primitives, new float[]{0, 0, 0}, new float[]{10, 10, 0});

        final JsonObject gltf = parseJson(
                GlbWriter.write(mesh, lookup(sampleTexture()), new GlbWriter.Options()));

        assertEquals("Two materials, one per bias group",
                2, gltf.getAsJsonArray("materials").size());
        assertEquals("But only one embedded image", 1, gltf.getAsJsonArray("images").size());
        assertEquals(1, gltf.getAsJsonArray("textures").size());

        for (int i = 0; i < 2; i++) {
            assertEquals(0, gltf.getAsJsonArray("materials").get(i).getAsJsonObject()
                    .getAsJsonObject("pbrMetallicRoughness")
                    .getAsJsonObject("baseColorTexture").get("index").getAsInt());
        }
    }

    /**
     * One node holding the mesh and the scale that converts game units to the
     * metres a viewer assumes.
     */
    @Test
    public void writesOneScaledNode() throws IOException {
        final JsonObject gltf = parseJson(
                GlbWriter.write(sampleMesh(), lookup(sampleTexture()), new GlbWriter.Options()));

        final JsonArray nodes = gltf.getAsJsonArray("nodes");
        assertEquals(1, nodes.size());
        assertEquals(1, gltf.getAsJsonArray("meshes").size());

        final JsonObject root = nodes.get(0).getAsJsonObject();
        assertEquals(0, root.get("mesh").getAsInt());
        assertEquals(1f / 128f, root.getAsJsonArray("scale").get(0).getAsFloat(), 1e-9);
    }

    @Test(expected = IOException.class)
    public void refusesToWriteAnEmptyModel() throws IOException {
        final MeshData empty = new MeshData(new float[0], new byte[0], null, null, new int[0],
                new ArrayList<>(), new float[]{0, 0, 0}, new float[]{0, 0, 0});
        GlbWriter.write(empty, id -> null, new GlbWriter.Options());
    }

    /**
     * Writes a sample model to build/ so a renderer can be worked on against a
     * real file without running the game - drop it on the admin model viewer.
     */
    @Test
    public void writesASampleForTheViewer() throws IOException {
        final byte[] glb = GlbWriter.write(sampleMesh(), lookup(sampleTexture()),
                new GlbWriter.Options().modelName("sample"));

        final File directory = new File("build/sample-models");
        assertTrue(directory.exists() || directory.mkdirs());
        try (OutputStream out = new FileOutputStream(new File(directory, "sample.glb"))) {
            out.write(glb);
        }
    }

    private static JsonObject parseJson(byte[] glb) {
        final ByteBuffer buffer = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(12);
        final int jsonLength = buffer.getInt();
        buffer.getInt();

        final byte[] json = new byte[jsonLength];
        buffer.get(json);
        return new JsonParser().parse(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
