package com.runeprofile.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@UtilityClass
public class Utils {
    // Code from: DinkPlugin
    // Repository: https://github.com/pajlads/DinkPlugin
    // License: BSD 2-Clause License
    public <T> CompletableFuture<T> readJson(@NonNull OkHttpClient httpClient, @NonNull Gson gson, @NonNull String url, @NonNull TypeToken<T> type) {
        return readUrl(httpClient, url, reader -> gson.fromJson(reader, type.getType()));
    }

    // Code from: DinkPlugin
    // Repository: https://github.com/pajlads/DinkPlugin
    // License: BSD 2-Clause License
    public <T> CompletableFuture<T> readUrl(@NonNull OkHttpClient httpClient, @NonNull String url, @NonNull Function<Reader, T> transformer) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                assert response.body() != null;
                try (Reader reader = response.body().charStream()) {
                    future.complete(transformer.apply(reader));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                } finally {
                    response.close();
                }
            }
        });
        return future;
    }

    // Code from: DinkPlugin
    // Repository: https://github.com/pajlads/DinkPlugin
    // License: BSD 2-Clause License
    public String sanitize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Text.removeTags(str.replace("<br>", "\n")).replace('\u00A0', ' ').trim();
    }

    public void openProfileInBrowser(String username) {
        String url = "https://www.runeprofile.com/" + username.replace(" ", "%20");
        LinkBrowser.browse(url);
    }

    public String getApiErrorMessage(Throwable ex, String defaultMessage) {
        String result = defaultMessage;

        if (ex instanceof RuneProfileApiException) {
            result = ex.getMessage();
        } else if (ex.getCause() instanceof RuneProfileApiException) {
            result = ex.getCause().getMessage();
        }

        return result;
    }

    /**
     * Formats an API timestamp (Postgres "yyyy-MM-dd HH:mm:ss[.SSSSSS]" in UTC,
     * or ISO-8601) for display. Falls back to the raw string if parsing fails.
     */
    public String formatTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("MMM d, yyyy");
        String normalized = raw.trim().replace(' ', 'T');

        try {
            return OffsetDateTime.parse(normalized)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(displayFormat);
        } catch (DateTimeParseException ignored) {
            // no offset in the string, assume UTC
        }

        try {
            return LocalDateTime.parse(normalized)
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(displayFormat);
        } catch (DateTimeParseException e) {
            return raw;
        }
    }

    /**
     * Multiline text that wraps at the component's actual width. Preferable over
     * width-constrained html labels, whose text can get clipped because the html
     * renderer measures the RuneScape fonts differently than they paint.
     */
    public JTextArea createParagraph(String text) {
        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(FontManager.getRunescapeSmallFont());
        area.setForeground(Color.LIGHT_GRAY);
        setParagraphText(area, text);
        return area;
    }

    public void setParagraphText(JTextArea area, String text) {
        area.setText(text);
        // the line-wrapped preferred height is derived from the current width,
        // so give it a conservative width before the next layout
        area.setSize(PluginPanel.PANEL_WIDTH - 40, Short.MAX_VALUE);
        area.setMaximumSize(new Dimension(Short.MAX_VALUE, area.getPreferredSize().height));
    }

    public boolean isAccountNotFound(Throwable ex) {
        if (ex instanceof RuneProfileApiException) {
            return ((RuneProfileApiException) ex).isAccountNotFound();
        }
        if (ex.getCause() instanceof RuneProfileApiException) {
            return ((RuneProfileApiException) ex.getCause()).isAccountNotFound();
        }
        return false;
    }
}
