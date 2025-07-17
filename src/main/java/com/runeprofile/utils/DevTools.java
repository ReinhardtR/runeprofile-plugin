package com.runeprofile.utils;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DevTools {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private Gson gson;

    public void generateHiscoreIconsJson() {
        clientThread.invokeLater(() -> {
            Map<String, String> icons = new HashMap<>();
            for (HiscoreSkill skill : HiscoreSkill.values()) {
                String key = skill.getName();
                int spriteId = skill.getSpriteId();
                if (spriteId == -1) {
                    continue;
                }

                final BufferedImage sprite = spriteManager.getSprite(spriteId, 0);
                writeBase64Sprite(sprite, key, icons);
            }

            DEV_writeJsonFile("hiscore-icons.json", icons);
        });
    }


    public void generateClanRankIconsJson() {
        clientThread.invokeLater(() -> {
            Map<String, String> icons = new HashMap<>();

            final EnumComposition clanIcons = client.getEnum(EnumID.CLAN_RANK_GRAPHIC);

            for (int i = 0; i < clanIcons.size(); i++) {
                final int key = clanIcons.getKeys()[i];
                final BufferedImage sprite = spriteManager.getSprite(clanIcons.getIntValue(key), 0);
                writeBase64Sprite(sprite, Integer.toString(key), icons);
            }

            DEV_writeJsonFile("clan-rank-icons.json", icons);
        });
    }

    private void writeBase64Sprite(BufferedImage sprite, String key, Map<String, String> icons) {
        if (sprite == null) {
            log.debug("Failed to load icon for = {}", key);
            return;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(sprite, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            icons.put(key, base64Image);
        } catch (IOException e) {
            log.debug("Failed to encode icon for = {}", key);
        }
    }


    private void DEV_writeJsonFile(String fileName, Object data) {
        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(data, writer);
            log.debug("Successfully wrote JSON to file = {}", fileName);
        } catch (IOException e) {
            log.debug("Failed to write JSON to file");
        }
    }
}
