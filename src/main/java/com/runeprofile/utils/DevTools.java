package com.runeprofile.utils;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
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
    private ItemManager itemManager;

    @Inject
    private Gson gson;

    // real -> dummy
    private final static Map<Integer, Integer> itemDummyMap = new HashMap<Integer, Integer>() {{
        put(29472, 12013); // Prospector helmet
        put(29474, 12014); // Prospector jacket
        put(29476, 12015); // Prospector legs
        put(29478, 12016); // Prospector boots
        put(10859, 25617); // Tea flask
        put(10877, 25618); // Plain satchel
        put(10878, 25619); // Green satchel
        put(10879, 25620); // Red satchel
        put(10880, 25621); // Black satchel
        put(10881, 25622); // Gold satchel
        put(10882, 25623); // Rune satchel
        put(13273, 25624); // Unsired
        put(12019, 25627); // Coal bag
        put(12020, 25628); // Gem bag
        put(24882, 25629); // Plank sack
        put(12854, 25630); // Flamtaer bag
        put(29990, 29992); // Alchemist's amulet
        put(30803, 30805); // Dossier
    }};

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

    public void generateItemIconsJson() {
        clientThread.invokeLater(() -> {
            Map<String, String> icons = new HashMap<>();

            int[] topLevelTabStructIds = client.getEnum(2102).getIntVals();
            for (int topLevelTabStructIndex : topLevelTabStructIds) {
                final StructComposition topLevelTabStruct = client.getStructComposition(topLevelTabStructIndex);
                int[] subtabStructIndices = client.getEnum(topLevelTabStruct.getIntValue(683)).getIntVals();
                for (int subtabStructIndex : subtabStructIndices) {
                    final StructComposition subtabStruct = client.getStructComposition(subtabStructIndex);
                    int[] clogItems = client.getEnum(subtabStruct.getIntValue(690)).getIntVals();
                    for (int clogItemId : clogItems) {
                        final int itemid = itemDummyMap.getOrDefault(clogItemId, clogItemId);
                        final BufferedImage sprite = itemManager.getImage(itemid, 10000, false);
                        writeBase64Sprite(sprite, Integer.toString(itemid), icons);
                    }
                }
            }

            DEV_writeJsonFile("item-icons.json", icons);
        });
    }

    private void writeBase64Sprite(BufferedImage sprite, String key, Map<String, String> icons) {
        if (sprite == null) {
            log.debug("Failed to load icon for = {}", key);
            return;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            synchronized (ImageIO.class) {
                ImageIO.write(sprite, "png", baos);
            }
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
