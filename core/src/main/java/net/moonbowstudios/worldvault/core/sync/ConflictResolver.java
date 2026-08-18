package net.moonbowstudios.worldvault.core.sync;

import net.moonbowstudios.worldvault.core.cloud.RemoteManifest;

public final class ConflictResolver {

	public record LastSync(long generation, String deviceId) {
		public static final LastSync NEVER = new LastSync(0L, null);

		public boolean everSynced() {
			return deviceId != null;
		}
	}

	public enum Decision {
		UPLOAD_NEW,
		UPLOAD,
		DOWNLOAD,
		CONFLICT,
		UP_TO_DATE,
		VERSION_MISMATCH
	}

	private ConflictResolver() {
	}

	public static Decision decide(RemoteManifest remote, LastSync last, boolean localChanged,
	                              String thisDeviceId, String thisMcVersion) {
		if (remote == null) {
			return Decision.UPLOAD_NEW;
		}

		if (remote.mcVersion() != null && !remote.mcVersion().equals(thisMcVersion)) {
			return Decision.VERSION_MISMATCH;
		}

		boolean remoteAdvanced = remote.generation() > last.generation();
		boolean sameDevice = thisDeviceId.equals(remote.deviceId());

		if (!last.everSynced()) {
			return localChanged ? Decision.CONFLICT : Decision.DOWNLOAD;
		}

		if (remoteAdvanced && !sameDevice) {
			return localChanged ? Decision.CONFLICT : Decision.DOWNLOAD;
		}

		if (localChanged || remote.generation() < last.generation()) {
			return Decision.UPLOAD;
		}

		return Decision.UP_TO_DATE;
	}
}
