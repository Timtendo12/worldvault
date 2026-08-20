const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

const ALLOWED_FIELDS = new Set([
	"grant_type",
	"code",
	"code_verifier",
	"redirect_uri",
	"refresh_token",
	"client_id",
]);

const INSTALL_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

// the client clamps, so this only rejects outright nonsense
const MAX_WORLDS = 1_000_000;

const THIRTY_DAYS_MS = 30 * 86_400_000;

export interface Env {
	GOOGLE_CLIENT_ID: string;
	GOOGLE_CLIENT_SECRET: string;
	STATS_TOKEN: string;
	DB: D1Database;
}

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url);

		switch (url.pathname) {
			case "/google/token":
				return request.method === "POST" ? googleToken(request, env) : methodNotAllowed();
			case "/v1/ping":
				return request.method === "POST" ? ping(request, env) : methodNotAllowed();
			case "/v1/stats":
				return request.method === "GET" ? stats(request, env) : methodNotAllowed();
			default:
				return json({ error: "not_found" }, 404);
		}
	},
};

/** Appends the Google client secret, which cannot ship inside a Minecraft mod jar. */
async function googleToken(request: Request, env: Env): Promise<Response> {
	if (!env.GOOGLE_CLIENT_ID || !env.GOOGLE_CLIENT_SECRET) {
		return json(
			{
				error: "server_error",
				error_description: "GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET must both be set",
			},
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
}

/** Records one install and its world count. Stores nothing else; timestamps are Unix ms. */
async function ping(request: Request, env: Env): Promise<Response> {
	if (!env.DB) {
		return json({ error: "server_error", error_description: "DB is not bound" }, 500);
	}

	let body: URLSearchParams;
	try {
		body = new URLSearchParams(await request.text());
	} catch {
		return json({ error: "invalid_request" }, 400);
	}

	const id = body.get("id") ?? "";
	if (!INSTALL_ID.test(id)) {
		return json({ error: "invalid_id" }, 400);
	}

	const worlds = Number(body.get("worlds"));
	if (!Number.isInteger(worlds) || worlds < 0 || worlds > MAX_WORLDS) {
		return json({ error: "invalid_worlds" }, 400);
	}

	const now = Date.now();
	await env.DB.prepare(
		"INSERT INTO installs (id, first_seen, last_seen, worlds) VALUES (?1, ?2, ?2, ?3) " +
			"ON CONFLICT(id) DO UPDATE SET last_seen = ?2, worlds = ?3",
	)
		.bind(id, now, worlds)
		.run();

	return new Response(null, { status: 204 });
}

interface Totals {
	installs: number;
	worlds: number;
}

interface Active {
	active30d: number;
}

async function stats(request: Request, env: Env): Promise<Response> {
	if (!env.DB) {
		return json({ error: "server_error", error_description: "DB is not bound" }, 500);
	}
	if (!env.STATS_TOKEN) {
		return json({ error: "server_error", error_description: "STATS_TOKEN is not set" }, 500);
	}
	if (request.headers.get("Authorization") !== `Bearer ${env.STATS_TOKEN}`) {
		return json({ error: "unauthorized" }, 401);
	}

	const [totals, active] = await env.DB.batch([
		env.DB.prepare("SELECT COUNT(*) AS installs, COALESCE(SUM(worlds), 0) AS worlds FROM installs"),
		env.DB.prepare("SELECT COUNT(*) AS active30d FROM installs WHERE last_seen >= ?1").bind(
			Date.now() - THIRTY_DAYS_MS,
		),
	]);

	const counts = (totals.results as unknown as Totals[])[0];
	const recent = (active.results as unknown as Active[])[0];

	return json(
		{
			installs: counts?.installs ?? 0,
			active30d: recent?.active30d ?? 0,
			worlds: counts?.worlds ?? 0,
		},
		200,
	);
}

function methodNotAllowed(): Response {
	return json({ error: "method_not_allowed" }, 405);
}

function json(body: unknown, status: number): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { "Content-Type": "application/json" },
	});
}
