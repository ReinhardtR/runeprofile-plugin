package com.runeprofile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runeprofile.dataobjects.PlayerData;
import com.runeprofile.dataobjects.PlayerModelData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class RuneProfileApiClient {
	private static final MediaType JSON_MEDIA_TYPE = Objects.requireNonNull(MediaType.parse("application/json; charset=utf-8"));

	@Inject
	private OkHttpClient okHttpClient;

	public void updateAccount(PlayerData playerData) {
		// Build URL
		HttpUrl url = new HttpUrl.Builder()
						.scheme("http")
						.host("localhost")
						.port(3000)
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("update")
						.build();

		// Build Request Body
		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, playerData.getJson().toString());

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.post(body)
						.build();

		// Request Call
		try {
			Response response = okHttpClient.newCall(request).execute();
			response.close();
		} catch (IOException e) {
			log.error("Request call to RuneProfile API failed.");
		}
	}

	public void updateModel(PlayerModelData playerModelData) {
		// Build URL
		HttpUrl url = new HttpUrl.Builder()
						.scheme("http")
						.host("localhost")
						.port(3000)
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("update-model")
						.build();

		// Build Request Body
		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, playerModelData.getJson().toString());

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.post(body)
						.build();

		// Request Call
		try {
			Response response = okHttpClient.newCall(request).execute();
			response.close();
		} catch (IOException e) {
			log.error("Request call to RuneProfile API failed.");
		}
	}

	public String updateGeneratedPath(long accountHash) throws Exception {
		// Build URL
		HttpUrl url = new HttpUrl.Builder()
						.scheme("http")
						.host("localhost")
						.port(3000)
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("update-generated-path")
						.addQueryParameter("accountHash", String.valueOf(accountHash))
						.build();

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")

						.build();

		// Request Call
		try (Response response = okHttpClient.newCall(request).execute()) {
			JsonObject body = new JsonParser().parse(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
			response.close();

			log.info("Body: " + body);

			return body.get("generatedPath").getAsString();
		} catch (IOException e) {
			throw new Exception("Request call to RuneProfile API failed.");
		}
	}

	public JsonObject updateIsPrivate(long accountHash, boolean isPrivate) throws Exception {
		// Build URL
		HttpUrl url = new HttpUrl.Builder()
						.scheme("http")
						.host("localhost")
						.port(3000)
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("update-is-private")
						.addQueryParameter("accountHash", String.valueOf(accountHash))
						.addQueryParameter("isPrivate", String.valueOf(isPrivate))
						.build();

		JsonObject jsonBody = new JsonObject();
		jsonBody.addProperty("accountHash", accountHash);
		jsonBody.addProperty("isPrivate", isPrivate);

		// Build Request Body
		RequestBody requestBody = RequestBody.create(JSON_MEDIA_TYPE, jsonBody.toString());

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.post(requestBody)
						.build();

		// Request Call
		try (Response response = okHttpClient.newCall(request).execute()) {
			JsonObject responseBody = new JsonParser().parse(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
			response.close();

			log.info("Body: " + responseBody);

			return responseBody;
		} catch (IOException e) {
			throw new Exception("Request call to RuneProfile API failed.");
		}
	}

	public void deleteProfile(long accountHash) {
		// Build URL
		HttpUrl url = new HttpUrl.Builder()
						.scheme("http")
						.host("localhost")
						.port(3000)
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("delete")
						.addQueryParameter("accountHash", String.valueOf(accountHash))
						.build();

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.delete()
						.build();

		// Request Call
		try {
			Response response = okHttpClient.newCall(request).execute();
			response.close();
		} catch (IOException e) {
			log.error("Request call to RuneProfile API failed.");
		}
	}
}
