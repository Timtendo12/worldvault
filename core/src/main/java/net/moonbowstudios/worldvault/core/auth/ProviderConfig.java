package net.moonbowstudios.worldvault.core.auth;

public record ProviderConfig(String id, String displayName, String authorizeUrl, String tokenUrl,
                             String clientId, String scopes,
                             java.util.Map<String, String> extraAuthArgs) {

	public String folderHint() {
		return switch (id) {
			case "googledrive" -> "files created by WorldVault";
			case "onedrive", "dropbox" -> "Apps/WorldVault";
			default -> "";
		};
	}

	public static ProviderConfig dropbox(String clientId) {
		return new ProviderConfig(
			"dropbox", "Dropbox",
			"https://www.dropbox.com/oauth2/authorize",
			"https://api.dropboxapi.com/oauth2/token",
			clientId,
			"files.content.read files.content.write files.metadata.read account_info.read",
			// dropbox only returns a refresh token when this is set
			java.util.Map.of("token_access_type", "offline"));
	}

	public static ProviderConfig oneDrive(String clientId) {
		return new ProviderConfig(
			"onedrive", "OneDrive",
			"https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
			"https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
			clientId,
			"Files.ReadWrite.AppFolder offline_access",
			java.util.Map.of());
	}

	public static ProviderConfig googleDrive(String clientId, String tokenEndpoint) {
		return new ProviderConfig(
			"googledrive", "Google Drive",
			"https://accounts.google.com/o/oauth2/v2/auth",
			tokenEndpoint,
			clientId,
			"https://www.googleapis.com/auth/drive.file",
			// without these google only issues a refresh token on the first consent
			java.util.Map.of("access_type", "offline", "prompt", "consent"));
	}
}
