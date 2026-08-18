package net.moonbowstudios.worldvault.core.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DropboxProvider implements CloudProvider {

	private static final String RPC = "https://api.dropboxapi.com/2";
	private static final String CONTENT = "https://content.dropboxapi.com/2";

	private static final int CHUNK_BYTES = 8 * 1024 * 1024;
	private static final long SIMPLE_UPLOAD_LIMIT = 32L * 1024 * 1024;

	private static final Gson GSON = new Gson();

	private final CloudHttp http;

	public DropboxProvider(CloudHttp http) {
		this.http = http;
	}

	@Override
	public String id() {
		return "dropbox";
	}

	@Override
	public String displayName() {
		return "Dropbox";
	}

	@Override
	public List<String> listWorldFolders() throws CloudException {
		List<String> folders = new ArrayList<>();
		JsonObject body = new JsonObject();
		body.addProperty("path", "");
		body.addProperty("recursive", false);

		String endpoint = "/files/list_folder";
		while (true) {
			JsonObject page = rpc(endpoint, body);
			JsonArray entries = page.getAsJsonArray("entries");
			if (entries != null) {
				for (JsonElement element : entries) {
					JsonObject entry = element.getAsJsonObject();
					if ("folder".equals(optString(entry, ".tag"))) {
						folders.add(entry.get("name").getAsString());
					}
				}
			}
			if (page.has("has_more") && page.get("has_more").getAsBoolean()) {
				endpoint = "/files/list_folder/continue";
				body = new JsonObject();
				body.addProperty("cursor", page.get("cursor").getAsString());
			} else {
				return folders;
			}
		}
	}

	@Override
	public Optional<byte[]> readSmallFile(String path) throws CloudException {
		JsonObject arg = new JsonObject();
		arg.addProperty("path", absolute(path));

		HttpResponse<byte[]> response;
		try {
			response = http.send(() -> HttpRequest.newBuilder(URI.create(CONTENT + "/files/download"))
				.timeout(Duration.ofMinutes(2))
				.header("Dropbox-API-Arg", jsonHeader(arg))
				.GET(), HttpResponse.BodyHandlers.ofByteArray());
		} catch (CloudException e) {
			if (isNotFound(e)) {
				return Optional.empty();
			}
			throw e;
		}
		return Optional.of(response.body());
	}

	@Override
	public void writeSmallFile(String path, byte[] content) throws CloudException {
		http.send(() -> HttpRequest.newBuilder(URI.create(CONTENT + "/files/upload"))
			.timeout(Duration.ofMinutes(2))
			.header("Dropbox-API-Arg", jsonHeader(uploadArg(path)))
			.header("Content-Type", "application/octet-stream")
			.POST(HttpRequest.BodyPublishers.ofByteArray(content)),
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
			// ofFile reopens the file on each subscribe, so one publisher survives a retry
			HttpRequest.BodyPublisher body = bodyOf(localFile);
			http.send(() -> HttpRequest.newBuilder(URI.create(CONTENT + "/files/upload"))
				.timeout(Duration.ofMinutes(10))
				.header("Dropbox-API-Arg", jsonHeader(uploadArg(path)))
				.header("Content-Type", "application/octet-stream")
				.POST(body), HttpResponse.BodyHandlers.ofString());
			progress.onProgress(size, size);
			return;
		}

		uploadInSession(path, localFile, size, progress);
	}

	private void uploadInSession(String path, Path localFile, long size, ProgressListener progress)
		throws CloudException {
		try (InputStream in = Files.newInputStream(localFile)) {
			byte[] buffer = new byte[CHUNK_BYTES];
			int first = in.readNBytes(buffer, 0, buffer.length);

			JsonObject startArg = new JsonObject();
			startArg.addProperty("close", false);
			byte[] firstChunk = java.util.Arrays.copyOf(buffer, first);

			HttpResponse<String> started = http.send(
				() -> HttpRequest.newBuilder(URI.create(CONTENT + "/files/upload_session/start"))
					.timeout(Duration.ofMinutes(5))
					.header("Dropbox-API-Arg", jsonHeader(startArg))
					.header("Content-Type", "application/octet-stream")
					.POST(HttpRequest.BodyPublishers.ofByteArray(firstChunk)),
				HttpResponse.BodyHandlers.ofString());

			String sessionId = GSON.fromJson(started.body(), JsonObject.class)
				.get("session_id").getAsString();
			long offset = first;
			progress.onProgress(offset, size);

			while (offset < size) {
				int read = in.readNBytes(buffer, 0, buffer.length);
				if (read <= 0) {
					break;
				}
				byte[] chunk = java.util.Arrays.copyOf(buffer, read);
				long at = offset;

				JsonObject cursor = new JsonObject();
				cursor.addProperty("session_id", sessionId);
				cursor.addProperty("offset", at);
				JsonObject appendArg = new JsonObject();
				appendArg.add("cursor", cursor);
				appendArg.addProperty("close", false);

				http.send(() -> HttpRequest.newBuilder(
						URI.create(CONTENT + "/files/upload_session/append_v2"))
						.timeout(Duration.ofMinutes(5))
						.header("Dropbox-API-Arg", jsonHeader(appendArg))
						.header("Content-Type", "application/octet-stream")
						.POST(HttpRequest.BodyPublishers.ofByteArray(chunk)),
					HttpResponse.BodyHandlers.ofString());

				offset += read;
				progress.onProgress(offset, size);
			}

			JsonObject cursor = new JsonObject();
			cursor.addProperty("session_id", sessionId);
			cursor.addProperty("offset", offset);
			JsonObject finishArg = new JsonObject();
			finishArg.add("cursor", cursor);
			finishArg.add("commit", uploadArg(path));

			http.send(() -> HttpRequest.newBuilder(
					URI.create(CONTENT + "/files/upload_session/finish"))
					.timeout(Duration.ofMinutes(5))
					.header("Dropbox-API-Arg", jsonHeader(finishArg))
					.header("Content-Type", "application/octet-stream")
					.POST(HttpRequest.BodyPublishers.noBody()),
				HttpResponse.BodyHandlers.ofString());

		} catch (IOException e) {
			throw new CloudException("Could not read " + localFile, e);
		}
	}

	@Override
	public void downloadFile(String path, Path target, ProgressListener progress)
		throws CloudException {
		JsonObject arg = new JsonObject();
		arg.addProperty("path", absolute(path));

		Path temp = target.resolveSibling(target.getFileName() + ".part");
		try {
			Files.createDirectories(target.getParent());

			HttpResponse<Path> response = http.sendFollowingRedirects(
				() -> HttpRequest.newBuilder(URI.create(CONTENT + "/files/download"))
					.timeout(Duration.ofMinutes(30))
					.header("Dropbox-API-Arg", jsonHeader(arg))
					.GET(),
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
		JsonObject arg = new JsonObject();
		arg.addProperty("path", absolute(path));
		try {
			rpc("/files/delete_v2", arg);
		} catch (CloudException e) {
			if (!isNotFound(e)) {
				throw e;
			}
		}
	}

	private JsonObject rpc(String endpoint, JsonObject body) throws CloudException {
		HttpResponse<String> response = http.send(
			() -> HttpRequest.newBuilder(URI.create(RPC + endpoint))
				.timeout(Duration.ofMinutes(2))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8)),
			HttpResponse.BodyHandlers.ofString());

		return GSON.fromJson(response.body(), JsonObject.class);
	}

	private static JsonObject uploadArg(String path) {
		JsonObject arg = new JsonObject();
		arg.addProperty("path", absolute(path));
		arg.addProperty("mode", "overwrite");
		arg.addProperty("autorename", false);
		arg.addProperty("mute", true);
		return arg;
	}

	private static String absolute(String path) {
		return path.startsWith("/") ? path : "/" + path;
	}

	static String jsonHeader(JsonObject arg) {
		String json = GSON.toJson(arg);
		StringBuilder out = new StringBuilder(json.length());
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c < 0x20 || c > 0x7E) {
				out.append(String.format("\\u%04x", (int) c));
			} else {
				out.append(c);
			}
		}
		return out.toString();
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
		return message != null && (message.contains("path/not_found") || message.contains("not_found"));
	}

	private static String optString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}
}
