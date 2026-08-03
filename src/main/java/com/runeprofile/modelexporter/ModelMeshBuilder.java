package com.runeprofile.modelexporter;

import lombok.NonNull;
import net.runelite.api.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a game {@link Model} into a {@link MeshData}, keeping the texture
 * mapping the old PLY exporter threw away.
 * <p>
 * The game gives us, per face, either a colour or a texture id plus a "texture
 * triangle": three vertices of the model itself that define the texture's
 * origin and its two axes. UV coordinates are recovered by projecting each face
 * vertex onto those axes, which is what the client's own GPU renderer does every
 * frame (see {@link #computeFaceUvs}).
 * <p>
 * Vertex colours mean different things on the two kinds of face. Untextured
 * faces carry a packed HSL colour. Textured faces carry a 7 bit lightness that
 * the renderer multiplies the sampled texel by, so those become greys and the
 * multiply happens again at draw time against the texture.
 */
public final class ModelMeshBuilder {
    /** Index by packed HSL to get RGB. Matches what the old PLY exporter used. */
    private static final int[] COLOR_PALETTE = JagexColor.createPalette(JagexColor.BRIGHTNESS_MIN);

    /** Lightness on a textured face is 7 bit; the renderer divides by this. */
    private static final float TEXTURE_LIGHT_MAX = 127f;

    /** A third face colour of -1 means flat shading rather than a colour per corner. */
    private static final int FLAT_SHADED = -1;

    /**
     * A third face colour of -2 marks a face the game never draws, distinct from
     * -1 which only means flat shading. Skipping it is not an optimisation: the
     * value is a sentinel, so reading it as a colour index lands on hue 63,
     * saturation 7, luminance 126, and the face appears as a stray near white
     * triangle across the model. Missing this is why one showed over the top of
     * an inferno cape in both this exporter and the older PLY one.
     */
    private static final int HIDDEN_FACE = -2;

    /**
     * Game model space is X east, Y down, Z north. Since X cross Y equals Z it is
     * already right handed, same as glTF, so getting Y pointing up is a rotation
     * rather than a mirror: negate Y and Z, which is a 180 degree turn about X.
     * Negating Y alone would reflect the model, putting the character back to
     * front and swapping its handedness.
     * <p>
     * Because the mapping is a proper rotation its determinant is +1, so face
     * winding carries over unchanged and corners are emitted in their original
     * order.
     */
    private static final float[] AXIS_SIGNS = {1f, -1f, -1f};

    private ModelMeshBuilder() {
    }

    public static MeshData build(@NonNull Model model) {
        final Map<FaceGroup, List<Integer>> groups = groupFaces(model);
        final boolean textured = groups.keySet().stream().anyMatch(FaceGroup::isTextured);

        final VertexBuffer vertices = new VertexBuffer(model.getFaceCount(), textured,
                model.getFaceRenderPriorities() != null);
        final List<MeshData.Primitive> primitives = new ArrayList<>(groups.size());

        for (Map.Entry<FaceGroup, List<Integer>> entry : groups.entrySet()) {
            final FaceGroup group = entry.getKey();
            final int start = vertices.indexCount();

            for (int face : entry.getValue()) {
                appendFace(model, face, group.isTextured(), vertices);
            }

            primitives.add(new MeshData.Primitive(group.textureId, start,
                    vertices.indexCount() - start, group.translucent));
        }

        return vertices.toMeshData(primitives);
    }

    /**
     * Buckets the faces the game would actually draw, one bucket per primitive.
     * <p>
     * A {@link TreeMap} keeps the buckets in {@link FaceGroup}'s order, so the
     * same model always exports byte for byte the same.
     */
    private static Map<FaceGroup, List<Integer>> groupFaces(Model model) {
        final int[] colors3 = model.getFaceColors3();
        final short[] faceTextures = model.getFaceTextures();
        final byte[] faceTransparencies = model.getFaceTransparencies();

        final Map<FaceGroup, List<Integer>> groups = new TreeMap<>();
        for (int face = 0; face < model.getFaceCount(); face++) {
            if (colors3[face] == HIDDEN_FACE) {
                continue;
            }
            final int alpha = alphaOf(faceTransparencies, face);
            if (alpha == 0) {
                // Fully transparent: the game does not draw it, so neither do we.
                continue;
            }

            final int textureId = faceTextures == null ? MeshData.NO_TEXTURE : faceTextures[face];
            groups.computeIfAbsent(new FaceGroup(textureId, alpha != 255),
                    key -> new ArrayList<>()).add(face);
        }
        return groups;
    }

    /** Appends one face's three corners, in their original winding. */
    private static void appendFace(Model model, int face, boolean textured, VertexBuffer vertices) {
        final float[] verticesX = model.getVerticesX();
        final float[] verticesY = model.getVerticesY();
        final float[] verticesZ = model.getVerticesZ();

        final int[] corners = {
                model.getFaceIndices1()[face],
                model.getFaceIndices2()[face],
                model.getFaceIndices3()[face],
        };

        // A flat shaded face has one colour for the whole triangle rather than
        // one per corner.
        final int[] colors3 = model.getFaceColors3();
        final boolean flat = colors3[face] == FLAT_SHADED;
        final int[] cornerColors = {
                model.getFaceColors1()[face],
                flat ? model.getFaceColors1()[face] : model.getFaceColors2()[face],
                flat ? model.getFaceColors1()[face] : colors3[face],
        };

        final float[] u = new float[3];
        final float[] v = new float[3];
        if (textured) {
            computeFaceUvs(model, face, u, v);
        }

        final int alpha = alphaOf(model.getFaceTransparencies(), face);
        final byte[] facePriorities = model.getFaceRenderPriorities();
        final int priority = facePriorities == null ? 0 : facePriorities[face] & 0xff;

        for (int corner = 0; corner < 3; corner++) {
            final int vertex = corners[corner];
            vertices.add(
                    verticesX[vertex] * AXIS_SIGNS[0],
                    verticesY[vertex] * AXIS_SIGNS[1],
                    verticesZ[vertex] * AXIS_SIGNS[2],
                    textured
                            ? lightnessToGrey(cornerColors[corner])
                            : COLOR_PALETTE[cornerColors[corner] & 0xffff],
                    alpha,
                    textured ? u[corner] : 0f,
                    textured ? v[corner] : 0f,
                    priority);
        }
    }

    /** Face transparency is 0 for opaque, 255 for invisible; alpha is the inverse. */
    private static int alphaOf(byte[] faceTransparencies, int face) {
        if (faceTransparencies == null) {
            return 255;
        }
        return 255 - (faceTransparencies[face] & 0xff);
    }

    /**
     * A textured face's "colour" is a 7 bit lightness the renderer multiplies the
     * texel by. Expanding it to a grey lets an ordinary vertex colour multiply
     * reproduce the same shading.
     */
    private static int lightnessToGrey(int lightness) {
        final int clamped = lightness < 0 ? 0 : Math.min(lightness, (int) TEXTURE_LIGHT_MAX);
        final int level = Math.round(clamped / TEXTURE_LIGHT_MAX * 255f);
        return (level << 16) | (level << 8) | level;
    }

    /**
     * Projects a face's three vertices onto its texture triangle to recover UV
     * coordinates, mirroring {@code ModelUploader.computeFaceUvs} in the client's
     * GPU plugin so exported models are mapped exactly as the game maps them.
     * <p>
     * The texture triangle is three model vertices: the first is the texture's
     * origin, the other two are the ends of its U and V axes. Faces without one
     * fall back to mapping the triangle straight onto the texture's corner.
     */
    static void computeFaceUvs(Model model, int face, float[] u, float[] v) {
        final byte[] textureFaces = model.getTextureFaces();
        if (textureFaces == null || textureFaces[face] == -1) {
            u[0] = 0f;
            v[0] = 0f;
            u[1] = 1f;
            v[1] = 0f;
            u[2] = 0f;
            v[2] = 1f;
            return;
        }

        final float[] vertexX = model.getVerticesX();
        final float[] vertexY = model.getVerticesY();
        final float[] vertexZ = model.getVerticesZ();

        final int triangleA = model.getFaceIndices1()[face];
        final int triangleB = model.getFaceIndices2()[face];
        final int triangleC = model.getFaceIndices3()[face];

        final int textureFace = textureFaces[face] & 0xff;
        final int texA = model.getTexIndices1()[textureFace];
        final int texB = model.getTexIndices2()[textureFace];
        final int texC = model.getTexIndices3()[textureFace];

        // Texture origin, then its two axes as vectors from that origin.
        final float originX = vertexX[texA];
        final float originY = vertexY[texA];
        final float originZ = vertexZ[texA];

        final float axisUx = vertexX[texB] - originX;
        final float axisUy = vertexY[texB] - originY;
        final float axisUz = vertexZ[texB] - originZ;

        final float axisVx = vertexX[texC] - originX;
        final float axisVy = vertexY[texC] - originY;
        final float axisVz = vertexZ[texC] - originZ;

        // The face's own corners, relative to the same origin.
        final float aX = vertexX[triangleA] - originX;
        final float aY = vertexY[triangleA] - originY;
        final float aZ = vertexZ[triangleA] - originZ;

        final float bX = vertexX[triangleB] - originX;
        final float bY = vertexY[triangleB] - originY;
        final float bZ = vertexZ[triangleB] - originZ;

        final float cX = vertexX[triangleC] - originX;
        final float cY = vertexY[triangleC] - originY;
        final float cZ = vertexZ[triangleC] - originZ;

        // normal = axisU x axisV
        final float normalX = axisUy * axisVz - axisUz * axisVy;
        final float normalY = axisUz * axisVx - axisUx * axisVz;
        final float normalZ = axisUx * axisVy - axisUy * axisVx;

        // Projecting along axisV x normal isolates the U component, and the
        // reciprocal dot with axisU normalises it so axisU's end lands on u = 1.
        float projX = axisVy * normalZ - axisVz * normalY;
        float projY = axisVz * normalX - axisVx * normalZ;
        float projZ = axisVx * normalY - axisVy * normalX;
        float scale = 1.0F / (projX * axisUx + projY * axisUy + projZ * axisUz);

        u[0] = (projX * aX + projY * aY + projZ * aZ) * scale;
        u[1] = (projX * bX + projY * bY + projZ * bZ) * scale;
        u[2] = (projX * cX + projY * cY + projZ * cZ) * scale;

        // Same again with the axes swapped, for V.
        projX = axisUy * normalZ - axisUz * normalY;
        projY = axisUz * normalX - axisUx * normalZ;
        projZ = axisUx * normalY - axisUy * normalX;
        scale = 1.0F / (projX * axisVx + projY * axisVy + projZ * axisVz);

        v[0] = (projX * aX + projY * aY + projZ * aZ) * scale;
        v[1] = (projX * bX + projY * bY + projZ * bZ) * scale;
        v[2] = (projX * cX + projY * cY + projZ * cZ) * scale;
    }

    /**
     * Identifies which primitive a face belongs to: the two things that need a
     * separate material. Ordering puts untextured before textured and opaque
     * before translucent.
     * <p>
     * Nothing else belongs here. Splitting is not free - every extra primitive
     * is another mesh, and translucent triangles can only be ordered against
     * each other while they share one - so per face data a renderer needs is
     * carried per vertex instead.
     */
    private static final class FaceGroup implements Comparable<FaceGroup> {
        private final int textureId;
        private final boolean translucent;

        FaceGroup(int textureId, boolean translucent) {
            this.textureId = textureId;
            this.translucent = translucent;
        }

        boolean isTextured() {
            return textureId != MeshData.NO_TEXTURE;
        }

        @Override
        public int compareTo(FaceGroup other) {
            if (textureId != other.textureId) {
                return Integer.compare(textureId, other.textureId);
            }
            return translucent == other.translucent ? 0 : (translucent ? 1 : -1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FaceGroup)) {
                return false;
            }
            final FaceGroup other = (FaceGroup) o;
            return textureId == other.textureId && translucent == other.translucent;
        }

        @Override
        public int hashCode() {
            return textureId * 2 + (translucent ? 1 : 0);
        }
    }

    /**
     * Collects vertices as faces are appended, merging any that come out
     * identical, and tracks the bounds glTF wants on the position accessor.
     * <p>
     * Sized for the worst case of three unshared vertices per face and trimmed
     * at the end, which avoids growing four parallel buffers as it goes.
     */
    private static final class VertexBuffer {
        private final float[] positions;
        private final byte[] colors;
        private final float[] uvs;
        private final float[] priorities;
        private final int[] indices;
        private final Map<VertexKey, Integer> lookup;

        private final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        private final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        private int vertexCount;
        private int indexCount;

        VertexBuffer(int faceCount, boolean textured, boolean prioritised) {
            final int capacity = faceCount * 3;
            this.positions = new float[capacity * 3];
            this.colors = new byte[capacity * 4];
            this.uvs = textured ? new float[capacity * 2] : null;
            this.priorities = prioritised ? new float[capacity] : null;
            this.indices = new int[capacity];
            this.lookup = new HashMap<>(capacity * 2);
        }

        int indexCount() {
            return indexCount;
        }

        void add(float x, float y, float z, int rgb, int alpha, float u, float v, int priority) {
            final VertexKey key = new VertexKey(x, y, z, rgb, alpha, u, v, priority);
            Integer slot = lookup.get(key);
            if (slot == null) {
                slot = vertexCount++;
                positions[slot * 3] = x;
                positions[slot * 3 + 1] = y;
                positions[slot * 3 + 2] = z;

                colors[slot * 4] = (byte) ((rgb >> 16) & 0xff);
                colors[slot * 4 + 1] = (byte) ((rgb >> 8) & 0xff);
                colors[slot * 4 + 2] = (byte) (rgb & 0xff);
                colors[slot * 4 + 3] = (byte) alpha;

                if (uvs != null) {
                    uvs[slot * 2] = u;
                    uvs[slot * 2 + 1] = v;
                }

                if (priorities != null) {
                    priorities[slot] = priority;
                }

                grow(x, 0);
                grow(y, 1);
                grow(z, 2);

                lookup.put(key, slot);
            }
            indices[indexCount++] = slot;
        }

        private void grow(float value, int axis) {
            if (value < min[axis]) {
                min[axis] = value;
            }
            if (value > max[axis]) {
                max[axis] = value;
            }
        }

        MeshData toMeshData(List<MeshData.Primitive> primitives) {
            if (vertexCount == 0) {
                final float[] origin = {0f, 0f, 0f};
                return new MeshData(new float[0], new byte[0], null, null, new int[0],
                        new ArrayList<>(), origin, origin);
            }
            return new MeshData(
                    trim(positions, vertexCount * 3),
                    trim(colors, vertexCount * 4),
                    priorities == null ? null : trim(priorities, vertexCount),
                    uvs == null ? null : trim(uvs, vertexCount * 2),
                    trim(indices, indexCount),
                    primitives,
                    min,
                    max);
        }

        private static float[] trim(float[] source, int length) {
            if (source.length == length) {
                return source;
            }
            final float[] trimmed = new float[length];
            System.arraycopy(source, 0, trimmed, 0, length);
            return trimmed;
        }

        private static byte[] trim(byte[] source, int length) {
            if (source.length == length) {
                return source;
            }
            final byte[] trimmed = new byte[length];
            System.arraycopy(source, 0, trimmed, 0, length);
            return trimmed;
        }

        private static int[] trim(int[] source, int length) {
            if (source.length == length) {
                return source;
            }
            final int[] trimmed = new int[length];
            System.arraycopy(source, 0, trimmed, 0, length);
            return trimmed;
        }
    }

    /**
     * Identity of a vertex for merging. Two face corners collapse into one vertex
     * only when position, colour and UV all agree, so flat shaded faces keep
     * their hard colour edges and textured faces keep their seams.
     */
    private static final class VertexKey {
        private final float x, y, z, u, v;
        private final int rgb, alpha, priority;
        private final int hash;

        VertexKey(float x, float y, float z, int rgb, int alpha, float u, float v, int priority) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rgb = rgb;
            this.alpha = alpha;
            this.u = u;
            this.v = v;
            this.priority = priority;

            int h = Float.floatToIntBits(x);
            h = h * 31 + Float.floatToIntBits(y);
            h = h * 31 + Float.floatToIntBits(z);
            h = h * 31 + rgb;
            h = h * 31 + alpha;
            h = h * 31 + Float.floatToIntBits(u);
            h = h * 31 + Float.floatToIntBits(v);
            h = h * 31 + priority;
            this.hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof VertexKey)) {
                return false;
            }
            final VertexKey other = (VertexKey) o;
            return hash == other.hash
                    && Float.floatToIntBits(x) == Float.floatToIntBits(other.x)
                    && Float.floatToIntBits(y) == Float.floatToIntBits(other.y)
                    && Float.floatToIntBits(z) == Float.floatToIntBits(other.z)
                    && rgb == other.rgb
                    && alpha == other.alpha
                    && Float.floatToIntBits(u) == Float.floatToIntBits(other.u)
                    && Float.floatToIntBits(v) == Float.floatToIntBits(other.v)
                    && priority == other.priority;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
