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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GoogleDriveProvider implements CloudProvider {

	private static final String API = "https://www.googleapis.com/drive/v3";
	private static final String UPLOAD_API = "https://www.googleapis.com/upload/drive/v3";

	private static final String FOLDER_MIME = "application/vnd.google-apps.folder";
	private static final String ROOT_FOLDER_NAME = "WorldVault";

	private static final int CHUNK_UNIT = 256 * 1024;
	private static final int CHUNK_BYTES = 32 * CHUNK_UNIT;   // 8 MiB
	private static final long SIMPLE_UPLOAD_LIMIT = 5L * 1024 * 1024;

	private static final Gson GSON = new Gson();

	private final CloudHttp http;
	private final Map<String, String> folderIds = new ConcurrentHashMap<>();

	public GoogleDriveProvider(CloudHttp http) {
		this.http = http;
	}

	@Override
	public String id() {
		return "googledrive";
	}

	@Override
	public String displayName() {
		return "Google Drive";
	}

	@Override
	public List<String> listWorldFolders() throws CloudException {
		String root = rootFolderId();
		List<String> names = new ArrayList<>();

		for (JsonObject child : listChildren(root, true)) {
			names.add(child.get("name").getAsString());
		}
		return names;
	}

	@Override
	public Optional<byte[]> readSmallFile(String path) throws CloudException {
		Optional<String> fileId = findFileId(path);
		if (fileId.isEmpty()) {
			return Optional.empty();
		}

		HttpResponse<byte[]> response = http.send(
			() -> HttpRequest.newBuilder(URI.create(API + "/files/" + fileId.get() + "?alt=media"))
				.timeout(Duration.ofMinutes(2)).GET(),
			HttpResponse.BodyHandlers.ofByteArray());
		return Optional.of(response.body());
	}

	@Override
	public void writeSmallFile(String path, byte[] content) throws CloudException {
		uploadBytes(path, content);
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
			try {
				uploadBytes(path, Files.readAllBytes(localFile));
			} catch (IOException e) {
				throw new CloudException("Could not read " + localFile, e);
			}
			progress.onProgress(size, size);
			return;
		}

		String sessionUrl = startResumableSession(path, size);
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

				http.sendUnauthenticated(() -> HttpRequest.newBuilder(URI.create(sessionUrl))
						.timeout(Duration.ofMinutes(10))
						.header("Content-Range", range)
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
		String fileId = findFileId(path)
			.orElseThrow(() -> new CloudException("Not in Google Drive: " + path));

		Path temp = target.resolveSibling(target.getFileName() + ".part");
		try {
			Files.createDirectories(target.getParent());

			HttpResponse<Path> response = http.sendFollowingRedirects(
				() -> HttpRequest.newBuilder(URI.create(API + "/files/" + fileId + "?alt=media"))
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
		Optional<String> fileId = findFileId(path);
		if (fileId.isPresent()) {
			deleteById(fileId.get());
		}
	}

	@Override
	public void deleteFolder(String path) throws CloudException {
		String parentPath = parentOf(path);
		String name = nameOf(path);
		Optional<String> parentId = resolveFolder(parentPath, false);
		if (parentId.isEmpty()) {
			return;
		}

		Optional<String> folderId = childId(parentId.get(), name, true);
		if (folderId.isPresent()) {
			deleteById(folderId.get());
			folderIds.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"));
		}
	}

	private void uploadBytes(String path, byte[] content) throws CloudException {
		String parentId = resolveFolder(parentOf(path), true).orElseThrow();
		String name = nameOf(path);
		Optional<String> existing = childId(parentId, name, false);

		String boundary = "worldvault" + Long.toHexString(System.nanoTime());
		JsonObject metadata = new JsonObject();
		metadata.addProperty("name", name);
		if (existing.isEmpty()) {
			JsonArray parents = new JsonArray();
			parents.add(parentId);
			metadata.add("parents", parents);
		}

		// simple upload cannot set a name and parent in one request
		byte[] body = multipartBody(boundary, GSON.toJson(metadata), content);
		String url = existing
			.map(fileId -> UPLOAD_API + "/files/" + fileId + "?uploadType=multipart")
			.orElse(UPLOAD_API + "/files?uploadType=multipart");

		http.send(() -> {
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofMinutes(10))
				.header("Content-Type", "multipart/related; boundary=" + boundary);
			return existing.isPresent()
				? request.method("PATCH", HttpRequest.BodyPublishers.ofByteArray(body))
				: request.POST(HttpRequest.BodyPublishers.ofByteArray(body));
		}, HttpResponse.BodyHandlers.ofString());
	}

	private String startResumableSession(String path, long size) throws CloudException {
		String parentId = resolveFolder(parentOf(path), true).orElseThrow();
		String name = nameOf(path);
		Optional<String> existing = childId(parentId, name, false);

		JsonObject metadata = new JsonObject();
		metadata.addProperty("name", name);
		if (existing.isEmpty()) {
			JsonArray parents = new JsonArray();
			parents.add(parentId);
			metadata.add("parents", parents);
		}

		String url = existing
			.map(fileId -> UPLOAD_API + "/files/" + fileId + "?uploadType=resumable")
			.orElse(UPLOAD_API + "/files?uploadType=resumable");

		HttpResponse<String> response = http.send(() -> {
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofMinutes(1))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("X-Upload-Content-Length", Long.toString(size));
			String json = GSON.toJson(metadata);
			return existing.isPresent()
				? request.method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
				: request.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
		}, HttpResponse.BodyHandlers.ofString());

		return response.headers().firstValue("Location").orElseThrow(
			() -> new CloudException("Google Drive did not return an upload session URL."));
	}

	private String rootFolderId() throws CloudException {
		String cached = folderIds.get("");
		if (cached != null) {
			return cached;
		}

		String query = "name = '" + escapeQuery(ROOT_FOLDER_NAME) + "'"
			+ " and mimeType = '" + FOLDER_MIME + "' and trashed = false and 'root' in parents";

		Optional<String> found = firstIdMatching(query);
		String id = found.isPresent() ? found.get() : createFolder(ROOT_FOLDER_NAME, "root");
		folderIds.put("", id);
		return id;
	}

	private Optional<String> resolveFolder(String path, boolean create) throws CloudException {
		if (path.isEmpty()) {
			return Optional.of(rootFolderId());
		}
		String cached = folderIds.get(path);
		if (cached != null) {
			return Optional.of(cached);
		}

		String parentId = rootFolderId();
		StringBuilder walked = new StringBuilder();

		for (String segment : path.split("/")) {
			if (segment.isEmpty()) {
				continue;
			}
			if (!walked.isEmpty()) {
				walked.append('/');
			}
			walked.append(segment);

			String key = walked.toString();
			String known = folderIds.get(key);
			if (known != null) {
				parentId = known;
				continue;
			}

			Optional<String> childId = childId(parentId, segment, true);
			if (childId.isEmpty()) {
				if (!create) {
					return Optional.empty();
				}
				childId = Optional.of(createFolder(segment, parentId));
			}
			folderIds.put(key, childId.get());
			parentId = childId.get();
		}
		return Optional.of(parentId);
	}

	private Optional<String> findFileId(String path) throws CloudException {
		Optional<String> parentId = resolveFolder(parentOf(path), false);
		if (parentId.isEmpty()) {
			return Optional.empty();
		}
		return childId(parentId.get(), nameOf(path), false);
	}

	private Optional<String> childId(String parentId, String name, boolean folder)
		throws CloudException {
		String query = "name = '" + escapeQuery(name) + "' and '" + parentId + "' in parents"
			+ " and trashed = false"
			+ (folder ? " and mimeType = '" + FOLDER_MIME + "'"
			: " and mimeType != '" + FOLDER_MIME + "'");
		return firstIdMatching(query);
	}

	private Optional<String> firstIdMatching(String query) throws CloudException {
		String url = API + "/files?pageSize=1&fields=files(id,name)&q="
			+ net.moonbowstudios.worldvault.core.util.Http.encode(query);

		HttpResponse<String> response = http.send(
			() -> HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(1)).GET(),
			HttpResponse.BodyHandlers.ofString());

		JsonArray files = GSON.fromJson(response.body(), JsonObject.class).getAsJsonArray("files");
		if (files == null || files.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(files.get(0).getAsJsonObject().get("id").getAsString());
	}

	private List<JsonObject> listChildren(String parentId, boolean foldersOnly)
		throws CloudException {
		List<JsonObject> out = new ArrayList<>();
		String query = "'" + parentId + "' in parents and trashed = false"
			+ (foldersOnly ? " and mimeType = '" + FOLDER_MIME + "'" : "");
		String pageToken = null;

		do {
			String url = API + "/files?pageSize=200&fields=nextPageToken,files(id,name)&q="
				+ net.moonbowstudios.worldvault.core.util.Http.encode(query)
				+ (pageToken != null ? "&pageToken=" + pageToken : "");

			HttpResponse<String> response = http.send(
				() -> HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(1)).GET(),
				HttpResponse.BodyHandlers.ofString());

			JsonObject page = GSON.fromJson(response.body(), JsonObject.class);
			JsonArray files = page.getAsJsonArray("files");
			if (files != null) {
				for (JsonElement element : files) {
					out.add(element.getAsJsonObject());
				}
			}
			pageToken = page.has("nextPageToken") ? page.get("nextPageToken").getAsString() : null;
		} while (pageToken != null);

		return out;
	}

	private String createFolder(String name, String parentId) throws CloudException {
		JsonObject metadata = new JsonObject();
		metadata.addProperty("name", name);
		metadata.addProperty("mimeType", FOLDER_MIME);
		JsonArray parents = new JsonArray();
		parents.add(parentId);
		metadata.add("parents", parents);

		HttpResponse<String> response = http.send(
			() -> HttpRequest.newBuilder(URI.create(API + "/files?fields=id"))
				.timeout(Duration.ofMinutes(1))
				.header("Content-Type", "application/json; charset=UTF-8")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(metadata), StandardCharsets.UTF_8)),
			HttpResponse.BodyHandlers.ofString());

		return GSON.fromJson(response.body(), JsonObject.class).get("id").getAsString();
	}

	private void deleteById(String fileId) throws CloudException {
		http.send(() -> HttpRequest.newBuilder(URI.create(API + "/files/" + fileId))
			.timeout(Duration.ofMinutes(1)).DELETE(), HttpResponse.BodyHandlers.ofString());
	}

	static byte[] multipartBody(String boundary, String metadataJson, byte[] content) {
		byte[] head = ("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
			+ metadataJson + "\r\n--" + boundary
			+ "\r\nContent-Type: application/octet-stream\r\n\r\n")
			.getBytes(StandardCharsets.UTF_8);
		byte[] tail = ("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8);

		byte[] body = new byte[head.length + content.length + tail.length];
		System.arraycopy(head, 0, body, 0, head.length);
		System.arraycopy(content, 0, body, head.length, content.length);
		System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
		return body;
	}

	static String escapeQuery(String value) {
		return value.replace("\\", "\\\\").replace("'", "\\'");
	}

	static String parentOf(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	static String nameOf(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1);
	}
}
