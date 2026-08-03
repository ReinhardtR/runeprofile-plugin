package com.runeprofile.modelexporter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Texture;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads game textures out of the client as PNGs, along with the scroll rate
 * that makes animated ones (an inferno cape's fire, lava, water) move.
 * <p>
 * Encoded PNGs are cached for the life of the client: there are only a couple
 * of hundred textures in the game and a player uses a handful, but a profile
 * sync would otherwise re-encode the same cape every time.
 */
@Slf4j
public final class GameTextures {
    /**
     * The client advances texture animation once per client tick, and shifts
     * UVs by speed/128 each time. Client ticks are 20ms, so this converts the
     * game's per tick step into UV units per second for a renderer that works
     * in wall clock time.
     */
    private static final float TICKS_PER_SECOND = 50f;
    private static final float TEXTURE_ANIM_UNIT = 1f / 128f;

    /** One texture, ready to embed or upload. */
    public static final class TextureData {
        private final int id;
        private final byte[] png;
        private final int averageColor;
        private final float scrollU;
        private final float scrollV;

        TextureData(int id, @Nullable byte[] png, int averageColor, float scrollU, float scrollV) {
            this.id = id;
            this.png = png;
            this.averageColor = averageColor;
            this.scrollU = scrollU;
            this.scrollV = scrollV;
        }

        public int getId() {
            return id;
        }

        /** Null if the pixels could not be encoded; fall back to {@link #getAverageColor()}. */
        @Nullable
        public byte[] getPng() {
            return png;
        }

        /**
         * Packed RGB mean of the non transparent texels. Used as a flat stand in
         * when the image itself is unavailable, which is what the old PLY
         * exporter did for every textured face.
         */
        public int getAverageColor() {
            return averageColor;
        }

        /** UV units per second, 0 when the texture does not animate. */
        public float getScrollU() {
            return scrollU;
        }

        public float getScrollV() {
            return scrollV;
        }

        public boolean isAnimated() {
            return scrollU != 0f || scrollV != 0f;
        }
    }

    private final Client client;
    private final Map<Integer, TextureData> cache = new HashMap<>();

    public GameTextures(@NonNull Client client) {
        this.client = client;
    }

    /**
     * Returns the texture, or null when the client has not loaded it yet (its
     * pixels are fetched lazily, so a texture the local player has never had
     * on screen may genuinely be unavailable).
     */
    @Nullable
    public TextureData get(int textureId) {
        if (textureId < 0) {
            return null;
        }
        if (cache.containsKey(textureId)) {
            return cache.get(textureId);
        }

        TextureData data = load(textureId);
        cache.put(textureId, data);
        return data;
    }

    @Nullable
    private TextureData load(int textureId) {
        final int[] pixels;
        try {
            pixels = client.getTextureProvider().load(textureId);
        } catch (RuntimeException e) {
            log.debug("Could not load texture {}", textureId, e);
            return null;
        }

        if (pixels == null || pixels.length == 0) {
            return null;
        }

        // Textures are square; the client uses 128x128 but deriving it keeps
        // this working if that ever changes.
        final int size = (int) Math.round(Math.sqrt(pixels.length));
        if (size * size != pixels.length) {
            log.debug("Texture {} is not square ({} pixels)", textureId, pixels.length);
            return null;
        }

        byte[] png = null;
        try {
            png = encodePng(pixels, size);
        } catch (IOException | RuntimeException e) {
            // Still worth returning the average colour so the model renders as
            // a plausible flat surface rather than grey.
            log.debug("Could not encode texture {}", textureId, e);
        }

        final float[] scroll = scrollOf(textureId);
        return new TextureData(textureId, png, averageColor(pixels), scroll[0], scroll[1]);
    }

    /** Mean of every texel the game actually draws, ignoring transparent ones. */
    private static int averageColor(int[] pixels) {
        long red = 0;
        long green = 0;
        long blue = 0;
        int counted = 0;
        for (int pixel : pixels) {
            if (pixel == 0) {
                continue;
            }
            red += (pixel >> 16) & 0xff;
            green += (pixel >> 8) & 0xff;
            blue += pixel & 0xff;
            counted++;
        }
        if (counted == 0) {
            return 0xffffff;
        }
        return (int) ((red / counted) << 16 | (green / counted) << 8 | (blue / counted));
    }

    /**
     * A pixel of 0 is the game's transparent texel, which the renderer discards
     * rather than drawing black, so it becomes a fully transparent PNG pixel.
     */
    private static byte[] encodePng(int[] pixels, int size) throws IOException {
        final int[] argb = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            final int rgb = pixels[i];
            argb[i] = rgb == 0 ? 0 : 0xff000000 | rgb;
        }

        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, size, size, argb, 0, size);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * Direction and speed come straight off the texture definition; the mapping
     * from direction to axis matches the client's own
     * {@code TextureManager.computeTextureAnimations}.
     */
    private float[] scrollOf(int textureId) {
        final Texture[] textures = client.getTextureProvider().getTextures();
        if (textures == null || textureId >= textures.length || textures[textureId] == null) {
            return new float[]{0f, 0f};
        }

        final Texture texture = textures[textureId];
        float u = 0f;
        float v = 0f;
        switch (texture.getAnimationDirection()) {
            case 1:
                v = -1f;
                break;
            case 2:
                u = -1f;
                break;
            case 3:
                v = 1f;
                break;
            case 4:
                u = 1f;
                break;
            default:
                return new float[]{0f, 0f};
        }

        final float perSecond = texture.getAnimationSpeed() * TEXTURE_ANIM_UNIT * TICKS_PER_SECOND;
        return new float[]{u * perSecond, v * perSecond};
    }
}
