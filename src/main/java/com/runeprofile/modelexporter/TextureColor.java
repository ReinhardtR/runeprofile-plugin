/*
 * Copyright (c) 2020, bram91
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.runeprofile.modelexporter;

import lombok.NonNull;
import net.runelite.api.Client;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class TextureColor {
    private static final Map<Integer, Color> colorCache = new HashMap<>();

    // get single average color from Jagex texture id
    public static Color getColor(@NonNull Client client, int textureId) {
        if (colorCache.containsKey(textureId))
            return colorCache.get(textureId);

        int[] pixels = client.getTextureProvider().load(textureId);

        int r = 0;
        int g = 0;
        int b = 0;
        int n = 0;
        for (int pixel : pixels) {
            // skip transparent (black)
            if (pixel == 0)
                continue;

            Color c = new Color(pixel);
            r += c.getRed();
            g += c.getGreen();
            b += c.getBlue();
            n++;
        }

        Color c = new Color(r / n, g / n, b / n);
        colorCache.put(textureId, c);
        return c;
    }

}