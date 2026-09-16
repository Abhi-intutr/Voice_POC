# Voice Cloning POC

A local, three-part proof of concept:

```
React (frontend)  --http-->  Spring Boot (backend)  --http-->  Flask (tts-service)
   :5173                          :8080                              :8000
                                                                (MLX + Qwen3-TTS 0.6B,
                                                                 Apple Silicon only)
```

1. **frontend/** — upload a reference clip, type its transcript, type new text, hit generate.
2. **backend/** — Java 21 / Spring Boot. Validates input, stores files, forwards the
   request to the TTS service, stores the result, serves it back.
3. **tts-service/** — Python, runs [MLX-Audio](https://github.com/Blaizzy/mlx-audio)'s
   Qwen3-TTS 0.6B model natively on Apple Silicon (Metal, no CUDA) to actually clone
   the voice.

No database, no auth, no Docker — everything is local files, on purpose, for a POC.

## Before you start: read this

This project was written and reviewed on a Linux sandbox, **not** on an actual Mac,
because that's the only environment available to the assistant that wrote it. Two
consequences you should know about:

- **`tts-service` cannot be run or tested outside macOS/Apple Silicon.** MLX only
  builds on Apple Silicon; `pip install mlx-audio` will simply fail on Linux/Windows/
  Intel Mac. Its Python syntax was checked (`python3 -m py_compile app.py`, passes),
  and its API calls were confirmed against MLX-Audio's own documented Qwen3-TTS
  voice-cloning example (see `tts-service/README.md` for the exact source), but the
  actual model load + inference has **not** been executed by the assistant.
- **`backend` (Spring Boot) has not been compiled.** Maven Central wasn't reachable
  from the sandbox's network (an outbound allowlist blocked it), so `mvn compile`
  couldn't fetch Spring Boot's dependencies. The code was written carefully and
  reviewed by hand, but you're the first to actually build it — see the
  Troubleshooting section below if something doesn't compile.
- **`frontend` was built and linted successfully** (`npm run build`, `oxlint`) in the
  sandbox, so that part is verified working.

None of this should stop the POC from working on your actual M1 Mac — it just means
you're doing the first real end-to-end run, not re-running something already proven
to work. If backend compilation fails, it's most likely a small dependency-version
mismatch, not a fundamental design issue; check the error message against
`backend/pom.xml`.

## Prerequisites (Apple Silicon Mac, 8 GB RAM)

| Tool | Version | Notes |
|---|---|---|
| macOS | any recent version | Apple Silicon (M1/M2/M3/M4) required for MLX |
| Python | **3.11** | Do **not** use the system Python 3.13 — create a 3.11 venv (see below) |
| Node.js | 18+ | for the React/Vite frontend |
| Java | 21+ | for Spring Boot |
| Maven | 3.8+ | to build/run the backend |

Install what you're missing, e.g. with Homebrew:

```bash
brew install python@3.11 node openjdk@21 maven
```

If `python3.11` isn't on your PATH after that, use the full path Homebrew prints
(usually `/opt/homebrew/bin/python3.11` or `/opt/homebrew/opt/python@3.11/bin/python3.11`).

## 1. Set up the TTS service

```bash
cd tts-service
python3.11 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
```

Start it:

```bash
python app.py
```

**What to expect:** the first start downloads `mlx-community/Qwen3-TTS-12Hz-0.6B-Base-bf16`
from Hugging Face — roughly **1–1.5 GB** on disk, into `~/.cache/huggingface`. Loaded
in memory it uses roughly **1.5–2 GB RAM**, which is comfortable on an 8 GB machine as
long as you're not simultaneously running another large model. Subsequent starts skip
the download and load from the local cache in a few seconds.

Leave this running in its own terminal. Check it's healthy:

```bash
curl http://127.0.0.1:8000/health
# {"status": "ready", "model": "mlx-community/Qwen3-TTS-12Hz-0.6B-Base-bf16"}
```

If `status` is `"loading"`, wait and retry — the model is still downloading/loading.
If it's `"error"`, the message will say why (usually: not on Apple Silicon, or mlx-audio
isn't installed correctly).

Full details: [`tts-service/README.md`](tts-service/README.md).

## 2. Start the Spring Boot backend

In a **new terminal** (leave tts-service running):

```bash
cd backend
mvn spring-boot:run
```

This starts on `http://localhost:8080`. It reads `voices/` and `generated/` as paths
*relative to the directory it's started from* (`backend/`), so run it from inside
`backend/` as shown — that resolves to the `voices/` and `generated/` folders at the
project root. Check it can see the TTS service:

```bash
curl http://localhost:8080/api/health
```

- `200 OK` + `"ttsServiceStatus":"ready"` → everything is connected.
- `503` + `"ttsServiceStatus":"unreachable"` → tts-service isn't running or the wrong port.
- `503` + `"ttsServiceStatus":"loading"` → tts-service is still loading the model.

## 3. Start the React frontend

In a **third terminal**:

```bash
cd frontend
npm install
npm run dev
```

Open the URL Vite prints (default `http://localhost:5173`).

## 4. Test the complete workflow

You need a short (a few seconds is plenty) reference audio clip of a voice, plus its
exact transcript. Easiest options on a Mac:

- **QuickTime Player** → File → New Audio Recording → record yourself reading the
  sample transcript below → File → Export As → save as `.m4a` or convert to `.wav`.
- Any existing short voice memo/recording you already have.

**Sample reference transcript** (read this aloud when recording, or paste it as the
transcript for whatever you recorded):

```
This is a short recording of my voice, used only so the model can learn how I sound.
```

**Sample text to generate** (paste into the "text to generate" field):

```
Thanks for trying out the voice cloning proof of concept. If you can hear this in a
voice that sounds like the reference recording, it worked.
```

The frontend has a "Use sample text" shortcut that fills both fields in for you (it
won't overwrite anything you've already typed).

In the UI: upload the clip → paste/confirm the transcript → paste the text to
generate → **Generate speech** → wait (first generation after tts-service starts is
slower while MLX warms up; a short sentence should still take well under a minute on
an M1) → play or download the result.

### Testing the backend directly with curl

```bash
# 1. Kick off generation
curl -X POST http://localhost:8080/api/voice/generate \
  -F "referenceAudio=@/path/to/your/reference.wav" \
  -F "referenceText=This is a short recording of my voice, used only so the model can learn how I sound." \
  -F "text=Thanks for trying out the voice cloning proof of concept. If you can hear this in a voice that sounds like the reference recording, it worked."

# -> {"success":true,"audioUrl":"/api/audio/<uuid>"}

# 2. Fetch the generated file
curl http://localhost:8080/api/audio/<uuid> -o generated.wav

# 3. Health check
curl http://localhost:8080/api/health
```

## Configuration reference

All backend config lives in `backend/src/main/resources/application.yml`
(`app.*` keys) — nothing is hardcoded:

| Key | Default | Meaning |
|---|---|---|
| `app.storage.upload-dir` | `../voices` | where reference audio is stored |
| `app.storage.generated-dir` | `../generated` | where generated audio is stored |
| `app.tts.base-url` | `http://127.0.0.1:8000` | tts-service URL |
| `app.tts.timeout-seconds` | `180` | HTTP timeout for a generation call |
| `app.cors.allowed-origins` | `localhost:5173`, `localhost:3000` | allowed frontend origins |
| `app.upload.max-audio-bytes` | `20971520` (20 MB) | max reference-audio upload size |
| `app.upload.allowed-audio-extensions` | wav, mp3, m4a, flac, ogg | accepted formats |
| `app.upload.max-text-length` | `2000` | max chars for generation text |

tts-service config is via environment variables — see `tts-service/README.md`.

## Known limitations (by design, for a POC)

- No database — files are looked up by UUID filename on local disk.
- No authentication.
- Single request at a time in tts-service (`threaded=False`) — MLX model calls
  aren't safe to run concurrently in this simple wrapper; a second request while
  one is in flight will just queue behind it.
- No automatic cleanup of `voices/`/`generated/` — they'll accumulate; delete
  their contents manually if disk space matters.
- No Docker/Kubernetes/cloud infra, per the brief.

## Troubleshooting

- **`mvn spring-boot:run` fails to resolve dependencies** — check you have network
  access to Maven Central and are running Maven 3.8+ / Java 21+ (`mvn -version`,
  `java -version`).
- **tts-service `/health` says `"error"`** — read the `error` field; the most common
  cause is not running on Apple Silicon macOS, or a partially-installed `mlx-audio`.
  Re-run `pip install -r requirements.txt` inside the activated `.venv`.
- **CORS errors in the browser console** — confirm the frontend's origin
  (`http://localhost:5173` by default) is listed under `app.cors.allowed-origins`
  in `application.yml`, and that you restarted the backend after any change.
- **Generation is slow or the Mac gets warm** — expected on first generation while
  MLX/Metal warms up; short sentences after that should be fast on an M1.

## Starting the complete POC (quick reference)

Three terminals, in this order:

```bash
# Terminal 1
cd tts-service && source .venv/bin/activate && python app.py

# Terminal 2
cd backend && mvn spring-boot:run

# Terminal 3
cd frontend && npm run dev
```

Then open the frontend URL (default `http://localhost:5173`).
