"""Speech to text for downloaded reels with faster-whisper (multilingual, so Hindi/Hinglish works).

GPU (float16) when CUDA and its libraries are available, otherwise CPU int8.
"""

from __future__ import annotations

import time
from dataclasses import dataclass
from functools import cache
from pathlib import Path


# Words Whisper mishears in tech reels ("cloud code", "3.js", "hiding" for hiring).
VOCAB = ("Claude Code, Codex, Cursor, GitHub, repo, open source, MCP, Three.js, React, Next.js, Vercel, "
         "OpenAI, Anthropic, Gemini, LLM, API, hiring, freshers, LPA, CTC, DSA, roadmap, certification.")


@dataclass
class Transcript:
    text: str
    language: str
    language_prob: float
    duration: float
    seconds_taken: float
    device: str


def _preload_cuda_libs() -> None:
    """Load cuBLAS/cuDNN from the nvidia-* wheels so CTranslate2 finds them without LD_LIBRARY_PATH."""
    import ctypes
    import importlib.util

    for pkg, libs in (("nvidia.cublas", ["libcublasLt.so.12", "libcublas.so.12"]), ("nvidia.cudnn", ["libcudnn.so.9"])):
        spec = importlib.util.find_spec(pkg)
        if not spec or not spec.submodule_search_locations:
            continue
        for lib in libs:
            path = Path(next(iter(spec.submodule_search_locations))) / "lib" / lib
            if path.exists():
                ctypes.CDLL(str(path), mode=ctypes.RTLD_GLOBAL)


@cache
def _model(size: str):
    import numpy as np
    from faster_whisper import WhisperModel

    try:
        _preload_cuda_libs()
        model = WhisperModel(size, device="cuda", compute_type="float16")
        # CUDA errors only show up on the first real run, so probe with a second of silence.
        list(model.transcribe(np.zeros(16000, dtype=np.float32), language="en")[0])
        return model, "cuda"
    except Exception:  # no GPU, or CUDA libraries missing
        return WhisperModel(size, device="cpu", compute_type="int8"), "cpu"


def transcribe(audio: Path, size: str = "small") -> Transcript:
    model, device = _model(size)
    start = time.monotonic()
    # vad_filter drops music-only stretches, which is where Whisper tends to invent words.
    segments, info = model.transcribe(str(audio), vad_filter=True, beam_size=5, initial_prompt=VOCAB)
    text = " ".join(s.text.strip() for s in segments).strip()
    return Transcript(text, info.language, round(info.language_probability, 2), round(info.duration, 1),
                      round(time.monotonic() - start, 1), device)
