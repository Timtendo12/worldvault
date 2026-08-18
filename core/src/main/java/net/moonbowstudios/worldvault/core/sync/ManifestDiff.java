package net.moonbowstudios.worldvault.core.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public record ManifestDiff(List<String> upload, List<String> delete, long uploadBytes) {

	public ManifestDiff {
		upload = Collections.unmodifiableList(new ArrayList<>(upload));
		delete = Collections.unmodifiableList(new ArrayList<>(delete));
	}

	public static ManifestDiff between(LocalManifest local, Map<String, String> remoteHashes) {
		List<String> upload = new ArrayList<>();
		long bytes = 0L;

		for (Map.Entry<String, LocalManifest.Entry> entry : local.files().entrySet()) {
			String remoteHash = remoteHashes.get(entry.getKey());
			if (!entry.getValue().sha256().equals(remoteHash)) {
				upload.add(entry.getKey());
				bytes += entry.getValue().size();
			}
		}

		List<String> delete = new ArrayList<>(new TreeSet<>(remoteHashes.keySet()));
		delete.removeAll(local.files().keySet());

		return new ManifestDiff(upload, delete, bytes);
	}

	public boolean isEmpty() {
		return upload.isEmpty() && delete.isEmpty();
	}
}
