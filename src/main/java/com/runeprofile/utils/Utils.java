package com.runeprofile.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.*;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@UtilityClass
public class Utils {
    public <T> CompletableFuture<T> readJson(@NonNull OkHttpClient httpClient, @NonNull Gson gson, @NonNull String url, @NonNull TypeToken<T> type) {
        return readUrl(httpClient, url, reader -> gson.fromJson(reader, type.getType()));
    }

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
}
