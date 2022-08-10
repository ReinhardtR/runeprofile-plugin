package com.runeprofile;

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

	public void createOrUpdateRuneProfile(PlayerData playerData) {
		// Build URL
		HttpUrl url = new HttpUrl.Builder()
						.scheme("http")
						.host("localhost")
						.port(3000)
						.addPathSegment("api")
						.addPathSegment("account")
						.addPathSegment("submit-data")
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
}
