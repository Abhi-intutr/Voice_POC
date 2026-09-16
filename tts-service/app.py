"""
tts-service: a small local HTTP wrapper around MLX-Audio's Qwen3-TTS voice
cloning API, for Apple Silicon Macs.

Endpoints
---------
GET  /health   -> {"status": "ready"|"loading"|"error", "model": "..."}
POST /tts      -> multipart/form-data: reference_audio (file), reference_text,
                   text  => binary WAV response

This process must run natively on macOS/arm64 (MLX uses the Metal GPU via
Apple's Accelerate/Metal frameworks). It will NOT install or run on Linux,
Windows, or Intel Macs.

Voice cloning API used (confirmed against Blaizzy/mlx-audio's Qwen3-TTS
README, see tts-service/README.md for the source link):

    from mlx_audio.tts.utils import load_model
    model = load_model("mlx-community/Qwen3-TTS-12Hz-0.6B-Base-bf16")
    results = list(model.generate(text=..., ref_audio="path.wav", ref_text=...))
    audio = np.array(results[0].audio)   # mx.array -> numpy
    sf.write(out_path, audio, model.sample_rate)
"""

import os
import tempfile
import traceback

from flask import Flask, request, jsonify, send_file, after_this_request

# --- Configuration (override via environment variables) --------------------
# 1.7B is the larger Qwen3-TTS variant for higher quality voice cloning output.
# "Base" is the variant whose generate() method accepts ref_audio/ref_text,
# i.e. the actual voice-cloning path (CustomVoice/VoiceDesign use different
# methods and predefined speakers instead of a reference clip).
MODEL_ID = os.environ.get("TTS_MODEL_ID", "mlx-community/Qwen3-TTS-12Hz-1.7B-Base-bf16")
HOST = os.environ.get("TTS_HOST", "127.0.0.1")
PORT = int(os.environ.get("TTS_PORT", "8000"))
MAX_TEXT_LENGTH = int(os.environ.get("TTS_MAX_TEXT_LENGTH", "2000"))
MAX_CONTENT_LENGTH_BYTES = int(os.environ.get("TTS_MAX_UPLOAD_BYTES", str(25 * 1024 * 1024)))  # 25 MB

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_LENGTH_BYTES

_model = None
_model_error = None


def get_model():
    """Load the MLX Qwen3-TTS model once and cache it in memory.

    Deliberately NOT loaded per-request: loading involves reading model
    weights from disk (or downloading them once from Hugging Face on first
    use) and is far too slow to repeat per call. Only one model is ever
    resident at a time, which matters on an 8 GB machine.
    """
    global _model, _model_error
    if _model is not None:
        return _model
    if _model_error is not None:
        raise RuntimeError(_model_error)

    print(f"[tts-service] Loading model '{MODEL_ID}' ...")
    print("[tts-service] First run downloads weights from Hugging Face "
          "(a few hundred MB to ~1.2 GB depending on precision) - this can "
          "take a few minutes on first start.")
    try:
        from mlx_audio.tts.utils import load_model  # imported lazily so
        # `python app.py --help`-style introspection doesn't require mlx.
        _model = load_model(MODEL_ID)
        print("[tts-service] Model loaded and ready.")
    except Exception as exc:  # noqa: BLE001 - want to surface any load failure
        _model_error = (
            f"Failed to load model '{MODEL_ID}': {exc}. "
            "Check that mlx-audio is installed (see tts-service/README.md) "
            "and that you are running on Apple Silicon macOS."
        )
        traceback.print_exc()
        raise RuntimeError(_model_error)
    return _model


@app.route("/health", methods=["GET"])
def health():
    if _model is not None:
        return jsonify(status="ready", model=MODEL_ID), 200
    if _model_error is not None:
        return jsonify(status="error", model=MODEL_ID, error=_model_error), 503
    return jsonify(status="loading", model=MODEL_ID), 503


@app.route("/tts", methods=["POST"])
def tts():
    # --- validation -----------------------------------------------------
    if "reference_audio" not in request.files or request.files["reference_audio"].filename == "":
        return jsonify(error="reference_audio file is required"), 400

    reference_text = (request.form.get("reference_text") or "").strip()
    text = (request.form.get("text") or "").strip()

    if not reference_text:
        return jsonify(error="reference_text is required"), 400
    if not text:
        return jsonify(error="text is required"), 400
    if len(text) > MAX_TEXT_LENGTH:
        return jsonify(error=f"text exceeds maximum length of {MAX_TEXT_LENGTH} characters"), 400

    ref_file = request.files["reference_audio"]
    allowed_ext = {".wav", ".mp3", ".m4a", ".flac", ".ogg"}
    suffix = os.path.splitext(ref_file.filename or "")[1].lower() or ".wav"
    if suffix not in allowed_ext:
        return jsonify(error=f"unsupported reference_audio format '{suffix}'. "
                              f"Supported: {', '.join(sorted(allowed_ext))}"), 400

    # --- model ------------------------------------------------------------
    try:
        model = get_model()
    except RuntimeError as exc:
        return jsonify(error=str(exc)), 503

    # --- inference ----------------------------------------------------
    tmp_ref_path = None
    tmp_out_path = None
    try:
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            ref_file.save(tmp.name)
            tmp_ref_path = tmp.name

        import numpy as np
        import soundfile as sf

        results = list(model.generate(
            text=text,
            ref_audio=tmp_ref_path,
            ref_text=reference_text,
        ))
        if not results:
            return jsonify(error="model produced no audio output"), 500

        audio = np.array(results[0].audio)

        fd, tmp_out_path = tempfile.mkstemp(suffix=".mp3")
        os.close(fd)
        sf.write(tmp_out_path, audio, model.sample_rate)

        @after_this_request
        def _cleanup(response):
            for p in (tmp_ref_path, tmp_out_path):
                if p and os.path.exists(p):
                    try:
                        os.remove(p)
                    except OSError:
                        pass
            return response

        return send_file(tmp_out_path, mimetype="audio/mpeg",
                          as_attachment=True, download_name="generated.mp3")

    except Exception as exc:  # noqa: BLE001 - convert any failure to a clean 500
        traceback.print_exc()
        if tmp_ref_path and os.path.exists(tmp_ref_path):
            os.remove(tmp_ref_path)
        if tmp_out_path and os.path.exists(tmp_out_path):
            os.remove(tmp_out_path)
        return jsonify(error=f"TTS generation failed: {exc}"), 500


if __name__ == "__main__":
    # Load eagerly at startup: fails fast with a clear message instead of
    # silently stalling the first real request, and means /health is
    # meaningful ("error" vs "loading") from the moment the process starts.
    try:
        get_model()
    except RuntimeError as exc:
        print(f"[tts-service] WARNING: {exc}")
        print("[tts-service] Starting HTTP server anyway - /health will "
              "report the error until this is fixed and the process is restarted.")
    app.run(host=HOST, port=PORT, threaded=False)
