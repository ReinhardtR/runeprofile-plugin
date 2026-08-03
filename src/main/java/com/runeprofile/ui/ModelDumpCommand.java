package com.runeprofile.ui;

import com.runeprofile.modelexporter.GlbExporter;
import com.runeprofile.utils.DevMode;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.TreeSet;

/**
 * Development only {@code ::rpmodel} command that writes the local player's
 * model to {@code .runelite/runeprofile/models/}.
 */
@Slf4j
public class ModelDumpCommand {
    private static final String COMMAND = "rpmodel";
    private static final String DIRECTORY_NAME = "runeprofile";
    private static final String MODELS_DIRECTORY_NAME = "models";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    public void startUp() {
        if (DevMode.ENABLED) {
            eventBus.register(this);
        }
    }

    public void shutDown() {
        if (DevMode.ENABLED) {
            eventBus.unregister(this);
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted event) {
        if (!COMMAND.equalsIgnoreCase(event.getCommand())) {
            return;
        }

        clientThread.invokeLater(this::dump);
    }

    private void dump() {
        final Player player = client.getLocalPlayer();
        if (player == null || player.getModel() == null) {
            report("No player model available yet.");
            return;
        }

        final String stamp = LocalDateTime.now().format(STAMP);
        write(player.getModel(), "player-" + stamp);

        final NPC pet = client.getFollower();
        final Model petModel = pet == null ? null : pet.getModel();
        if (petModel != null) {
            write(petModel, "pet-" + stamp);
        } else if (pet != null) {
            // Following but not yet rendered, so its model has not been built.
            report("Pet is out but has no model yet; move the camera onto it and retry.");
        } else {
            report("No pet out, so only the player was written.");
        }
    }

    /**
     * Reports the properties of a model that decide which paths the exporter
     * takes, so a dump says for itself whether it covered anything new.
     * <p>
     * Cheaper and more reliable than guessing at gear: most of what has gone
     * wrong so far was a case nothing happened to be wearing, and a line like
     * this would have said so immediately.
     */
    private void describe(Model model) {
        final StringBuilder notes = new StringBuilder();

        final Set<Integer> textures = new TreeSet<>();
        final short[] faceTextures = model.getFaceTextures();
        if (faceTextures != null) {
            for (int face = 0; face < model.getFaceCount(); face++) {
                if (faceTextures[face] != -1) {
                    textures.add((int) faceTextures[face]);
                }
            }
        }
        notes.append("textures=").append(textures.isEmpty() ? "none" : textures);

        // Whether any texture is cut out. The alpha=0 path has never been
        // exercised by a real model; every texture seen so far is fully opaque.
        boolean cutOut = false;
        for (int textureId : textures) {
            final int[] pixels = client.getTextureProvider().load(textureId);
            if (pixels == null) {
                continue;
            }
            for (int pixel : pixels) {
                if (pixel == 0) {
                    cutOut = true;
                    break;
                }
            }
        }
        notes.append(" cutOutTexture=").append(cutOut);

        int minAlpha = 255;
        final byte[] transparencies = model.getFaceTransparencies();
        if (transparencies != null) {
            for (int face = 0; face < model.getFaceCount(); face++) {
                minAlpha = Math.min(minAlpha, 255 - (transparencies[face] & 0xff));
            }
        }
        notes.append(" minAlpha=").append(minAlpha);

        // Model level transparency is not exported. It is 0 on everything seen
        // so far; anything else means a model would render more solid than the
        // game draws it.
        final int modelTransparency = model.getTransparency() & 0xff;
        if (modelTransparency != 0) {
            notes.append(" MODEL-TRANSPARENCY=").append(modelTransparency).append(" (not exported)");
        }

        // Above 65535 vertices the exporter switches to 32 bit indices, a path
        // no real model has reached.
        notes.append(" verts=").append(model.getVerticesCount());

        // Faces the exporter skips on purpose, and why.
        int hidden = 0;
        int invisible = 0;
        int nearlyInvisible = 0;
        final int[] colors3 = model.getFaceColors3();
        for (int face = 0; face < model.getFaceCount(); face++) {
            if (colors3[face] == -2) {
                hidden++;
            } else if (transparencies != null) {
                final int raw = transparencies[face] & 0xff;
                if (raw == 255) {
                    invisible++;
                } else if (raw == 254) {
                    nearlyInvisible++;
                }
            }
        }
        notes.append(" dropped(hidden=").append(hidden)
                .append(" invisible=").append(invisible)
                .append(" alpha254=").append(nearlyInvisible).append(")");

        report(notes.toString());
    }

    private void write(Model model, String name) {
        try {
            final byte[] glb = GlbExporter.toBytes(client, model, name);
            final File file = writeFile(name + ".glb", glb);
            report(String.format("Wrote %s (%d faces, %d KB) to %s",
                    file.getName(), model.getFaceCount(), glb.length / 1024,
                    file.getParent()));
            describe(model);
        } catch (IOException | RuntimeException e) {
            log.warn("[rpmodel] export failed", e);
            report("Export failed: " + e.getMessage());
        }
    }

    /**
     * Reports into game chat as well as the log, since the usual way to run this
     * is a client started by double clicking a jar with no console in sight.
     */
    private void report(String message) {
        log.info("[rpmodel] {}", message);
        client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", message, null);
    }

    private File writeFile(String fileName, byte[] contents) throws IOException {
        final File directory = new File(new File(RuneLite.RUNELITE_DIR, DIRECTORY_NAME),
                MODELS_DIRECTORY_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }

        final File file = new File(directory, fileName);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(contents);
        }
        return file;
    }
}
