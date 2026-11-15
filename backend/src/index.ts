/**
 * FreshKeep-backend (Cloudflare Worker).
 *
 * - De Gemini API-sleutel staat ALLEEN hier, als secret (GEMINI_API_KEY).
 * - De app stuurt een app-token (X-App-Token) en een anoniem toestel-ID (X-Device-Id).
 * - Maximaal DAGLIMIET verzoeken per toestel per dag (UTC), bijgehouden in een Durable Object.
 * - Foto's worden enkel in het geheugen doorgegeven aan Gemini en nooit opgeslagen of gelogd.
 */
import { DurableObject } from "cloudflare:workers";

export interface Env {
  GEMINI_API_KEY: string;
  APP_TOKEN: string;
  GEMINI_MODEL: string;
  DAGLIMIET: string;
  DAGLIMIET_DO: DurableObjectNamespace<Daglimiet>;
}

const MAX_FOTO_BYTES = 4 * 1024 * 1024;

// ---------------------------------------------------------------------------
// Daglimiet per toestel
// ---------------------------------------------------------------------------

export class Daglimiet extends DurableObject<Env> {
  /** Telt één verzoek; geeft false terug als de limiet voor vandaag bereikt is. */
  async tel(limiet: number): Promise<{ toegestaan: boolean; resterend: number }> {
    const vandaag = new Date().toISOString().slice(0, 10);
    const opgeslagen = await this.ctx.storage.get<{ dag: string; aantal: number }>("teller");
    const aantal = opgeslagen?.dag === vandaag ? opgeslagen.aantal : 0;
    if (aantal >= limiet) return { toegestaan: false, resterend: 0 };
    await this.ctx.storage.put("teller", { dag: vandaag, aantal: aantal + 1 });
    return { toegestaan: true, resterend: limiet - aantal - 1 };
  }
}

// ---------------------------------------------------------------------------
// Prompts en JSON-schema's
// ---------------------------------------------------------------------------

const DATUM_PROMPT =
  "Lees alleen de vervaldatum op deze verpakking. Geef enkel JSON terug met datum (YYYY-MM-DD), " +
  "type (THT/TGT/onbekend) en zekerheid (0-1). Verzin niets; als je geen datum ziet, geef datum null.";

function datumRegels(vandaag: string): string {
  return [
    "Extra regels:",
    "- THT = ten minste houdbaar tot, tenminste houdbaar tot, best before (end), BBE, à consommer de préférence avant, DDM, DLUO, mindestens haltbar bis, MHD.",
    "- TGT = te gebruiken tot, use by, à consommer jusqu'au, DLC, zu verbrauchen bis.",
    "- Begrijp alle notaties: 15/11/26, 15.11.2026, 15-11-2026, 11-2026, 11/26, NOV 2026, 15 NOV 26, 3 déc. 2026.",
    "- De dag staat vóór de maand (Europese volgorde). Een jaartal met twee cijfers is 20xx.",
    "- Staan alleen maand en jaar vermeld, geef dan de LAATSTE dag van die maand.",
    "- Negeer productiedatums (geproduceerd op, prod., fabriqué le), lotnummers, prijzen en gewichten.",
    "- Zie je meerdere vervaldatums, kies de duidelijkste die bij THT/TGT hoort.",
    "- zekerheid: hoe zeker je bent dat de datum correct gelezen is (0 = gok, 1 = volledig zeker).",
    `- Vandaag is ${vandaag}.`,
  ].join("\n");
}

const PRODUCT_PROMPT =
  "Welk voedingsproduct staat op deze verpakking? Geef enkel JSON met naam (korte, gewone productnaam in het " +
  "Nederlands zoals op een boodschappenlijst, bijvoorbeeld 'Halfvolle melk' of 'Griekse yoghurt'), merk (of null) " +
  "en zekerheid (0-1). Verzin niets; als je het product niet herkent, geef naam null.";

function geopendPrompt(naam: string, categorie: string | null, locatie: string): string {
  return (
    `Een product is net geopend en wordt bewaard in: ${locatie}.\n` +
    `Product: ${naam}${categorie ? ` (categorie: ${categorie})` : ""}.\n` +
    "Hoeveel dagen blijft het na openen veilig en goed om op te eten? Volg gangbaar voedselveiligheidsadvies " +
    "en wees eerder voorzichtig. Geef enkel JSON met dagen (geheel getal) en uitleg (één korte Nederlandse zin)."
  );
}

