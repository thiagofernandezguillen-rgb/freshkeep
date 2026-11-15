# FreshKeep-backend (Cloudflare Worker)

Deze Worker is de enige plek waar de Gemini API-sleutel staat. De app praat alleen met deze Worker.

| Endpoint | Body | Antwoord |
|---|---|---|
| `POST /read-date` | JPEG-foto (`image/jpeg`, max 4 MB) | `{"datum": "YYYY-MM-DD" \| null, "type": "THT" \| "TGT" \| "onbekend", "zekerheid": 0-1}` |
| `POST /identify-product` | JPEG-foto | `{"naam": string \| null, "merk": string \| null, "zekerheid": 0-1}` |
| `POST /opened-shelf-life` | `{"naam", "categorie", "locatie"}` | `{"dagen": int \| null, "uitleg": string \| null}` |
| `GET /health` | – | `{"ok": true}` |

**Beveiliging**
- Elke POST heeft de header `X-App-Token` (gedeeld geheim) en `X-Device-Id` (anoniem toestel-ID) nodig.
- Maximaal 100 verzoeken per dag per toestel (instelbaar via `DAGLIMIET` in `wrangler.toml`). Daarboven krijg je HTTP 429.
- Foto's worden alleen in het geheugen doorgestuurd naar Gemini en nooit opgeslagen of gelogd.

## Vereisten

- [Node.js](https://nodejs.org) 18 of nieuwer
- Een gratis [Cloudflare-account](https://dash.cloudflare.com/sign-up)
- Een Gemini API-sleutel van [Google AI Studio](https://aistudio.google.com/apikey)

## Deployen, stap voor stap

Voer alles uit in de map `backend`:

```powershell
cd backend
npm install
```

1. **Aanmelden bij Cloudflare** (opent de browser):
   ```powershell
   npx wrangler login
   ```

2. **De Gemini-sleutel als secret instellen** (plak de sleutel als erom gevraagd wordt):
   ```powershell
   npx wrangler secret put GEMINI_API_KEY
   ```

3. **Een app-token maken en instellen.** Maak een willekeurige lange tekst, bijvoorbeeld zo:
   ```powershell
   [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")
   ```
   Stel die in als secret:
   ```powershell
   npx wrangler secret put APP_TOKEN
   ```

4. **Deployen:**
   ```powershell
   npx wrangler deploy
   ```
   Wrangler toont de URL, bijvoorbeeld `https://freshkeep-backend.<jouw-subdomein>.workers.dev`.

5. **Testen:**
   ```powershell
   curl.exe https://freshkeep-backend.<jouw-subdomein>.workers.dev/health
   ```
   Een vervaldatum testen met een foto:
   ```powershell
   curl.exe -X POST "https://freshkeep-backend.<jouw-subdomein>.workers.dev/read-date" `
     -H "X-App-Token: <jouw-token>" -H "X-Device-Id: test-toestel-1" `
     -H "Content-Type: image/jpeg" --data-binary "@foto.jpg"
   ```

## De app koppelen

Zet in `local.properties` in de hoofdmap van het Android-project (dit bestand gaat **niet** mee in versiebeheer):

```properties
freshkeep.backendUrl=https://freshkeep-backend.<jouw-subdomein>.workers.dev/
freshkeep.appToken=<hetzelfde token als in stap 3>
```

Daarna in Android Studio *Build > Rebuild Project*. De waarden komen via `BuildConfig.BACKEND_URL` en
`BuildConfig.APP_TOKEN` in de app. Zonder deze instellingen werkt de app gewoon, maar zonder AI:
de datumkiezer opent dan meteen en de naam typ je zelf.

> Let op: het app-token zit wel in de APK en is dus geen echt geheim. Het houdt toevallig misbruik tegen;
> de daglimiet per toestel beperkt de kosten. De Gemini-sleutel zelf verlaat de Worker nooit.

## Lokaal ontwikkelen

Maak `backend/.dev.vars` (staat in `.gitignore`):

```
GEMINI_API_KEY=...
APP_TOKEN=...
```

Start dan `npm run dev`. De Worker draait op `http://localhost:8787`. Vanuit de Android-emulator is dat
`http://10.0.2.2:8787/`.

## Model aanpassen

In `wrangler.toml` staat `GEMINI_MODEL = "gemini-flash-latest"`. Dat is een Gemini-model met beeldherkenning.
Wil je een vaste versie, vul dan een concrete modelnaam uit de
[Gemini-modellenlijst](https://ai.google.dev/gemini-api/docs/models) in en deploy opnieuw.
JSON-uitvoer wordt afgedwongen met `responseMimeType: application/json` en een `responseSchema`.

## Logs bekijken

```powershell
npm run tail
```
