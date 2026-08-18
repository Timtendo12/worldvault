const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

const ALLOWED_FIELDS = new Set([
	"grant_type",
	"code",
	"code_verifier",
	"redirect_uri",
	"refresh_token",
	"client_id",
]);

export interface Env {
	GOOGLE_CLIENT_ID: string;
	GOOGLE_CLIENT_SECRET: string;
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url);

		if (url.pathname !== "/google/token") {
			return json({ error: "not_found" }, 404);
		}
		if (request.method !== "POST") {
			return json({ error: "method_not_allowed" }, 405);
		}
		if (!env.GOOGLE_CLIENT_SECRET) {
			return json(
				{ error: "server_error", error_description: "GOOGLE_CLIENT_SECRET is not set" },
				500,
			);
		}

		let incoming: URLSearchParams;
		try {
			incoming = new URLSearchParams(await request.text());
		} catch {
			return json({ error: "invalid_request" }, 400);
		}

		// allowlist the body so this cannot be used as an open relay
		const outgoing = new URLSearchParams();
		for (const [key, value] of incoming) {
			if (ALLOWED_FIELDS.has(key)) {
				outgoing.set(key, value);
			}
		}
		const grant = outgoing.get("grant_type");
		if (grant !== "authorization_code" && grant !== "refresh_token") {
			return json({ error: "unsupported_grant_type" }, 400);
		}

		outgoing.set("client_id", env.GOOGLE_CLIENT_ID);
		outgoing.set("client_secret", env.GOOGLE_CLIENT_SECRET);

		const upstream = await fetch(GOOGLE_TOKEN_URL, {
			method: "POST",
			headers: {
				"Content-Type": "application/x-www-form-urlencoded",
				Accept: "application/json",
			},
			body: outgoing.toString(),
		});

		return new Response(upstream.body, {
			status: upstream.status,
			headers: { "Content-Type": "application/json" },
		});
	},
};

function json(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { "Content-Type": "application/json" },
	});
}
