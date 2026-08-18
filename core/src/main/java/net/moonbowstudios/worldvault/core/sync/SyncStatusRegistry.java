package net.moonbowstudios.worldvault.core.sync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SyncStatusRegistry {

	private static final Map<String, WorldSyncStatus> STATUSES = new ConcurrentHashMap<>();

	private static volatile Consumer<String> listener = levelId -> { };

	private SyncStatusRegistry() {
	}

	public static WorldSyncStatus get(String levelId) {
		WorldSyncStatus status = STATUSES.get(levelId);
		return status != null ? status : WorldSyncStatus.NOT_LINKED;
	}

	public static void put(String levelId, WorldSyncStatus status) {
		STATUSES.put(levelId, status);
		listener.accept(levelId);
	}

	public static void remove(String levelId) {
		if (STATUSES.remove(levelId) != null) {
			listener.accept(levelId);
		}
	}

	public static void replaceAll(Map<String, WorldSyncStatus> statuses) {
		STATUSES.keySet().retainAll(statuses.keySet());
		STATUSES.putAll(statuses);
		statuses.keySet().forEach(listener::accept);
	}

	public static void clear() {
		STATUSES.clear();
	}

	public static void setListener(Consumer<String> newListener) {
		listener = newListener != null ? newListener : levelId -> { };
	}
}
