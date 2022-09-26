package com.runeprofile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runeprofile.dataobjects.PlayerData;
import com.runeprofile.dataobjects.PlayerModelData;
import com.runeprofile.utils.DateHeader;
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

	private final boolean isDevMode = true;

	@Inject
	private OkHttpClient okHttpClient;

	private HttpUrl.Builder getBaseUrl() {
		return isDevMode
						? new HttpUrl.Builder().scheme("http").host("localhost").port(3000)
						: new HttpUrl.Builder().scheme("https").host("runeprofile.com");
	}

	public String updateAccount(PlayerData playerData) {
		// Build URL
		HttpUrl url = getBaseUrl()
						.addPathSegment("api")
						.addPathSegment("profile")
						.build();

		// Build Request Body
		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, playerData.getJson().toString());

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.put(body)
						.build();

		// Request Call
		try {
			Response response = okHttpClient.newCall(request).execute();
			String date = response.header("Date");
			response.close();

			return DateHeader.getDateString(date);
		} catch (IOException e) {
			log.error("Request call to RuneProfile API failed.");
		}

		return "Failed";
	}

	public String updateModel(PlayerModelData playerModelData) {
		// Build URL
		HttpUrl url = getBaseUrl()
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("model")
						.build();

		// Build Request Body
		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, playerModelData.getJson().toString());

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.put(body)
						.build();

		// Request Call
		try {
			Response response = okHttpClient.newCall(request).execute();
			String date = response.header("Date");
			response.close();

			return DateHeader.getDateString(date);
		} catch (IOException e) {
			log.error("Request call to RuneProfile API failed.");
		}

		return "Failed";
	}

	public String updateGeneratedPath(long accountHash) throws Exception {
		// Build URL
		HttpUrl url = getBaseUrl()
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("generated-path")
						.build();

		JsonObject jsonRequestBody = new JsonObject();
		jsonRequestBody.addProperty("accountHash", accountHash);
		RequestBody requestBody = RequestBody.create(JSON_MEDIA_TYPE, jsonRequestBody.toString());

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.put(requestBody)
						.build();

		// Request Call
		try (Response response = okHttpClient.newCall(request).execute()) {
			JsonObject responseBody = new JsonParser().parse(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
			response.close();

			return responseBody.get("generatedPath").getAsString();
		} catch (IOException e) {
			throw new Exception("Request call to RuneProfile API failed.");
		}
	}

	public String updateDescription(long accountHash, String description) throws Exception {
		// Build URL
		HttpUrl url = getBaseUrl()
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("description")
						.build();

		JsonObject jsonRequestBody = new JsonObject();
		jsonRequestBody.addProperty("accountHash", accountHash);
		jsonRequestBody.addProperty("description", description);
		RequestBody requestBody = RequestBody.create(JSON_MEDIA_TYPE, jsonRequestBody.toString());

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.put(requestBody)
						.build();

		// Request Call
		try (Response response = okHttpClient.newCall(request).execute()) {
			JsonObject responseBody = new JsonParser().parse(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
			response.close();

			return responseBody.get("description").getAsString();
		} catch (IOException e) {
			throw new Exception("Request call to RuneProfile API failed.");
		}
	}

	public JsonObject updateIsPrivate(long accountHash, boolean isPrivate) throws Exception {
		// Build URL
		HttpUrl url = getBaseUrl()
						.addPathSegment("api")
						.addPathSegment("profile")
						.addPathSegment("private")
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
						.put(requestBody)
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
		HttpUrl url = getBaseUrl()
						.addPathSegment("api")
						.addPathSegment("profile")
						.build();

		JsonObject requestBodyJson = new JsonObject();
		requestBodyJson.addProperty("accountHash", accountHash);
		RequestBody requestBody = RequestBody.create(JSON_MEDIA_TYPE, requestBodyJson.toString());

		// Build Request
		Request request = new Request.Builder()
						.url(url)
						.header("Content-Type", "application/json")
						.header("User-Agent", "RuneLite")
						.delete(requestBody)
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
