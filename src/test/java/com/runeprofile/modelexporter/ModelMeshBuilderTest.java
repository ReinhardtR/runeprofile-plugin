package com.runeprofile.modelexporter;

import net.runelite.api.Model;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ModelMeshBuilderTest {
    /**
     * Two faces sharing an edge: one flat shaded and untextured, one textured.
     * Vertices are laid out in game space, where Y points down.
     */
    private static Model twoFaceModel() {
        return FakeModel.builder()
                .faceCount(2)
                .vertices(
                        new float[]{0, 10, 0, 10},
                        new float[]{0, 0, -10, -10},
                        new float[]{0, 0, 0, 0})
                .faceIndices(
                        new int[]{0, 1},
                        new int[]{1, 3},
                        new int[]{2, 2})
                .faceColors(
                        new int[]{100, 127},
                        new int[]{100, 127},
                        new int[]{-1, 127})
                .faceTextures(new short[]{-1, 5})
                .build();
    }

    @Test
    public void groupsFacesIntoOnePrimitivePerTexture() {
        final MeshData mesh = ModelMeshBuilder.build(twoFaceModel());
        final List<MeshData.Primitive> primitives = mesh.getPrimitives();

        assertEquals(2, primitives.size());

        // Untextured comes first so the bulk of a model is one primitive.
        assertFalse(primitives.get(0).isTextured());
        assertEquals(MeshData.NO_TEXTURE, primitives.get(0).getTextureId());
        assertEquals(0, primitives.get(0).getIndexOffset());
        assertEquals(3, primitives.get(0).getIndexCount());

        assertTrue(primitives.get(1).isTextured());
        assertEquals(5, primitives.get(1).getTextureId());
        assertEquals(3, primitives.get(1).getIndexOffset());
        assertEquals(3, primitives.get(1).getIndexCount());
    }

    @Test
    public void flipsYSoTheModelIsUpInGltfSpace() {
        final MeshData mesh = ModelMeshBuilder.build(twoFaceModel());
        final float[] positions = mesh.getPositions();

        // Game Y of -10 is ten units above the ground, so glTF Y must be +10.
        boolean sawPositiveY = false;
        for (int vertex = 0; vertex < mesh.getVertexCount(); vertex++) {
            final float y = positions[vertex * 3 + 1];
            assertTrue("Y should never be negative for this model", y >= 0f);
            if (y == 10f) {
                sawPositiveY = true;
            }
        }
        assertTrue(sawPositiveY);

        assertEquals(0f, mesh.getMin()[1], 0.0001f);
        assertEquals(10f, mesh.getMax()[1], 0.0001f);
    }

    @Test
    public void keepsWindingBecauseTheAxisMappingIsAProperRotation() {
        final MeshData mesh = ModelMeshBuilder.build(twoFaceModel());
        final int[] indices = mesh.getIndices();
        final float[] positions = mesh.getPositions();

        // Face 0 is game vertices (0, 1, 2). Negating Y and Z is a rotation, not
        // a reflection, so corners keep their order and the second index emitted
        // is the one built from game vertex 1 at x = 10.
        final int second = indices[1];
        assertEquals(10f, positions[second * 3], 0.0001f);
        assertEquals(0f, positions[second * 3 + 1], 0.0001f);
    }

    /**
     * The whole point of negating Z as well as Y. Mapping (X, -Y, Z) would have
     * a determinant of -1, mirroring the character back to front; (X, -Y, -Z) is
     * a 180 degree rotation about X and leaves it facing the right way.
     */
    @Test
    public void mapsAxesWithAProperRotationRatherThanAMirror() {
        final Model model = FakeModel.builder()
                .faceCount(1)
                // A vertex off every axis, so a sign error on any of them shows.
                .vertices(new float[]{7, 0, 0}, new float[]{0, -11, 0}, new float[]{0, 0, 13})
                .faceIndices(new int[]{0}, new int[]{1}, new int[]{2})
                .faceColors(new int[]{100}, new int[]{100}, new int[]{-1})
                .build();

        final float[] positions = ModelMeshBuilder.build(model).getPositions();
        final int[] indices = ModelMeshBuilder.build(model).getIndices();

        // Game vertex 0 is (7, 0, 0) -> unchanged on X.
        assertEquals(7f, positions[indices[0] * 3], 0.0001f);
        // Game vertex 1 is (0, -11, 0); Y down becomes Y up.
        assertEquals(11f, positions[indices[1] * 3 + 1], 0.0001f);
        // Game vertex 2 is (0, 0, 13); Z flips to preserve handedness.
        assertEquals(-13f, positions[indices[2] * 3 + 2], 0.0001f);
    }

    @Test
    public void turnsTexturedFaceLightnessIntoGrey() {
        final MeshData mesh = ModelMeshBuilder.build(twoFaceModel());
        final byte[] colors = mesh.getColors();
        final int[] indices = mesh.getIndices();

        // The textured primitive starts at index 3; its lightness of 127 is the
        // maximum, so it should expand to white rather than stay a dark grey.
        final int vertex = indices[3];
        assertEquals((byte) 255, colors[vertex * 4]);
        assertEquals((byte) 255, colors[vertex * 4 + 1]);
        assertEquals((byte) 255, colors[vertex * 4 + 2]);
        assertEquals((byte) 255, colors[vertex * 4 + 3]);
    }

    @Test
    public void emitsUvsOnlyWhenSomethingIsTextured() {
        assertNotNull(ModelMeshBuilder.build(twoFaceModel()).getUvs());

        final Model untextured = FakeModel.builder()
                .faceCount(1)
                .vertices(new float[]{0, 10, 0}, new float[]{0, 0, -10}, new float[]{0, 0, 0})
                .faceIndices(new int[]{0}, new int[]{1}, new int[]{2})
                .faceColors(new int[]{100}, new int[]{100}, new int[]{-1})
                .build();

        assertNull(ModelMeshBuilder.build(untextured).getUvs());
    }

    @Test
    public void mergesVerticesThatAgreeOnEverything() {
        // A single face drawn twice: the second copy must reuse all three
        // vertices rather than adding its own.
        final Model duplicated = FakeModel.builder()
                .faceCount(2)
                .vertices(new float[]{0, 10, 0}, new float[]{0, 0, -10}, new float[]{0, 0, 0})
                .faceIndices(new int[]{0, 0}, new int[]{1, 1}, new int[]{2, 2})
                .faceColors(new int[]{100, 100}, new int[]{100, 100}, new int[]{-1, -1})
                .build();

        final MeshData mesh = ModelMeshBuilder.build(duplicated);
        assertEquals(6, mesh.getIndices().length);
        assertEquals(3, mesh.getVertexCount());
    }

    /**
     * A third colour of -2 marks a face the game never draws. It is a sentinel,
     * not a colour, so keeping the face both shows geometry that should not
     * exist and paints it near white from reading -2 as a palette index.
     */
    @Test
    public void dropsFacesTheGameNeverDraws() {
        final Model model = FakeModel.builder()
                .faceCount(2)
                .vertices(
                        new float[]{0, 10, 0, 10},
                        new float[]{0, 0, -10, -10},
                        new float[]{0, 0, 0, 5})
                .faceIndices(new int[]{0, 1}, new int[]{1, 3}, new int[]{2, 2})
                .faceColors(new int[]{100, 100}, new int[]{100, 100}, new int[]{-1, -2})
                .build();

        final MeshData mesh = ModelMeshBuilder.build(model);
        assertEquals("Only the first face should survive", 3, mesh.getIndices().length);

        // Nothing near white should have been emitted; -2 read as a colour index
        // decodes to luminance 126 of 127.
        for (int vertex = 0; vertex < mesh.getVertexCount(); vertex++) {
            final int red = mesh.getColors()[vertex * 4] & 0xff;
            assertTrue("A near white vertex means -2 leaked through as a colour", red < 240);
        }
    }

    @Test
    public void dropsFullyTransparentFaces() {
        final Model model = FakeModel.builder()
                .faceCount(2)
                .vertices(
                        new float[]{0, 10, 0, 10},
                        new float[]{0, 0, -10, -10},
                        new float[]{0, 0, 0, 0})
                .faceIndices(new int[]{0, 1}, new int[]{1, 3}, new int[]{2, 2})
                .faceColors(new int[]{100, 100}, new int[]{100, 100}, new int[]{-1, -1})
                // 255 means invisible, so only the first face survives.
                .faceTransparencies(new byte[]{0, (byte) 255})
                .build();

        final MeshData mesh = ModelMeshBuilder.build(model);
        assertEquals(3, mesh.getIndices().length);
        assertEquals(1, mesh.getPrimitives().size());
        assertFalse(mesh.getPrimitives().get(0).isTranslucent());
    }

    @Test
    public void marksPartiallyTransparentRunsAsTranslucent() {
        final Model model = FakeModel.builder()
                .faceCount(1)
                .vertices(new float[]{0, 10, 0}, new float[]{0, 0, -10}, new float[]{0, 0, 0})
                .faceIndices(new int[]{0}, new int[]{1}, new int[]{2})
                .faceColors(new int[]{100}, new int[]{100}, new int[]{-1})
                .faceTransparencies(new byte[]{(byte) 128})
                .build();

        final MeshData mesh = ModelMeshBuilder.build(model);
        assertTrue(mesh.getPrimitives().get(0).isTranslucent());
        assertEquals((byte) 127, mesh.getColors()[3]);
    }

    /**
     * A few see through faces must not drag opaque geometry into a blended
     * material: a renderer draws a double sided blended mesh in two passes, so
     * mixing them roughly doubles the cost of the whole model.
     */
    @Test
    public void separatesTranslucentFacesFromOpaqueOnes() {
        final Model model = FakeModel.builder()
                .faceCount(3)
                .vertices(
                        new float[]{0, 10, 0, 10},
                        new float[]{0, 0, -10, -10},
                        new float[]{0, 0, 0, 5})
                .faceIndices(new int[]{0, 1, 0}, new int[]{1, 3, 2}, new int[]{2, 2, 3})
                .faceColors(
                        new int[]{100, 100, 100},
                        new int[]{100, 100, 100},
                        new int[]{-1, -1, -1})
                // Middle face is half transparent, the other two are solid.
                .faceTransparencies(new byte[]{0, (byte) 128, 0})
                .build();

        final List<MeshData.Primitive> primitives = ModelMeshBuilder.build(model).getPrimitives();
        assertEquals(2, primitives.size());

        // Opaque first, and it keeps both solid faces together.
        assertFalse(primitives.get(0).isTranslucent());
        assertEquals(6, primitives.get(0).getIndexCount());

        assertTrue(primitives.get(1).isTranslucent());
        assertEquals(3, primitives.get(1).getIndexCount());
    }

    /**
     * Priority travels per vertex so it never splits a primitive - translucent
     * geometry has to stay in one mesh to be sorted, and a per material offset
     * would silently apply to opaque faces only, reordering them against
     * translucent ones.
     */
    @Test
    public void carriesPriorityPerVertexWithoutSplitting() {
        final Model model = FakeModel.builder()
                .faceCount(2)
                .vertices(
                        new float[]{0, 10, 0, 10},
                        new float[]{0, 0, -10, -10},
                        new float[]{0, 0, 0, 5})
                .faceIndices(new int[]{0, 1}, new int[]{1, 3}, new int[]{2, 2})
                .faceColors(new int[]{100, 100}, new int[]{100, 100}, new int[]{-1, -1})
                .facePriorities(new byte[]{0, 10})
                .build();

        final MeshData mesh = ModelMeshBuilder.build(model);
        assertEquals("Differing priority must not split the mesh", 1, mesh.getPrimitives().size());

        final float[] priorities = mesh.getPriorities();
        assertNotNull(priorities);
        boolean sawZero = false;
        boolean sawTen = false;
        for (float priority : priorities) {
            sawZero |= priority == 0f;
            sawTen |= priority == 10f;
        }
        assertTrue("Both priorities should reach the vertices", sawZero && sawTen);
    }

    @Test
    public void omitsPrioritiesWhenTheModelHasNone() {
        assertNull(ModelMeshBuilder.build(twoFaceModel()).getPriorities());
    }

    /**
     * Only texture and translucency may split a primitive. Everything else a
     * renderer needs travels per vertex, because each extra split is another
     * mesh and translucent triangles have to share one to be sorted.
     */
    @Test
    public void splitsOnlyOnTextureAndTranslucency() {
        final Model model = FakeModel.builder()
                .faceCount(3)
                .vertices(
                        new float[]{0, 10, 0, 10},
                        new float[]{0, 0, -10, -10},
                        new float[]{0, 0, 0, 5})
                .faceIndices(new int[]{0, 1, 0}, new int[]{1, 3, 2}, new int[]{2, 2, 3})
                .faceColors(
                        new int[]{100, 100, 100},
                        new int[]{100, 100, 100},
                        new int[]{-1, -1, -1})
                // Differing bias and priority must not split anything.
                .faceBias(new byte[]{0, 1, 2})
                .facePriorities(new byte[]{0, 5, 10})
                .build();

        assertEquals(1, ModelMeshBuilder.build(model).getPrimitives().size());
    }

    @Test
    public void splitsTranslucencyWithinATexturedGroupToo() {
        final Model model = FakeModel.builder()
                .faceCount(2)
                .vertices(
                        new float[]{0, 10, 0, 10},
                        new float[]{0, 0, -10, -10},
                        new float[]{0, 0, 0, 0})
                .faceIndices(new int[]{0, 1}, new int[]{1, 3}, new int[]{2, 2})
                .faceColors(new int[]{127, 127}, new int[]{127, 127}, new int[]{127, 127})
                .faceTextures(new short[]{5, 5})
                .faceTransparencies(new byte[]{0, (byte) 128})
                .build();

        final List<MeshData.Primitive> primitives = ModelMeshBuilder.build(model).getPrimitives();
        assertEquals(2, primitives.size());
        for (MeshData.Primitive primitive : primitives) {
            assertEquals(5, primitive.getTextureId());
        }
        assertFalse(primitives.get(0).isTranslucent());
        assertTrue(primitives.get(1).isTranslucent());
    }

    @Test
    public void mapsFacesOntoTheTextureTriangle() {
        // Texture triangle (0, 1, 2) matches the face exactly, so the face's
        // corners should land on the texture's origin and both axis ends.
        final Model model = FakeModel.builder()
                .faceCount(1)
                .vertices(new float[]{0, 10, 0}, new float[]{0, 0, -10}, new float[]{0, 0, 0})
                .faceIndices(new int[]{0}, new int[]{1}, new int[]{2})
                .faceColors(new int[]{127}, new int[]{127}, new int[]{127})
                .faceTextures(new short[]{5})
                .textureFaces(new byte[]{0}, new int[]{0}, new int[]{1}, new int[]{2})
                .build();

        final float[] u = new float[3];
        final float[] v = new float[3];
        ModelMeshBuilder.computeFaceUvs(model, 0, u, v);

        assertEquals(0f, u[0], 0.0001f);
        assertEquals(0f, v[0], 0.0001f);
        assertEquals(1f, u[1], 0.0001f);
        assertEquals(0f, v[1], 0.0001f);
        assertEquals(0f, u[2], 0.0001f);
        assertEquals(1f, v[2], 0.0001f);
    }

    @Test
    public void fallsBackToCornerMappingWithoutATextureTriangle() {
        final Model model = FakeModel.builder()
                .faceCount(1)
                .vertices(new float[]{0, 10, 0}, new float[]{0, 0, -10}, new float[]{0, 0, 0})
                .faceIndices(new int[]{0}, new int[]{1}, new int[]{2})
                .faceColors(new int[]{127}, new int[]{127}, new int[]{127})
                .faceTextures(new short[]{5})
                .build();

        final float[] u = new float[3];
        final float[] v = new float[3];
        ModelMeshBuilder.computeFaceUvs(model, 0, u, v);

        assertEquals(0f, u[0], 0.0001f);
        assertEquals(1f, u[1], 0.0001f);
        assertEquals(1f, v[2], 0.0001f);
    }
}
