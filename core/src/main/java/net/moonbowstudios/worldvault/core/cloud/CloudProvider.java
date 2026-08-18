package net.moonbowstudios.worldvault.core.cloud;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface CloudProvider {

	String id();

	String displayName();

	List<String> listWorldFolders() throws CloudException;

	Optional<byte[]> readSmallFile(String path) throws CloudException;

	void writeSmallFile(String path, byte[] content) throws CloudException;

	void uploadFile(String path, Path localFile, ProgressListener progress) throws CloudException;

	void downloadFile(String path, Path target, ProgressListener progress) throws CloudException;

	void deleteFile(String path) throws CloudException;

	void deleteFolder(String path) throws CloudException;
}
