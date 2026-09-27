# M0 results (2026-09-27)

Run on the real WhatsApp export and 10 real reels. Code in `brain/` (`uv run mindcore import …`, tests: `uv run pytest`).

| Test | Result |
|---|---|
| **WhatsApp import** (`brain/src/mindcore/whatsapp.py`) | ✅ 519 messages, 21 Mar → 27 Sep 2026. 170 unique links → **157 kept, 13 skipped** (astrology lists dropped entirely). Both export formats (iOS-style `[m/d/yy, h:mm:ss AM]` and Android `dd/mm/yyyy, hh:mm am -`) parsed, 4 tests pass. |
| **Learning / work sort** (`triage.py`, rules only) | ✅ learning 90 · work 45 · unsure 22. Own repos (Aman241104, mehtatechteam), Vercel previews, Shopify tasks, Drive, Meet → Work. The unsure 22 (client sites, FRP tank research, Figma files) go to the AI step. |
| **Reel download from the laptop** (yt-dlp, no login) | ✅ **9 / 10** (audio + caption, 2.7 MB total). 1 reel returned "empty media" without login (probably private/removed). |
| **Speech to text** (faster-whisper `small`, RTX 3050) | ✅ **~2–3 s per reel on GPU**, language detected (9 × English, 1 × Spanish). Mishears tech words ("cloud code", "3.js", "hiding" → hiring); fixed with a vocabulary hint + the extraction step. Needed the `nvidia-cublas/cudnn` wheels (handled in code). |
| **Extraction** (`claude -p --model sonnet`, all 9 reels in ONE call) | ✅ 55 s, **15 items** with claims, plus promo/bait flags. |
| **Frames matter** | ⚠️ **4 of 9 reels hide the key name** ("comment X and I'll DM you") → needs_frames. Test: 2 scene-change frames + tesseract OCR on the gym reel revealed **"Exercises Dataset … LogPress"** → `hasaneyldrm/exercises-dataset` ★22k. A name guessed without frames (ExerciseDB) would have been **wrong**, so guesses must stay labelled unconfirmed. |
| **Checking** (GitHub search, most stars + name match) | ✅ Archify → `tt-a1i/archify` ★72k MIT · OmniRoute → `diegosouzapw/OmniRoute` ★70k MIT · Open Executive → `SenteLabsAI/OpenExecutive` ★5.3k · photo→Three.js → `img2threejs/img2threejs` ★17k Apache-2.0. Claims are dated (Archify reel said 43k stars, now 72k). |
| **Share links** | ChatGPT ✅ plain fetch · Gemini ✅ real browser only · Claude ❌ login wall → data-export zip. |
| **On-device Gemma 4 E2B (7a GPU)** | ✅ 448 tok/s read · 11.1 tok/s write · 7.5 s load. |

## Found along the way
- **OmniRoute server is not running** (port 20128 down; its router on 20200 is up, the 6-hourly health check failed at 11:09). No systemd unit starts OmniRoute itself. Not touched.
- WhatsApp export still has **no media** (the `chat.md` suggests a converter dropped it).

## Still open in M0
- Android SDK install + "hello glass" screen on the 7a (needs the phone on USB with debugging on).
- Hindi/Hinglish reel test (none of the 10 sampled reels were Hindi).
- RAM/heat of Gemma during a long batch on the 7a.
