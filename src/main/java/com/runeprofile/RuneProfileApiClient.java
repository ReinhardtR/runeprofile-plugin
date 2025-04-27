package com.runeprofile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.runeprofile.data.CollectionLogPage;
import com.runeprofile.data.PlayerData;
import com.runeprofile.data.PlayerModelData;
import com.runeprofile.data.ProfileSearchResult;
import com.runeprofile.utils.RuneProfileApiException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import okhttp3.*;

import javax.annotation.Nullable;
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

    private CompletableFuture<Response> postHttpRequestAsync(HttpUrl url, String data) {
        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, data);
        Request request = buildApiRequest(url, builder -> builder.post(body)).build();
        log.debug("Sending json request to = {}, data = {}", url.toString(), data);
        return executeHttpRequestAsync(okHttpClient, request);
    }

    private CompletableFuture<Response> postHttpRequestAsync(HttpUrl url, MultipartBody data) {
        Request request = buildApiRequest(url, builder -> builder.post(data)).build();
        log.debug("Sending form data request to = {}, data = {}", url.toString(), data);
        return executeHttpRequestAsync(okHttpClient, request);
    }

    private CompletableFuture<Response> getHttpRequestAsync(HttpUrl url) {
        Request request = buildApiRequest(url, Request.Builder::get)
                .build();
        return executeHttpRequestAsync(okHttpClient, request);
    }

    private CompletableFuture<Response> deleteHttpRequestAsync(HttpUrl url) {
        Request request = buildApiRequest(url, Request.Builder::delete)
                .build();
        return executeHttpRequestAsync(okHttpClient, request);
    }

    private <T> T handleResponse(Response response, @Nullable Class<T> clazz) {
        try (Response res = response) {
            ResponseBody body = res.body();

            if (body == null) {
                throw new RuneProfileApiException("Response body is null");
            }

            String bodyString = body.string();

            if (!response.isSuccessful()) {
                JsonObject json = gson.fromJson(bodyString, JsonObject.class);
                throw new RuneProfileApiException(json.get("message").getAsString());
            }

            if (clazz == null) {
                return null;
            }

            return gson.fromJson(bodyString, clazz);
        } catch (IOException e) {
            throw new RuneProfileApiException("Error reading response body");
        }
    }

    public CompletableFuture<Void> updateProfileAsync(PlayerData data) {
        HttpUrl url = buildApiUrl("profiles");
        return postHttpRequestAsync(url, gson.toJson(data))
                .thenApply((response) -> handleResponse(response, null));
    }

    public CompletableFuture<Void> updateModelAsync(PlayerModelData data) {
        HttpUrl url = buildApiUrl("profiles", "models");

        RequestBody modelFile = RequestBody.create(MediaType.parse("model/ply"), data.getModel());

        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("accountId", data.getAccountHash())
                .addFormDataPart("model", "model.ply", modelFile);

        @Nullable
        byte[] petModel = data.getPetModel();
        if (petModel != null) {
            RequestBody petFile = RequestBody.create(MediaType.parse("model/ply"), petModel);
            bodyBuilder.addFormDataPart("petModel", "pet.ply", petFile);
        }

        MultipartBody body = bodyBuilder.build();

        return postHttpRequestAsync(url, body)
                .thenApplyAsync((response -> handleResponse(response, null)));
    }

    public CompletableFuture<CollectionLogPage> getCollectionLogPage(String username, String page) {
        HttpUrl url = buildApiUrl("profiles", username, "collection-log", page);
        return getHttpRequestAsync(url)
                .thenApplyAsync((response -> handleResponse(response, CollectionLogPage.class)));
    }

    public CompletableFuture<ProfileSearchResult[]> searchProfiles(String query) {
        HttpUrl url = buildApiUrl("profiles")
                .newBuilder()
                .addQueryParameter("q", query)
                .build();
        return getHttpRequestAsync(url)
                .thenApplyAsync((response -> handleResponse(response, ProfileSearchResult[].class)));
    }
}
