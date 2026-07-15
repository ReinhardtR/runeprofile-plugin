package com.runeprofile.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A classpath JSON map of base64-encoded PNGs (the shape produced by
 * {@link DevTools} and shared with the website), decoded to {@link ImageIcon}s
 * on demand and cached. The JSON is loaded lazily on first access.
 */
@Slf4j
public final class IconSheet {
    private final String resourcePath;

    private Map<String, String> base64ByKey;

    // Decoded icons, including null for missing keys, so each key is decoded once.
    private final Map<String, ImageIcon> cache = new HashMap<>();

    public IconSheet(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /**
     * The icon for a key, or {@code null} when the key is absent or fails to decode.
     */
    public synchronized @Nullable ImageIcon get(@Nullable String key) {
        if (key == null) {
            return null;
        }
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        ImageIcon icon = decode(key);
        cache.put(key, icon);
        return icon;
    }

    private @Nullable ImageIcon decode(String key) {
        String base64 = base64ByKey().get(key);
        if (base64 == null) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return new ImageIcon(ImageIO.read(new ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            log.debug("Failed to decode icon for key = {} in {}", key, resourcePath, e);
            return null;
        }
    }

    private Map<String, String> base64ByKey() {
        if (base64ByKey == null) {
            base64ByKey = load(resourcePath);
        }
        return base64ByKey;
    }

    private static Map<String, String> load(String resourcePath) {
        try (InputStream in = RuneProfilePlugin.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.debug("Icon sheet not found on classpath: {}", resourcePath);
                return Collections.emptyMap();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Map<String, String> icons = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {
                }.getType());
                return icons != null ? icons : Collections.emptyMap();
            }
        } catch (Exception e) {
            log.debug("Failed to load icon sheet: {}", resourcePath, e);
            return Collections.emptyMap();
        }
    }
}
