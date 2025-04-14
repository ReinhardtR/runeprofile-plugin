package com.runeprofile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.runeprofile.data.CollectionLogPage;
import com.runeprofile.data.PlayerData;
import com.runeprofile.data.PlayerModelData;
import com.runeprofile.utils.DateHeader;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class RuneProfileApiClient {
    private static final MediaType JSON_MEDIA_TYPE = Objects.requireNonNull(MediaType.parse("application/json; charset=utf-8"));

    @Inject
    private OkHttpClient okHttpClient;

    private final String userAgent;

    // increment when making breaking changes to how the plugin users the API
    private static final String version = "2.0.0";

    private final HttpUrl baseUrl;

    @Inject
    private Gson gson;

    @Inject
    public RuneProfileApiClient() {
        boolean isDevMode = false;

        String runeliteVersion = RuneLiteProperties.getVersion();
        userAgent = "RuneLite:" + runeliteVersion + "," + "Client:" + version;

        //noinspection ConstantValue
        baseUrl = isDevMode
                ? new HttpUrl.Builder().scheme("http").host("localhost").port(8787).build()
                : new HttpUrl.Builder().scheme("https").host("api.runeprofile.com").build();
    }

    private HttpUrl buildApiUrl(String... pathSegments) {
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
        for (String segment : pathSegments) {
            urlBuilder.addPathSegment(segment);
        }
        return urlBuilder.build();
    }

    private RequestBody createJsonBody(JsonObject jsonObject) {
        return RequestBody.create(JSON_MEDIA_TYPE, jsonObject.toString());
    }

    private Request.Builder buildApiRequest(HttpUrl url, Consumer<Request.Builder> methodSetter) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent);
        methodSetter.accept(builder);
        return builder;
    }

    private CompletableFuture<Response> executeHttpRequestAsync(OkHttpClient client, Request request) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                log.error("Async API request failed.", e);
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                future.complete(response);
            }
        });
        return future;
    }

    public CompletableFuture<Response> postHttpRequestAsync(HttpUrl url, String data) {
        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, data);
        Request request = buildApiRequest(url, builder -> builder.post(body)).build();
        log.debug("Sending json request to = {}, data = {}", url.toString(), data);
        return executeHttpRequestAsync(okHttpClient, request);
    }

    public CompletableFuture<Response> postHttpRequestAsync(HttpUrl url, MultipartBody data) {
        Request request = buildApiRequest(url, builder -> builder.post(data)).build();
        log.debug("Sending form data request to = {}, data = {}", url.toString(), data);
        return executeHttpRequestAsync(okHttpClient, request);
    }

    public CompletableFuture<Response> getHttpRequestAsync(HttpUrl url) {
        Request request = buildApiRequest(url, Request.Builder::get)
                .build();
        return executeHttpRequestAsync(okHttpClient, request);
    }

    public CompletableFuture<Response> deleteHttpRequestAsync(HttpUrl url) {
        Request request = buildApiRequest(url, Request.Builder::delete)
                .build();
        return executeHttpRequestAsync(okHttpClient, request);
    }

    public CompletableFuture<String> updateProfileAsync(PlayerData data) {
        HttpUrl url = buildApiUrl("profiles");
        return postHttpRequestAsync(url, gson.toJson(data)).thenApplyAsync(this::getResponseDateString);
    }

    public CompletableFuture<String> updateModelAsync(PlayerModelData data) {
        HttpUrl url = buildApiUrl("profiles", "models");

        RequestBody file = RequestBody.create(MediaType.parse("model/ply"), data.getModel());
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("accountId", data.getAccountHash())
                .addFormDataPart("model", "model.ply", file)
                .build();

        return postHttpRequestAsync(url, body).thenApplyAsync(this::getResponseDateString);
    }

    public CompletableFuture<CollectionLogPage> getCollectionLogPage(String username, String page) {
        HttpUrl url = buildApiUrl("profiles", username, "collection-log", page);

        return getHttpRequestAsync(url).thenApplyAsync((response -> {
            if (response.isSuccessful()) {
                try (Response res = response) {
                    ResponseBody body = res.body();
                    if (body == null) {
                        log.warn("Async API request failed with code: {}", response.code());
                        return null;
                    }
                    return gson.fromJson(body.string(), CollectionLogPage.class);
                } catch (IOException e) {
                    log.warn("Async API request failed with code: {}", response.code());
                    log.error("Error reading response body", e);
                    return null;
                }
            } else {
                log.warn("Async API request failed with code: {}", response.code());
                return null;
            }
        }));
    }

    public String getResponseDateString(Response response) {
        try (Response res = response) {
            if (response.isSuccessful()) {
                String date = res.header("Date");
                return DateHeader.getDateString(date);
            } else {
                log.warn("Async API request failed with code: {}", response.code());
                return "Failed";
            }
        }
    }
}
