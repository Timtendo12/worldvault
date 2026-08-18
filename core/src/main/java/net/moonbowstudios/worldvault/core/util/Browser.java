package net.moonbowstudios.worldvault.core.util;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class Browser {

	private Browser() {
	}

	public static void open(URI uri) throws IOException {
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalArgumentException("Refusing to open a non-https URL: " + uri.getScheme());
		}

		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		List<String> command;

		if (os.contains("mac") || os.contains("darwin")) {
			command = List.of("open", uri.toString());
		} else if (os.contains("win")) {
			// rundll32 keeps the url away from cmd.exe
			command = List.of("rundll32", "url.dll,FileProtocolHandler", uri.toString());
		} else {
			command = List.of("xdg-open", uri.toString());
		}

		new ProcessBuilder(command)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
	}
}