const DATUM_SCHEMA = {
  type: "OBJECT",
  properties: {
    datum: { type: "STRING", nullable: true, description: "Vervaldatum als YYYY-MM-DD, of null" },
    type: { type: "STRING", enum: ["THT", "TGT", "onbekend"] },
    zekerheid: { type: "NUMBER" },
  },
  required: ["datum", "type", "zekerheid"],
};

const PRODUCT_SCHEMA = {
  type: "OBJECT",
  properties: {
    naam: { type: "STRING", nullable: true },
    merk: { type: "STRING", nullable: true },
    zekerheid: { type: "NUMBER" },
  },
  required: ["naam", "merk", "zekerheid"],
};

const GEOPEND_SCHEMA = {
  type: "OBJECT",
  properties: {
    dagen: { type: "INTEGER" },
    uitleg: { type: "STRING" },
  },
  required: ["dagen", "uitleg"],
};

// ---------------------------------------------------------------------------
// Validatie van wat Gemini teruggeeft (vertrouw nooit blind op het model)
// ---------------------------------------------------------------------------

export function normaliseerDatum(waarde: unknown): string | null {
  if (typeof waarde !== "string") return null;
  const tekst = waarde.trim();
  let m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(tekst);
  if (m) {
    const [j, mnd, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
    const dt = new Date(Date.UTC(j, mnd - 1, d));
    const geldig = dt.getUTCFullYear() === j && dt.getUTCMonth() === mnd - 1 && dt.getUTCDate() === d;
    return geldig && j >= 2000 && j <= 2100 ? tekst : null;
  }
  // "YYYY-MM": laatste dag van de maand.
  m = /^(\d{4})-(\d{2})$/.exec(tekst);
  if (m) {
    const [j, mnd] = [Number(m[1]), Number(m[2])];
    if (mnd < 1 || mnd > 12 || j < 2000 || j > 2100) return null;
    const laatste = new Date(Date.UTC(j, mnd, 0)).getUTCDate();
    return `${m[1]}-${m[2]}-${String(laatste).padStart(2, "0")}`;
  }
  return null;
}

export function normaliseerType(waarde: unknown): "THT" | "TGT" | "onbekend" {
  const t = typeof waarde === "string" ? waarde.trim().toUpperCase() : "";
  return t === "THT" || t === "TGT" ? t : "onbekend";
}

function zekerheid(waarde: unknown): number {
  const n = typeof waarde === "number" && Number.isFinite(waarde) ? waarde : 0;
  return Math.min(1, Math.max(0, n));
}

function tekstOfNull(waarde: unknown, max = 80): string | null {
  if (typeof waarde !== "string") return null;
  const t = waarde.trim();
  return t.length === 0 || t.toLowerCase() === "null" ? null : t.slice(0, max);
}

// ---------------------------------------------------------------------------
// Gemini
// ---------------------------------------------------------------------------

class HttpFout extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function vraagGemini(env: Env, parts: unknown[], schema: unknown): Promise<Record<string, unknown>> {
  const model = env.GEMINI_MODEL || "gemini-flash-latest";
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`;
  const antwoord = await fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
    body: JSON.stringify({
      contents: [{ role: "user", parts }],
      // JSON-uitvoer afdwingen met een schema.
      generationConfig: { responseMimeType: "application/json", responseSchema: schema },
    }),
  });
  if (!antwoord.ok) {
    // Geen details van Google naar de app doorsturen.
    console.error("Gemini-fout", antwoord.status);
    throw new HttpFout(antwoord.status === 429 ? 503 : 502, "AI niet beschikbaar");
  }
  const data = (await antwoord.json()) as {
    candidates?: { content?: { parts?: { text?: string }[] } }[];
  };
  const tekst = data.candidates?.[0]?.content?.parts?.find((p) => typeof p.text === "string")?.text;
  if (!tekst) throw new HttpFout(502, "Leeg antwoord van de AI");
  try {
    return JSON.parse(tekst) as Record<string, unknown>;
  } catch {
    throw new HttpFout(502, "Ongeldig antwoord van de AI");
  }
}

function base64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binair = "";
  const blok = 0x8000;
  for (let i = 0; i < bytes.length; i += blok) {
    binair += String.fromCharCode(...bytes.subarray(i, i + blok));
  }
  return btoa(binair);
}

async function leesFoto(request: Request): Promise<string> {
  const type = request.headers.get("content-type") ?? "";
  if (!type.startsWith("image/jpeg")) throw new HttpFout(415, "Stuur een JPEG (image/jpeg)");
  const lengte = Number(request.headers.get("content-length") ?? "0");
  if (lengte > MAX_FOTO_BYTES) throw new HttpFout(413, "Foto te groot");
  const buffer = await request.arrayBuffer();
  if (buffer.byteLength === 0) throw new HttpFout(400, "Geen foto ontvangen");
  if (buffer.byteLength > MAX_FOTO_BYTES) throw new HttpFout(413, "Foto te groot");
  return base64(buffer);
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------

async function leesDatum(request: Request, env: Env): Promise<Response> {
  const foto = await leesFoto(request);
  const vandaag = new Date().toISOString().slice(0, 10);
  const r = await vraagGemini(
    env,
    [{ inline_data: { mime_type: "image/jpeg", data: foto } }, { text: `${DATUM_PROMPT}\n\n${datumRegels(vandaag)}` }],
    DATUM_SCHEMA,
  );
  const datum = normaliseerDatum(r.datum);
  return json({ datum, type: normaliseerType(r.type), zekerheid: datum ? zekerheid(r.zekerheid) : 0 });
}

async function herkenProduct(request: Request, env: Env): Promise<Response> {
  const foto = await leesFoto(request);
  const r = await vraagGemini(
    env,
    [{ inline_data: { mime_type: "image/jpeg", data: foto } }, { text: PRODUCT_PROMPT }],
    PRODUCT_SCHEMA,
  );
  const naam = tekstOfNull(r.naam);
  return json({ naam, merk: naam ? tekstOfNull(r.merk) : null, zekerheid: naam ? zekerheid(r.zekerheid) : 0 });
}

async function geopendAdvies(request: Request, env: Env): Promise<Response> {
  let body: { naam?: unknown; categorie?: unknown; locatie?: unknown };
  try {
    body = await request.json();
  } catch {
    throw new HttpFout(400, "Ongeldige JSON");
  }
  const naam = tekstOfNull(body.naam);
  if (!naam) throw new HttpFout(400, "naam ontbreekt");
  const r = await vraagGemini(
    env,
    [{ text: geopendPrompt(naam, tekstOfNull(body.categorie), tekstOfNull(body.locatie, 20) ?? "Koelkast") }],
    GEOPEND_SCHEMA,
  );
  const dagen = typeof r.dagen === "number" && Number.isFinite(r.dagen) ? Math.round(r.dagen) : null;
  return json({ dagen: dagen !== null && dagen >= 0 && dagen <= 365 ? dagen : null, uitleg: tekstOfNull(r.uitleg, 200) });
}

// ---------------------------------------------------------------------------
// Router, beveiliging en limiet
// ---------------------------------------------------------------------------

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

/** Vergelijking in constante tijd, zodat het token niet via timing te raden is. */
function gelijk(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const x = enc.encode(a);
  const y = enc.encode(b);
  let verschil = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) verschil |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return verschil === 0;
}

const ROUTES: Record<string, (req: Request, env: Env) => Promise<Response>> = {
  "/read-date": leesDatum,
  "/identify-product": herkenProduct,
  "/opened-shelf-life": geopendAdvies,
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);

    if (pathname === "/health" && request.method === "GET") return json({ ok: true });

    const handler = ROUTES[pathname];
    if (!handler) return json({ fout: "Niet gevonden" }, 404);
    if (request.method !== "POST") return json({ fout: "Alleen POST" }, 405);

    if (!env.APP_TOKEN || !env.GEMINI_API_KEY) return json({ fout: "Server niet geconfigureerd" }, 500);
    const token = request.headers.get("x-app-token") ?? "";
    if (!gelijk(token, env.APP_TOKEN)) return json({ fout: "Niet toegestaan" }, 401);

    const toestel = request.headers.get("x-device-id") ?? "";
    if (!/^[A-Za-z0-9-]{8,64}$/.test(toestel)) return json({ fout: "Ongeldig toestel-ID" }, 400);

    const limiet = Number(env.DAGLIMIET) || 100;
    const teller = env.DAGLIMIET_DO.get(env.DAGLIMIET_DO.idFromName(toestel));
    const { toegestaan, resterend } = await teller.tel(limiet);
    if (!toegestaan) return json({ fout: "Daglimiet bereikt" }, 429);

    try {
      const antwoord = await handler(request, env);
      antwoord.headers.set("x-ratelimit-remaining", String(resterend));
      return antwoord;
    } catch (e) {
      if (e instanceof HttpFout) return json({ fout: e.message }, e.status);
      console.error("Onverwachte fout", e instanceof Error ? e.name : "onbekend");
      return json({ fout: "Interne fout" }, 500);
    }
  },
} satisfies ExportedHandler<Env>;
