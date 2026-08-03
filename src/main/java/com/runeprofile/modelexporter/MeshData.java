package com.runeprofile.modelexporter;

import java.util.Collections;
import java.util.List;

/**
 * A triangle mesh in a form that is independent of both the game's model
 * representation and the file format it will be written to.
 * <p>
 * There is a single shared vertex buffer and a single shared index buffer.
 * Each {@link Primitive} owns a contiguous run of that index buffer and names
 * the two things that decide how it is drawn: the game texture its triangles
 * are mapped to, and whether they are translucent. A model that mixes an
 * untextured body with, say, an inferno cape becomes two primitives over one
 * set of vertices rather than two meshes.
 * <p>
 * Everything else a renderer needs travels per vertex, so it never forces a
 * split. Face render priority is the reason that matters: translucent geometry
 * has to share one mesh to be sorted, so anything carried per primitive would
 * reach opaque faces only.
 * <p>
 * Faces are deliberately kept in as few primitives as they can be. Each one is
 * a separate mesh to a renderer, and translucent triangles can only be ordered
 * correctly against each other while they share one.
 * <p>
 * Coordinates are already in glTF's space: Y up, right handed, still in game
 * units (roughly 128 per tile), with index order wound counter clockwise.
 */
public final class MeshData {
    /** Texture id used by {@link Primitive#getTextureId()} for untextured triangles. */
    public static final int NO_TEXTURE = -1;

    /**
     * One run of triangles sharing a texture. {@code indexOffset} and
     * {@code indexCount} address {@link MeshData#getIndices()}.
     */
    public static final class Primitive {
        private final int textureId;
        private final int indexOffset;
        private final int indexCount;
        private final boolean translucent;

        Primitive(int textureId, int indexOffset, int indexCount, boolean translucent) {
            this.textureId = textureId;
            this.indexOffset = indexOffset;
            this.indexCount = indexCount;
            this.translucent = translucent;
        }

        public int getTextureId() {
            return textureId;
        }

        public int getIndexOffset() {
            return indexOffset;
        }

        public int getIndexCount() {
            return indexCount;
        }

        /** True when any triangle in the run is partially transparent. */
        public boolean isTranslucent() {
            return translucent;
        }




        public boolean isTextured() {
            return textureId != NO_TEXTURE;
        }
    }

    private final float[] positions;
    private final byte[] colors;
    private final float[] priorities;
    private final float[] uvs;
    private final int[] indices;
    private final List<Primitive> primitives;
    private final float[] min;
    private final float[] max;

    MeshData(float[] positions, byte[] colors, float[] priorities, float[] uvs, int[] indices,
             List<Primitive> primitives, float[] min, float[] max) {
        this.positions = positions;
        this.colors = colors;
        this.priorities = priorities;
        this.uvs = uvs;
        this.indices = indices;
        this.primitives = Collections.unmodifiableList(primitives);
        this.min = min;
        this.max = max;
    }

    /** Three floats per vertex. */
    public float[] getPositions() {
        return positions;
    }

    /** Four unsigned bytes per vertex, RGBA. */
    public byte[] getColors() {
        return colors;
    }

    /** Two floats per vertex, or null when no primitive is textured. */
    public float[] getUvs() {
        return uvs;
    }

    /**
     * One float per vertex: the game's face render priority, or null when the
     * model has none.
     * <p>
     * Per vertex rather than per primitive because a renderer needs it on
     * translucent geometry too, and translucent faces have to share one mesh so
     * their triangles can be sorted against each other. Carrying it on the
     * material would force a split that transparency cannot afford, and would
     * silently apply to opaque geometry only - which reorders opaque against
     * translucent and puts an arm through a crystal shield.
     */
    public float[] getPriorities() {
        return priorities;
    }

    public int[] getIndices() {
        return indices;
    }

    public List<Primitive> getPrimitives() {
        return primitives;
    }

    public int getVertexCount() {
        return positions.length / 3;
    }

    /** Per axis minimum of {@link #getPositions()}; glTF requires it on the position accessor. */
    public float[] getMin() {
        return min;
    }

    public float[] getMax() {
        return max;
    }

    public boolean isEmpty() {
        return indices.length == 0;
    }
}
