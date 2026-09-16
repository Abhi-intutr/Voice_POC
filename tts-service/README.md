# tts-service

A tiny local HTTP wrapper around [MLX-Audio](https://github.com/Blaizzy/mlx-audio)'s
Qwen3-TTS voice-cloning API. Runs natively on Apple Silicon (M1/M2/M3/M4) using
Apple's MLX framework — no CUDA, no NVIDIA GPU, no cloud.

> For full setup instructions (Python version, venv, model download, running
> the whole POC end-to-end) see the **root [README.md](../README.md)**. This
> file only documents this one service.

## Requirements

- macOS on Apple Silicon (arm64). This will NOT run on Linux, Windows, or Intel Macs.
- Python 3.11 (recommended) in a dedicated virtualenv — do not use the system Python.
- ~2–3 GB free disk space for model weights on first run.
- ~1.5–2 GB additional RAM while the model is loaded (fine on an 8 GB Mac; just
  don't run other heavy MLX/ML processes at the same time).

## Install

```bash
cd tts-service
python3.11 -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
```

## Run

```bash
source .venv/bin/activate
python app.py
```

The first request-worthy startup downloads the model weights from Hugging Face
(`mlx-community/Qwen3-TTS-12Hz-0.6B-Base-bf16`, roughly 1.2 GB) into
`~/.cache/huggingface`. Subsequent starts load from the local cache in a few
seconds.

The service listens on `http://127.0.0.1:8000` by default. Override with env vars:

| Variable              | Default                                          | Meaning                              |
|-----------------------|---------------------------------------------------|---------------------------------------|
| `TTS_MODEL_ID`         | `mlx-community/Qwen3-TTS-12Hz-0.6B-Base-bf16`     | MLX-community model repo to load      |
| `TTS_HOST`             | `127.0.0.1`                                       | bind host                             |
| `TTS_PORT`             | `8000`                                            | bind port                             |
| `TTS_MAX_TEXT_LENGTH`  | `2000`                                            | max characters of `text` per request  |
| `TTS_MAX_UPLOAD_BYTES` | `26214400` (25 MB)                                | max reference-audio upload size       |

## API

### `GET /health`

```json
{ "status": "ready", "model": "mlx-community/Qwen3-TTS-12Hz-0.6B-Base-bf16" }
```

`status` is one of `loading`, `ready`, `error` (HTTP 503 for the first and last).

### `POST /tts`

`multipart/form-data` with fields:

- `reference_audio` — file (`.wav`, `.mp3`, `.m4a`, `.flac`, `.ogg`)
- `reference_text` — exact transcript of `reference_audio`
- `text` — text to synthesize in the cloned voice

Returns the generated audio as `audio/wav` bytes, or a JSON `{"error": "..."}`
body on failure (400 for bad input, 503 if the model isn't loaded, 500 for
inference failures).

```bash
curl -X POST http://127.0.0.1:8000/tts \
  -F "reference_audio=@../voices/sample_reference.wav" \
  -F "reference_text=This is what my voice sounds like." \
  -F "text=Hello from the other side." \
  -o test_output.wav
```

## Notes on the model

- `Qwen3-TTS-12Hz-0.6B-Base-*` is used because its `generate()` method accepts
  `ref_audio` + `ref_text` — that is the actual voice-cloning path. The
  `CustomVoice` and `VoiceDesign` variants instead use predefined speakers or
  text-described voices and do not clone a reference clip, so they aren't a
  fit here.
- If you need to reduce memory further, you can point `TTS_MODEL_ID` at a
  quantized build if/when one is published for the `Base` variant (check
  https://huggingface.co/mlx-community for current `Qwen3-TTS-12Hz-0.6B-Base-*`
  tags) — 8-bit/4-bit quantized weights use noticeably less RAM at a small
  quality cost. This POC defaults to `bf16` since that's what MLX-Audio's own
  documented voice-cloning example uses.
