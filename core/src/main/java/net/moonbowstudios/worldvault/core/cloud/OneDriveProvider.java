package net.moonbowstudios.worldvault.core.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class OneDriveProvider implements CloudProvider {

	private static final String APPROOT = "https://graph.microsoft.com/v1.0/me/drive/special/approot";

	private static final int CHUNK_UNIT = 320 * 1024;
	private static final int CHUNK_BYTES = 25 * CHUNK_UNIT;   // 8 MiB
	private static final long SIMPLE_UPLOAD_LIMIT = 4L * 1024 * 1024;

	private static final Gson GSON = new Gson();

	private final CloudHttp http;

	public OneDriveProvider(CloudHttp http) {
		this.http = http;
	}

	@Override
	public String id() {
		return "onedrive";
	}

	@Override
	public String displayName() {
		return "OneDrive";
	}

	@Override
	public List<String> listWorldFolders() throws CloudException {
		List<String> folders = new ArrayList<>();
		String url = APPROOT + "/children?$select=name,folder&$top=200";

		while (url != null) {
			String next = url;
			HttpResponse<String> response = http.send(
				() -> HttpRequest.newBuilder(URI.create(next)).timeout(Duration.ofMinutes(1)).GET(),
				HttpResponse.BodyHandlers.ofString());

			JsonObject page = GSON.fromJson(response.body(), JsonObject.class);
			JsonArray values = page.getAsJsonArray("value");
			if (values != null) {
				for (JsonElement element : values) {
					JsonObject item = element.getAsJsonObject();
					if (item.has("folder")) {
						folders.add(item.get("name").getAsString());
					}
				}
			}
			url = page.has("@odata.nextLink") ? page.get("@odata.nextLink").getAsString() : null;
		}
		return folders;
	}

	@Override
	public Optional<byte[]> readSmallFile(String path) throws CloudException {
		try {
			HttpResponse<byte[]> response = http.send(
				() -> HttpRequest.newBuilder(URI.create(itemUrl(path) + ":/content"))
					.timeout(Duration.ofMinutes(2)).GET(),
				HttpResponse.BodyHandlers.ofByteArray());
			return Optional.of(response.body());
		} catch (CloudException e) {
			if (isNotFound(e)) {
				return Optional.empty();
			}
			throw e;
		}
	}

	@Override
	public void writeSmallFile(String path, byte[] content) throws CloudException {
		http.send(() -> HttpRequest.newBuilder(URI.create(itemUrl(path) + ":/content"))
				.timeout(Duration.ofMinutes(2))
				.header("Content-Type", "application/octet-stream")
				.PUT(HttpRequest.BodyPublishers.ofByteArray(content)),
			HttpResponse.BodyHandlers.ofString());
	}

	@Override
	public void uploadFile(String path, Path localFile, ProgressListener progress)
		throws CloudException {
		long size;
		try {
			size = Files.size(localFile);
		} catch (IOException e) {
			throw new CloudException("Could not read " + localFile, e);
		}

		if (size <= SIMPLE_UPLOAD_LIMIT) {
			HttpRequest.BodyPublisher body = bodyOf(localFile);
			http.send(() -> HttpRequest.newBuilder(URI.create(itemUrl(path) + ":/content"))
					.timeout(Duration.ofMinutes(10))
					.header("Content-Type", "application/octet-stream")
					.PUT(body),
				HttpResponse.BodyHandlers.ofString());
			progress.onProgress(size, size);
			return;
		}

		uploadInSession(path, localFile, size, progress);
	}

	private void uploadInSession(String path, Path localFile, long size, ProgressListener progress)
		throws CloudException {
		JsonObject item = new JsonObject();
		item.addProperty("@microsoft.graph.conflictBehavior", "replace");
		JsonObject body = new JsonObject();
		body.add("item", item);

		HttpResponse<String> created = http.send(
			() -> HttpRequest.newBuilder(URI.create(itemUrl(path) + ":/createUploadSession"))
				.timeout(Duration.ofMinutes(1))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8)),
			HttpResponse.BodyHandlers.ofString());

		String uploadUrl = GSON.fromJson(created.body(), JsonObject.class)
			.get("uploadUrl").getAsString();

		try (InputStream in = Files.newInputStream(localFile)) {
			byte[] buffer = new byte[CHUNK_BYTES];
			long offset = 0;

			while (offset < size) {
				int read = in.readNBytes(buffer, 0, buffer.length);
				if (read <= 0) {
					break;
				}
				byte[] chunk = Arrays.copyOf(buffer, read);
				String range = "bytes " + offset + "-" + (offset + read - 1) + "/" + size;

				// graph rejects the session url if a bearer token is attached as well
				http.sendUnauthenticated(() -> HttpRequest.newBuilder(URI.create(uploadUrl))
						.timeout(Duration.ofMinutes(10))
						.header("Content-Range", range)
						.header("Content-Type", "application/octet-stream")
						.PUT(HttpRequest.BodyPublishers.ofByteArray(chunk)),
					HttpResponse.BodyHandlers.ofString());

				offset += read;
				progress.onProgress(offset, size);
			}
		} catch (IOException e) {
			throw new CloudException("Could not read " + localFile, e);
		}
	}

	@Override
	public void downloadFile(String path, Path target, ProgressListener progress)
		throws CloudException {
		Path temp = target.resolveSibling(target.getFileName() + ".part");
		try {
			Files.createDirectories(target.getParent());

			HttpResponse<Path> response = http.sendFollowingRedirects(
				() -> HttpRequest.newBuilder(URI.create(itemUrl(path) + ":/content"))
					.timeout(Duration.ofMinutes(30)).GET(),
				HttpResponse.BodyHandlers.ofFile(temp));

			Files.move(response.body(), target, StandardCopyOption.REPLACE_EXISTING);
			long size = Files.size(target);
			progress.onProgress(size, size);
		} catch (IOException e) {
			throw new CloudException("Could not write " + target, e);
		} finally {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException ignored) {
			}
		}
	}

	@Override
	public void deleteFile(String path) throws CloudException {
		deletePath(path);
	}

	@Override
	public void deleteFolder(String path) throws CloudException {
		deletePath(path);
	}

	private void deletePath(String path) throws CloudException {
		try {
			http.send(() -> HttpRequest.newBuilder(URI.create(itemUrl(path)))
				.timeout(Duration.ofMinutes(1)).DELETE(), HttpResponse.BodyHandlers.ofString());
		} catch (CloudException e) {
			if (!isNotFound(e)) {
				throw e;
			}
		}
	}

	static String itemUrl(String path) {
		StringBuilder encoded = new StringBuilder();
		for (String segment : path.split("/")) {
			if (segment.isEmpty()) {
				continue;
			}
			if (!encoded.isEmpty()) {
				encoded.append('/');
			}
			encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
		}
		return APPROOT + ":/" + encoded;
	}

	private static HttpRequest.BodyPublisher bodyOf(Path file) throws CloudException {
		try {
			return HttpRequest.BodyPublishers.ofFile(file);
		} catch (java.io.FileNotFoundException e) {
			throw new CloudException("Could not read " + file, e);
		}
	}

	private static boolean isNotFound(CloudException e) {
		String message = e.getMessage();
		return message != null && (message.startsWith("HTTP 404") || message.contains("itemNotFound"));
	}
}
