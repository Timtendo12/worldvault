package net.moonbowstudios.worldvault.core.cloud;

@FunctionalInterface
public interface ProgressListener {

	ProgressListener NONE = (transferred, total) -> { };

	void onProgress(long bytesTransferred, long bytesTotal);
}
