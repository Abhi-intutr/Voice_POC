import { useCallback, useEffect, useRef, useState } from "react";
import "./App.css";

// Points at the Spring Boot backend. Override at build time with
// VITE_API_BASE_URL if the backend runs somewhere other than localhost:8080.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const SAMPLE_REFERENCE_TEXT =
  "This is a short recording of my voice, used only so the model can learn how I sound.";
const SAMPLE_GENERATION_TEXT =
  "Thanks for trying out the voice cloning proof of concept. If you can hear this in a voice that sounds like the reference recording, it worked.";

const MAX_TEXT_LENGTH = 2000;

function useHealthStatus() {
  const [health, setHealth] = useState({ state: "checking" });

  useEffect(() => {
    let cancelled = false;

    async function check() {
      try {
        const res = await fetch(`${API_BASE_URL}/api/health`);
        const body = await res.json().catch(() => ({}));
        if (cancelled) return;
        if (res.ok) {
          setHealth({ state: "ready", model: body.ttsModel });
        } else if (body.ttsServiceStatus === "loading") {
          setHealth({ state: "loading" });
        } else {
          setHealth({ state: "error", detail: body.detail || body.ttsServiceStatus });
        }
      } catch {
        if (!cancelled) setHealth({ state: "error", detail: "Backend unreachable" });
      }
    }

    check();
    const interval = setInterval(check, 15000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  return health;
}

function StatusPill({ health }) {
  const label = {
    checking: "Checking…",
    ready: "TTS ready",
    loading: "Model loading…",
    error: "TTS unavailable",
  }[health.state];

  return (
    <div className="status" title={health.detail || health.model || ""}>
      <span className={`dot ${health.state}`} />
      {label}
    </div>
  );
}

function ReferenceDropzone({ file, onFile }) {
  const [drag, setDrag] = useState(false);
  const inputRef = useRef(null);

  const handleFiles = useCallback(
    (files) => {
      if (files && files[0]) onFile(files[0]);
    },
    [onFile]
  );

  return (
    <div
      className={`dropzone ${drag ? "drag" : ""}`}
      onClick={() => inputRef.current?.click()}
      onDragOver={(e) => {
        e.preventDefault();
        setDrag(true);
      }}
      onDragLeave={() => setDrag(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDrag(false);
        handleFiles(e.dataTransfer.files);
      }}
      role="button"
      tabIndex={0}
    >
      <input
        ref={inputRef}
        type="file"
        accept=".wav,.mp3,.m4a,.flac,.ogg,audio/*"
        onChange={(e) => handleFiles(e.target.files)}
      />
      {file ? (
        <div className="file-name">{file.name}</div>
      ) : (
        <div className="placeholder">Click to choose, or drag a short audio clip here</div>
      )}
      <div className="sub">WAV, MP3, M4A, FLAC, or OGG · a few seconds is enough</div>
    </div>
  );
}

export default function App() {
  const health = useHealthStatus();

  const [referenceFile, setReferenceFile] = useState(null);
  const [referenceText, setReferenceText] = useState("");
  const [text, setText] = useState("");

  const [status, setStatus] = useState("idle"); // idle | generating | done | error
  const [error, setError] = useState(null);
  const [audioUrl, setAudioUrl] = useState(null);

  const canSubmit =
    referenceFile && referenceText.trim() && text.trim() && status !== "generating";

  async function handleSubmit(e) {
    e.preventDefault();
    if (!canSubmit) return;

    setStatus("generating");
    setError(null);
    setAudioUrl(null);

    const form = new FormData();
    form.append("referenceAudio", referenceFile);
    form.append("referenceText", referenceText.trim());
    form.append("text", text.trim());

    try {
      const res = await fetch(`${API_BASE_URL}/api/voice/generate`, {
        method: "POST",
        body: form,
      });
      const body = await res.json().catch(() => ({}));

      if (!res.ok || !body.success) {
        throw new Error(body.error || `Request failed (HTTP ${res.status})`);
      }

      setAudioUrl(`${API_BASE_URL}${body.audioUrl}`);
      setStatus("done");
    } catch (err) {
      setError(err.message || "Something went wrong generating audio.");
      setStatus("error");
    }
  }

  function fillSampleText() {
    if (!referenceText.trim()) setReferenceText(SAMPLE_REFERENCE_TEXT);
    if (!text.trim()) setText(SAMPLE_GENERATION_TEXT);
  }

  return (
    <div className="page">
      <div className="wrap">
        <div className="header">
          <h1 className="wordmark">
            voice<span>clone</span>
          </h1>
          <StatusPill health={health} />
        </div>
        <p className="tagline">
          Upload a short reference recording, tell it what that recording says, then type
          anything else — it comes back read aloud in that voice.
        </p>

        <form onSubmit={handleSubmit}>
          <div className="panel">
            <h2>Reference recording</h2>
            <p className="hint">A clean few-second clip of the voice to clone.</p>
            <ReferenceDropzone file={referenceFile} onFile={setReferenceFile} />
          </div>

          <div className="panel">
            <h2>What that recording says</h2>
            <p className="hint">
              The exact transcript of the reference clip above, word for word.{" "}
              <button
                type="button"
                onClick={fillSampleText}
                style={{
                  background: "none",
                  border: "none",
                  color: "var(--accent)",
                  cursor: "pointer",
                  padding: 0,
                  font: "inherit",
                }}
              >
                Use sample text
              </button>
            </p>
            <textarea
              rows={3}
              value={referenceText}
              onChange={(e) => setReferenceText(e.target.value.slice(0, 4000))}
              placeholder={SAMPLE_REFERENCE_TEXT}
            />
          </div>

          <div className="panel">
            <h2>Text to speak</h2>
            <p className="hint">Whatever you want generated in the cloned voice.</p>
            <textarea
              rows={5}
              value={text}
              onChange={(e) => setText(e.target.value.slice(0, MAX_TEXT_LENGTH))}
              placeholder={SAMPLE_GENERATION_TEXT}
            />
            <div className="char-count">
              {text.length} / {MAX_TEXT_LENGTH}
            </div>
          </div>

          {status === "error" && error && <div className="error-banner">{error}</div>}

          <button type="submit" className="generate-btn" disabled={!canSubmit}>
            {status === "generating" && <span className="spinner" />}
            {status === "generating" ? "Generating…" : "Generate speech"}
          </button>
        </form>

        {audioUrl && (
          <div className="panel output-panel" style={{ marginTop: 16 }}>
            <h2>Generated audio</h2>
            {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
            <audio controls src={audioUrl} />
            <div className="output-actions">
              <a href={audioUrl} download="generated.mp3">
                Download MP3
              </a>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
