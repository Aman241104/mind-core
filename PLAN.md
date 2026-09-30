# mind-core: plan

A personal Android app. Share a reel, post, link or screenshot to it, and AI works out what's worth keeping (repos, tools, certifications, courses, jobs, tips), checks each one online, and files it. You can then ask questions about everything you've saved.

Status: planning (2026-09-27). No code yet.

---

## 1. Goals

**Must do**
- Saving takes one tap from anywhere: the Share menu, or a screenshot.
- Import the backlog already sitting in the WhatsApp chat with yourself.
- Pull out real *items*, not just a transcript: "this reel mentions repo X, cert Y and tool Z".
- **Check** every item. Reels are often wrong (in `~/report.md`: Edge0 only runs on Apple Silicon, one repo was a 404, a license was listed wrong).
- Ask the AI about your library, with sources, and let it research further online.
- Polish on the level of an iOS app, with a glass look.
- Free to run by default.

**Won't do (for now)**
- Share with other people or collaborate. Only you use it.
- Publish on the Play Store (you install the APK yourself).
- Download or re-host other people's videos to watch later. The app only keeps the text and the extracted data.

---

## 2. Ways content gets in

| Channel | How | Notes |
|---|---|---|
| **Share menu** | Instagram / YouTube / X / LinkedIn / Chrome → Share → mind-core | Main path. Instagram only shares the URL, not the video. |
| **Share an image** | Gallery or screenshot preview → Share → mind-core | Screenshots are sent to the AI to read. |
| **WhatsApp backlog** | Export the chat with yourself → Share → mind-core | See §3. Clears out everything you've piled up. |
| **Watch the screenshots folder** | Optional: the app spots new screenshots and asks "mind-core this?" | Needs permission to read media. Can be turned off. |
| **Clipboard check** | When you open the app, it offers to save a link you just copied | Android 13+ shows a notice when an app reads the clipboard. |
| **Paste / type** | Box for a URL or a note | Fallback. |
| **Quick capture** (stands in for the Nothing "Essential Key") | Quick Settings tile + home-screen widget + a shortcut from long-pressing the app icon, all opening one capture sheet | Pixel 7a: possibly also Quick Tap (double-tap the back of the phone) → open mind-core. **Check on your phone** that Quick Tap can open an app on your Android version. |
| **Voice note** | Hold the capture button → speak ("remind me to try that Kafka course") | Speech → text → treated like any other save. Idea taken from Essential Space. |

---

## 3. Getting your WhatsApp chat in

WhatsApp has no official way for an app to read a personal chat. The options:

| Option | Verdict |
|---|---|
| **Export chat** (built into WhatsApp) | ✅ **Use this.** Official, safe, one tap. |
| Unofficial WhatsApp Web libraries (Baileys, whatsapp-web.js) | ❌ Against WhatsApp's rules. Numbers get banned unpredictably, often within weeks. In 2026 one "anti-ban" package was caught stealing sessions and messages. Not worth risking your main number. |
| WhatsApp Business Cloud API | ❌ Only works for business numbers. Can't read personal chats. |
| Decrypting the local backup (`msgstore.db.crypt15`) | ❌ Needs root or the 64-digit backup key. Fragile. |
| Reading notifications | ❌ Messages you send to yourself don't create notifications. |

### How the export works
1. WhatsApp → open the chat with yourself → ⋮ → More → **Export chat**.
2. Pick **Include media** if you want the screenshots. The limit is the most recent **10,000 messages** with media, or **40,000** without.
3. WhatsApp opens the Share menu → pick **mind-core**. The app receives a `.zip` (or a `.txt` plus media files).
4. The app reads the text, finds every URL and image, and puts them into the normal processing pipeline.

### Reading the file
- Line format depends on your phone's region and time settings, for example `27/09/2026, 10:15 pm - You: https://www.instagram.com/reel/...`. The reader has to handle 12h and 24h time, both date orders, and messages that run over several lines.
- Images show up as `IMG-20260927-WA0001.jpg (file attached)`. The app matches them to the files in the zip.
- Any notes you typed next to a link (e.g. "check this for DSA") are kept as **your note** on that item and used as extra context.
- **No duplicates:** each link is normalized (tracking parameters like `?igsh=` removed) and fingerprinted, so exporting again later only adds new messages.
- Dry run first: "Found 312 links, 48 screenshots, 27 already saved → Import 333?"

### What your real export contains (`Aman.zip`, checked 2026-09-27)
- `chat.txt` + `chat.md`, 1,905 lines, 21 Mar → 27 Sep 2026. Line format: `[3/21/26, 11:13:43 AM] You: …` (month/day/year, 12h time, with seconds). The `.md` version was made by a converter tool.
- **185 links, 170 unique:** 62 reels + 24 posts from Instagram, 7 GitHub (5 of them are your own/mehtatechteam repos), plus Figma, Drive, Vercel previews and client sites.
- **Exported without media:** 132 × `<image omitted>`, 165 other media omitted. Screenshots need a second export **with media**, or just share them from the gallery.
- **This chat is also a work notepad:** client site lists, FRP/composite company links, Vercel preview links, Meet links. So a first step, **"Learning or work?"**, is needed: learning saves go through the full pipeline; work links go to a quiet "Work" shelf (no checking, never mixed into For You). Your own repos are detected and labeled "mine".
- Forwarded messages start with `[Forwarded]`. Notes like "Hindu Addword Site List" come before a list of links and become that list's title.

### Privacy
- Only the chat you export is ever sent. The app never touches other chats.
- The raw export is read and then thrown away. Only the extracted links, notes and images are kept, in your own Cloudflare account.

---

## 4. What happens to each save

```
save → saved instantly (optimistic UI) → background job:
  1. identify what it is (reel / short / tweet / post / github / article / image)
  2. get the content
  3. extract items (AI, with a fixed output format)
  4. check each item online (§6)
  5. merge with items already saved + index for search
  6. notify: "Found 3 repos, 1 cert ✓"
```

| Type | How the content is fetched |
|---|---|
| Instagram reel/post | `yt-dlp` gets the audio and caption → Whisper turns speech into text. Also grab a few frames, because reels often show the repo name only on screen. |
| YouTube / Shorts | The video's existing transcript first, Whisper if there isn't one. |
| X / Twitter | The post text and any linked URLs. |
| LinkedIn | Usually behind a login. Use whatever preview text is public; otherwise ask you to share a screenshot. |
| GitHub link | Straight to checking. |
| Article | Pull the readable text from the page. |
| Screenshot | An AI that can read images extracts the text and the items. |

**Download on the phone itself** (works without the laptop):
- Built into the app: **youtubedl-android** (runs real `yt-dlp` inside an Android app; GPL-3.0, fine for a personal app you don't distribute). It's the same engine as **Seal** (29k★, actively maintained). yt-dlp updates itself in the app, so Instagram changes get fixed without rebuilding.
- Only the **audio + 3–4 frames** are downloaded (small, fast on mobile data), then uploaded to R2 → Workers AI Whisper turns it into text (free allowance).
- **Offline option:** **whisper.cpp** (MIT, 54k★) on the phone with a small model. Slower and uses battery, so it's only used when there's no network or you choose it.
- Order: **phone → laptop → caption only**. Your phone uses a mobile/home IP, so Instagram is less likely to block it than a cloud server.
- No-code option: install Seal, share the reel to Seal, share the downloaded file to mind-core. Works on day one, before the built-in downloader is ready.

**Main risk:** Instagram often blocks downloads that come from cloud servers. Plan B is to run that one step on the laptop (already reachable through your ttyd/Caddy setup). Plan C is the caption plus sampled frames only. **Milestone 0 tests this first.**

---

## 5. What gets extracted

```ts
Item {
  kind: 'repo' | 'tool' | 'cert' | 'course' | 'job' | 'tip' | 'person' | 'other'
  title, summary                 // one line + 3 bullets
  url?                           // canonical link, resolved
  claims: string[]               // what the reel *said* ("free", "35B on 4GB RAM")
  verification: {...}            // §6
  sources: Save[]                // every reel/screenshot that mentioned it
  tags: string[]                 // automatic + your own
  status: 'new' | 'want' | 'trying' | 'done' | 'skip'
  your_note?
}
```

The same repo mentioned in 3 different reels becomes **one item with 3 sources**, not 3 cards.

---

## 6. Checking each item (the key feature)

| Kind | What gets checked |
|---|---|
| **Repo** | GitHub API: stars, license, last push, archived?, open issues. **Is this the original repo or a copy/fork?** (the report found dozens of copies). Platform needs vs. *your* hardware (Android + Arch laptop with an RTX 3050 4GB). |
| **Tool / site** | Does the site load? Pricing (free / freemium / paid). Is it a paid ad disguised as advice? |
| **Cert / course** | Official provider page, cost, format, how long it takes, whether it expires. Free or paid. Rough standing. |
| **Job** | Is the posting still open? Official careers page. Salary figures marked "unverified" unless a source says so. |
| **Claims** | Each claim from the reel gets ✓ confirmed / ✗ wrong / ? unclear, with the source link. |

Result: a **trust badge** (Verified · Mixed · Hype · Dead) and a "why" sheet with the sources. Facts get re-checked weekly (stars, whether a job is still open).

---

## 7. Library and organization

- Automatic sections: Repos, Tools, Certs, Courses, Jobs, Tips.
- Automatic collections by topic (e.g. "Local LLMs", "UI/Design", "Interview prep"), which you can rename.
- Search by **meaning**, not just keywords ("that thing for running big models on low RAM").
- Filters: kind, status, trust badge, free only, date, where it came from.
- Status flow: new → want → trying → done / skip. Swipe gestures on cards.
- Undo on every destructive action. Deleted items go to a trash that empties after 30 days.

---

## 8. Ask (the AI)

- Chat about your library with cited sources (tap a source → opens the item or reel).
- **Research mode:** "Is Karakeep better than X?" → searches online, compares, and can save what it finds as new items.
- Quick prompts:
  - "What did I save this week?"
  - "Make a learning plan from my saved certs"
  - "Which repos can actually run on my laptop?"
  - "Anything I saved that's now dead or paid?"
- Ask about a single item from its detail page.

### 8a. Which AI does the work (no paid API by default)

Three tiers, picked automatically per task:

| Tier | Where it runs | Used for | Cost |
|---|---|---|---|
| **1. Cloud (always on)** | Cloudflare Workers AI + free models through OmniRoute | Embeddings, speech to text (Whisper), sorting links into learning/work, simple extraction | Free daily allowance |
| **2. Laptop brain (your subscriptions)** | On the laptop: `claude -p` (Claude Code on your Claude plan), `codex exec` (Codex CLI on your ChatGPT plan), and OmniRoute's `free-stack` (Claude first, then Gemini Pro and others) | **Deep research**, checking claims, comparing tools, learning plans, hard extraction | $0 extra, uses your plan limits |
| **3. Phone hand-off** | The Claude / ChatGPT apps on your 7a | "Open this in Claude" when you want to read the research yourself | Free |

- **Laptop brain:** the laptop picks up jobs from the Cloudflare queue. If the laptop is off, research jobs **wait** and show "waiting for laptop" on the phone. They don't fail and don't fall back to something worse without telling you. Everything else (saving, search, Ask about your library) keeps working in the cloud.
- `claude -p` and `codex exec` are the official command-line tools running on your own plans, which fits personal use. Keep it personal and modest in volume; plan rate limits apply. Don't pass subscription logins through third-party proxies for heavy automated use. That's the grey area that gets accounts limited.
- **Phone hand-off:** an **"Ask Claude" / "Ask ChatGPT"** button sends a ready-made prompt (the item, its sources, what to check) to that app through Android's share menu. You read the answer there. To keep it, share the answer back to mind-core and it gets attached to the item. **Automatic replies back from those apps aren't possible without an API.** The only ways would be screen-scraping (accessibility automation) or reusing the web login, which are fragile and against their terms. Not doing that.

### 8a-1. On-device AI on the Pixel 7a (researched 2026-09-27)

Your 7a has a Tensor G2 and 8 GB of RAM. **Gemini Nano / AICore doesn't support the 7a** (ML Kit GenAI lists Pixel 9 and up), so on-device AI means bundling our own models. Candidates from Hugging Face:

| Job | Main pick | Fallback | Notes |
|---|---|---|---|
| **Speech to text** | **Moonshine** (`moonshine-ai/moonshine-base` / `litert-community/moonshine-tiny`, MIT, ~100 MB) | **whisper.cpp** (MIT) with `base`/`small`; `litert-community/whisper-tiny` i8 is 41 MB | Moonshine is English-only, fast and short-clip friendly. **Hindi/Hinglish reels need Whisper** (multilingual), so language detection picks which one. |
| **Understanding + extraction + reading screenshots** | **Gemma 4 E2B** (`litert-community/gemma-4-E2B-it-litert-lm`, Apache-2.0) | Qwen3-1.7B int4 (`litert-community/Qwen3-1.7B`, 977 MB, text only) | Gemma 4 E2B takes **text, images and audio** in one model: 2.0 GB (GPU build) / 2.6 GB (CPU build), and it loads the vision/audio parts only when needed. Google only publishes benchmarks for flagships (S26 Ultra GPU ~50 tok/s); **7a speed is unknown → measure in M0.** |
| **Text in screenshots (fast first pass)** | **ML Kit Text Recognition v2** (Google, on-device, free, small) | Gemma 4 E2B vision | Reads the text in milliseconds; Gemma is only used when the meaning matters (e.g. "which of these 5 repos is the point of this screenshot?"). |
| **Embeddings (meaning search)** | Kept **in the cloud** (Workers AI), so the phone and the cloud index use the same model | Later: `onnx-community/embeddinggemma-300m-ONNX` for offline search | The on-device EmbeddingGemma files on HF are built for other chips' NPUs (Tensor G5/G6, Snapdragon); none for G2. |

**How it's used**
- **Settings → Phone AI:** Off / **Smart** (default) / Max.
  - Smart: speech to text + screenshot text on the phone (private, free, works offline). Extraction on the phone only when you're offline; otherwise it goes to the cloud or laptop, which are more accurate.
  - Max: everything the phone can do, plus **offline Ask** over your locally cached items.
- **Research always needs the internet.** The phone model can't browse, so research stays Laptop-Claude / Free-Gemini (see the switch below).
- Models download **once, over Wi-Fi, when first needed** (~2.3 GB for Gemma + Moonshine). They can be deleted in Settings.
- Runtimes: **LiteRT-LM** (Kotlin API, the same stack as Google's AI Edge Gallery app) for Gemma/Moonshine, whisper.cpp through a small native bridge, ML Kit for text recognition.
- Guardrails: never run while the battery is under 20% or the phone is hot (checks Android's thermal status). Long work only runs when charging or when you ask.
- **Measured on your 7a (AI Edge Gallery 1.0.19, Gemma-4-E2B-it, GPU, 256 prefill/256 decode, avg of 3 runs, 2026-09-27):** reading **448 tok/s** · writing **11.1 tok/s** · first token **0.67 s** · load **7.5 s** each time (86 s the very first time, one-off).
  - **What that means:** reading a whole reel transcript (~1,000 tokens) takes about 2 s. Writing is the slow part: a full item card (~250 tokens) takes ~23 s.
  - **Design decisions from this:**
    1. The phone model writes a **compact output** (short JSON: kind + name + url + 1 line, ~60 tokens ≈ 5–6 s); the cloud/laptop fills in details later.
    2. **Batches, not one-offs:** load once (7.5 s), process the whole queue, then unload to free RAM. Big batches (e.g. the WhatsApp backlog) run **while charging**.
    3. **Offline Ask** streams at ~11 tok/s (≈ 8 words/s, faster than reading speed), so it feels fine. It shows a "warming up" state for the 7.5 s load.
    4. When online, extraction stays in the cloud/laptop by default (faster + more accurate). **Smart** mode is still the default.
- **Quick test with no code:** install **Google AI Edge Gallery** (Play Store), load Gemma 4 E2B, and try "list the repos in this screenshot". That shows real 7a speed before I build anything.

### 8a-2. Research switch (in Settings and on every research request)

| Mode | What it does |
|---|---|
| **Auto** (default) | Laptop online → **Claude** (`claude -p` on your plan). Laptop off → free cloud research. |
| **Claude** | Always use the laptop. Waits in the queue if the laptop is off. |
| **Free** | **Gemini Flash with Google Search grounding** (free tier, about 500 grounded requests/day on 2.5 Flash, limits to recheck) + Tavily free credits for web search. Runs from Cloudflare, no laptop needed. |
| **Hand-off** | Sends a ready-made prompt to the Claude / ChatGPT / Gemini / Perplexity app on your phone. |

**Laptop engines (you pick the default):** `claude -p` (Claude plan) · `gemini` CLI (**Google AI Pro**: switch the CLI from API-key login to *Login with Google* to use your Pro quota, and it has built-in Google Search) · `codex exec` (ChatGPT plan). Auto can alternate between them when one hits its limit.

The laptop sends a heartbeat to Cloudflare every minute, so the phone always shows a live "🟢 Laptop online / ⚪ offline" dot next to the switch.

### 8a-3. Import chats from Claude / ChatGPT / Gemini by link

Share a chat's **public share link** to mind-core (or paste it) → the whole conversation is fetched, split into chunks, indexed, and the useful items (repos, tools, certs…) are extracted like from a reel. No copying answers one by one.

**Tested 2026-09-27 with your real links:**

| Link | Result | How mind-core gets it |
|---|---|---|
| ChatGPT `chatgpt.com/share/…` | ✅ **Plain request works.** The whole chat ("Interview Preparation Plan") is inside the page; decoded 120 long answer blocks. | Cloudflare Worker, no browser needed |
| Gemini `share.gemini.google/…` | ⚠️ Plain request only returns an empty shell (the chat loads through JavaScript). ✅ **A real browser works** (the n8n hosting chat: 2 questions + 2 answers read). | Hidden in-app WebView on the phone, or the laptop's browser |
| Claude `claude.ai/share/…` | ❌ Plain request → Cloudflare bot check. ❌ A real browser without a login → **redirected to the sign-in page**. | See below |

**Claude options (no scraping tricks):**
1. **Best: Claude data export.** claude.ai → Settings → Privacy → Export data → a zip with *all* your chats as JSON, by email. mind-core imports the zip (like the WhatsApp import). Redo it whenever you want a refresh.
2. **Single chats:** in the Claude app, copy/share the answer text → share to mind-core.
3. **In-app WebView signed in as you:** you log into claude.ai once inside mind-core's browser view and it reads share links like you would. It works, but it keeps a Claude login inside the app, so it's **opt-in only**.

- Nothing sneaks past the bot check (no "stealth" tricks, no borrowed login cookies). If a page won't load normally, mind-core asks you to open it once.
- A link can only be shared once you've pressed **Share** in that app. Private chats can't be fetched, by design.
- **Bulk option:** ChatGPT and Claude both offer a full **data export** (a zip by email) in Settings. mind-core can import that zip for your *entire* history in one go (the same idea as the WhatsApp import).
- To test in M0: one real share link from each app.

### 8b. RAG: how Ask stays accurate

1. **Everything gets indexed:** each item, transcript chunk, OCR text and note → embedding (Workers AI) → **Vectorize**, with extra fields (kind, shelf learning/work, status, trust, date, source app).
2. **Hybrid search:** meaning search (Vectorize) **plus** exact keyword search (D1 full-text / FTS5), so exact names like "Karakeep" or "bge-m3" never get missed. The results are merged (reciprocal rank fusion).
3. **Filters before search:** "certs I haven't done", "only verified", "last month", "exclude Work shelf". Filters narrow the pool first, so the AI never sees content that doesn't belong in the answer.
4. **Rerank:** a reranker model (Workers AI) keeps only the ~8 best chunks, so less noise goes to the model.
5. **Grounded answer:** the model may only use those chunks and must cite `[1] [2]`. If the answer isn't in your library, it says so and offers **"Research online?"** (→ laptop brain) instead of guessing.
6. **Relation matching:** a small relations table in D1 connects items: *same repo*, *alternative to*, *needs*, *part of topic*, *mentioned together*. Built during extraction and used to show "Related" on every item and to expand search (if you ask about X, alternatives to X come too).
7. **Hallucination checks:** citations are checked mechanically (the cited chunk must contain the claim's key words/numbers). Answers that fail get marked "unverified" instead of being shown as fact.

---

## 8c. Integrations: Obsidian · NotebookLM · Perplexity (researched 2026-09-27)

### Obsidian (two-way, via the laptop brain) ⭐ best fit
Your vault (`~/Obsidian Vault`) already has **Dataview, Tasks, Spaced Repetition, Smart Connections, remotely-save**, and a `20 - Courses` folder.
- **mind-core → Obsidian:** every learning item becomes a note in `40 - mind-core/<Kind>/`, with frontmatter (`kind, status, trust, tags, source, saved`) and the summary, the claimed-vs-true table and source links. Courses/certs also link into `20 - Courses`.
  - **Dataview:** ready-made dashboard note ("repos I want to try", "certs by cost").
  - **Tasks:** "want" items become `- [ ] Try <repo>` tasks with due dates if you set a reminder.
  - **Spaced Repetition:** optional `#flashcards` generated from tips/concepts, so saved reels actually get remembered.
- **Obsidian → mind-core:** change `status: done` in a note → the laptop brain reads it back and updates the app. Your own writing under a `## My notes` heading is **never overwritten**; mind-core only owns the frontmatter and the top block.
- **Your notes in Ask:** optionally index the vault too, so "what do I know about MLOps?" answers from your reels **and** your notes.
- **On the phone:** notes reach Obsidian mobile through your existing **remotely-save** sync. There's also a "Send to Obsidian" button using the `obsidian://new` link for a single note straight away.

### NotebookLM (via Google Drive, no unofficial API)
- **No public NotebookLM API for normal accounts.** The Enterprise API needs Google Cloud Gemini Enterprise. The popular `notebooklm-py` works by reusing your **browser login cookies**, which is the same kind of workaround we ruled out for WhatsApp/Claude. Not using it.
- **Clean route:** mind-core keeps one **Google Doc per collection** in your Drive ("mind-core · Local LLMs", "mind-core · Certs"…), updated automatically. Add those Docs to a NotebookLM notebook **once** as sources. NotebookLM can re-sync Drive Docs (check in M-phase that the sync button still behaves this way).
  → You get NotebookLM's **Audio Overview (podcast of your saves), mind maps, quizzes and study guides** built on everything you've saved.
- **Quick hand-off:** "Open in NotebookLM" shares an item's link/text to the NotebookLM app as a new source (check that the Android app accepts shares).

### Perplexity
- **The API isn't free:** Sonar is ~$1 / 1M tokens + per-request fees, Sonar Pro $3/$15. A Pro subscription doesn't reliably include API credit in 2026 (reports conflict). **Off by default** (no-cost rule). It can be added as a 5th research-switch mode if you ever add a key.
- **Free ways:**
  - **Hand-off:** "Ask Perplexity" sends a ready-made prompt to the Perplexity app (like Claude/ChatGPT).
  - **Import threads by link:** share a Perplexity thread link → fetched with the in-app WebView (same method as Claude share links) → answer + **its cited sources** become items.
- The free **Gemini + Google Search** mode already does Perplexity-style cited web answers.

---

## 9. Extras (later)

- **Weekly digest** notification: "5 new saves, 2 turned out to be hype, 1 job closes Friday."
- **Send to laptop:** "set this repo up" → hands off to Claude Code on the laptop (remote-control).
- Reminders ("try this repo this weekend").
- Home-screen widget: the latest items, and a quick-save button.
- Share an item back out as a neat card image.

---

## 10. Design

Three references, each with a clear job:

| Reference | What we take from it |
|---|---|
| **[Convx](https://github.com/cosmictaserdev-creator/Convx)** (the glass look) | Its glass surfaces actually see and bend the content behind them. It uses **[Kyant0/backdrop](https://github.com/Kyant0/AndroidLiquidGlass)** (Apache-2.0, ~3.9k★, built for Android's native UI toolkit). From Convx: a floating glass tab bar with a springy "puck" that slides between tabs, a separate round glass button beside it (search in Convx → **capture** in mind-core), frosted mini-bars floating above the tabs, glass segmented controls, bouncy overscroll, pages blurring as they transition, rounded tiles on a dark background. |
| **Nothing Essential Space** (usability) | Its two-tab structure: **For You** (what matters now: reminders, deadlines, what just got processed) and **Library** (everything else). Capture in one tap, then the AI files it. Very few buttons, lots of empty space, monochrome with one accent. Automatic dates/reminders pulled from saves. Editable AI summaries. |
| **Material 3 Expressive** (Pixel 7a) | Material You colors from your wallpaper, Expressive shape-changing loaders and buttons, predictive back gesture, Android's own share sheet / widgets / Quick Settings tile, big bold headline type. |

**Rule to avoid a mess:** glass is only for the floating layer (tab bar, capture button, top bar, sheets, the "processing" pill). Content (cards, text, detail pages) is solid Material 3 surfaces tinted with your wallpaper colors, so everything stays readable. This is the main fix for why the old web glass attempt felt wrong.

- **Motion:** spring physics everywhere. Cards expand into their detail page (shared-element transitions). Tab puck you can drag. Lists appear with a slight stagger. Expressive loading shapes while processing.
- **Haptics:** a light tick on selection, a success buzz when processing finishes, a stronger bump on swipe actions.
- **Type:** big bold headlines in the Essential Space / Convx style ("Library", "For You"), clean text for everything else. Dark first, light supported, and both follow your wallpaper colors.
- **States:** skeleton placeholders, a live processing card, and helpful empty and error screens.
- **Performance:** glass rendered at reduced resolution (Convx does this), a settings toggle to reduce glass, and a target of 90 fps on the 7a's screen. Tested on your actual phone at M2, not at the end.
- **Glass needs Android 13+.** Your 7a is fine. Older phones get a plain blur.

### Screens
1. **For You:** processing now, reminders and deadlines (e.g. a job closing Friday), newest verified finds, and "you saved this 3 weeks ago, still want it?"
2. **Library:** segmented control (All · Repos · Tools · Certs · Courses · Jobs), grid or list toggle, sort, and rounded tiles like Convx.
3. **Item detail:** summary (editable), trust badge, claimed vs. true, sources, actions (open, status, remind, send to laptop).
4. **Ask:** chat, opened from its own tab or from any item.
5. **Capture sheet:** paste, photo/screenshot, voice, recent clipboard link.
6. **Import:** the WhatsApp backlog import with its dry run.
7. **Settings:** AI models, laptop backup for downloads, glass strength, export, privacy.

Bottom bar (Convx-style): glass pill with **For You · Library · Ask**, plus a separate round glass **＋ capture** button.

---

## 11. Tech stack (all free tiers, limits to confirm when building)

| Part | Choice |
|---|---|
| App | **Native Android: Kotlin + Jetpack Compose** (changed from Expo; see below) |
| Glass | **Kyant0/backdrop** (Apache-2.0), the same engine Convx uses |
| Components | Compose Material 3 (Expressive), dynamic color, predictive back |
| Share menu / quick capture | Native Android share target, Quick Settings tile, home-screen widget (Glance), app shortcuts |
| Local data | Room (offline cache) + WorkManager (background uploads and retries) |
| Build | Gradle on this Arch laptop → `adb install` straight to your 7a (`adb` is already installed; Android SDK still needed) |
| API | **Cloudflare Workers** (free: 100k requests/day) |
| Database | **D1** (SQLite, free 5 GB): items, sources, relations, jobs, full-text search |
| Files (screenshots) | ~~R2~~ (needs a card) → **Cloudinary Free** (your existing account: 25 credits/month ≈ 25 GB storage+bandwidth, 10 MB max per image). The phone uploads straight to Cloudinary using a signature from the Worker (the API secret never leaves Cloudflare), into `mind-core/`. The laptop reads the image by URL for OCR. Cloudinary thumbnails (`w_400,f_auto,q_auto`) keep the app fast. |
| Meaning search | **Vectorize** (free tier) |
| Jobs | **Queues** (free: 10k operations/day, 24h retention) |
| Cloud AI | **Workers AI**: embeddings, reranker, Whisper (free daily allowance) |
| Login | Just you: Cloudflare Access or a device token. No full account system |
| Reel downloads | **Laptop worker** (`yt-dlp` + ffmpeg). Cloudflare Workers can't run `yt-dlp`, and Instagram tends to block cloud IPs anyway |
| Laptop brain | Small daemon (systemd --user): pulls jobs → `yt-dlp`, `claude -p`, `codex exec`, OmniRoute → posts results back |
| Checking | GitHub REST API, web search (from the laptop brain), fetching pages |

**Why native instead of Expo:** Convx's glass comes from a Jetpack Compose library (Kyant0/backdrop). React Native can't use it directly, so matching that look means building natively. Native Compose is also where Material 3 Expressive, widgets, the Quick Settings tile and the share target work best. The trade-off (Android only) doesn't matter since you're on a Pixel.
**License note:** Convx is GPL-3.0, so we don't copy its code. We use Kyant0/backdrop directly (Apache-2.0) and only take design ideas from Convx.

---

## 12. Risks

| Risk | Plan |
|---|---|
| Instagram blocks downloads | Downloads already happen from the laptop (home IP). Fallback: caption + frames. Tested in M0. |
| Laptop is off | Saving, search and Ask still work in the cloud. Downloads and research wait in the queue (24h on the free tier, so the laptop re-checks D1 for anything left over). |
| Plan rate limits (Claude / ChatGPT) | Research runs one job at a time, shows remaining work, and falls back to OmniRoute's free models only when you allow it. |
| Free-tier limits | Queue jobs + back off and retry. Show "waiting" in the UI instead of failing. |
| AI makes things up | Fixed output format + the checking step + always show sources. Anything unchecked is labeled that way. |
| Glass is slow on your phone | Settings toggle to cut the effect back to plain blur. |
| WhatsApp export format changes | Tests for the file reader using your real export. |

---

## 13. Milestones

- **M0: prove the risky part.** ✅ Gemma 4 E2B speed measured (11 tok/s writing). Still to do on the 7a: RAM/heat during a long batch, Moonshine and Whisper on real Hindi+English reels.
   Use the real `Aman.zip`: build the export reader + the learning-or-work step, then take 10 of the 62 reels → download + transcribe from a cloud server *and* from the laptop. Pick the one that works. Also install the Android SDK and get a "hello glass" test screen running on your 7a.
- **M1: backend.** Database schema, worker, extraction and checking. Run it on the whole WhatsApp backlog and check the results against `~/report.md`.
- **M2: app basics.** Share menu, Inbox, Library, Item detail, WhatsApp import.
- **M3: Ask.** Chat about your library + research mode.
- **M4: polish.** Glass system, motion, haptics, empty/error states, tuning on your phone.
- **M5: extras.** Digest, Obsidian export, widget, send to laptop.

Each milestone ends with a real test on your phone with real data. Nothing counts as done just because it compiles.

---

## 14. Open questions

Decided 2026-09-27: name **mind-core** · re-export **with media** · **Work shelf** yes · backend **Cloudflare** · AI = cloud free tier + laptop brain (subscriptions) + phone hand-off.

Decided: laptop brain runs as a boot service · research switch Auto/Claude/Free/Hand-off · download on the phone first · import chats by share link.

1. Send one real share link each from Claude, ChatGPT and Gemini (any harmless chat) for the M0 test.
2. The WhatsApp re-export **with media** → `~/stash/data/`.

---

## 15. Widgets (researched 2026-09-27)

What similar apps ship: Todoist has 4 widgets (quick add, task list, productivity stats, and a voice widget that opens in listening mode); Nothing Essential Space puts upcoming reminders and events first; Readwise is built around a daily review; Google Keep has a note-list widget.
Implementation guidance (Android Glance docs + 2026 write-ups): Jetpack Glance; widgets read a cached snapshot, never the network; push `updateAll()` when data changes; WorkManager only for occasional background refresh (~30 min floor); few items, responsive sizes, theme-aware colors.

| Widget | Pattern | Content |
|---|---|---|
| Quick capture | Todoist quick add + voice | ＋ Save · 🎙 Voice (opens recording) · 📋 Paste |
| Coming up | Essential Space reminders | next deadlines, days left, overdue in red |
| Fresh finds | Keep list + Todoist stats | newest items + "laptop online · N processing" |
| Daily pick | Readwise daily review | one item per day to try, Open / Done / Skip |

Data flow: app refresh / capture / 1-hourly worker → `WidgetSnapshot` (JSON in app storage) → `updateAll()` → widgets render from the snapshot.

---

## 16. v2: the ideas + notes app (researched 2026-09-27)

**Direction:** mind-core becomes the place for *all* thinking, not only saved reels: fast capture (Keep), AI that
organizes and links by itself (Mem, Tana), a thinking partner (Reflect), visual brainstorming (Heptabase, Miro),
and resurfacing so ideas don't die (Readwise). Everything built so far stays: reel/screenshot/PDF/video capture,
extraction + fact-checking, Ask + research, calendar + deadlines, widgets, edge drawer, voice, glass.

Research: the category split into lanes (canvas-first, database-first, text-stream, daily-notes graph, local-first,
structured PKM); nobody combines fast capture + auto-linking + brainstorming + resurfacing on the phone. The 2026
shift is from manual linking to AI-surfaced connections; GraphRAG (retrieval that follows links) beats plain vector RAG
for "how do my ideas connect" questions.

### New things you can make
| Thing | What it is |
|---|---|
| **Note** | Your own writing: headings, checklists, bullets, quotes, code, images, voice (transcribed). `[[links]]` to notes and saved items; backlinks shown automatically. |
| **Idea** | A one-line spark, captured in 2 seconds (widget, edge drawer, voice). Grows: spark → growing → ready → done/parked. |
| **Board** | A brainstorm space: a topic in the middle, ideas/notes/items around it, AI-suggested angles you accept or dismiss. |
| (existing) **Item** | Repos, tools, courses, jobs, videos, books pulled out of what you saved. |

### AI that does the boring parts
- **Auto-connections:** every note/idea gets "Connected to" chips (meaning similarity + shared entities), plus a graph view.
- **Brainstorm engine:** pick a topic → angles from your own library (RAG) + web research + methods (How might we,
  SCAMPER, first principles, "what would make this 10×"), shown as a radial mind map; keep what's good.
- **Idea incubation:** old ideas resurface on a schedule with "anything new?"; new saves that relate to a parked idea ping it.
- **Clean-up:** voice rambles → tidy note; notes → checklist tasks with dates (into the calendar); note → flashcards.
- **Ask everything:** notes + ideas + items, GraphRAG-lite (hybrid search, then follow links one hop), streaming
  answers so they start in ~1 s, and a faster model for list questions (the list answers took 20–35 s in testing).

### Design system v2 ("Lotus Studio")
From the two references: huge bold headlines, pill tabs with counts, circular icon buttons, big-radius hero cards in
one strong accent, soft organic card shapes, a floating capsule nav with a prominent center + and mic.
- Type: a characterful display face (Bricolage Grotesque) + a clean text face (Figtree), Google downloadable fonts.
- Light theme (warm paper, pastel cards) and dark theme (near-black, one lotus accent), both first-class.
- Glass stays for floating chrome; content cards are solid and calm.

### Swan, the Lotus blob (our own mascot, not a copy of Bloub/x.ai's avatar)
One soft liquid blob in the lotus gradient with eyes, drawn in Compose (no images, no animation library), morphing
between states that *mean* something:
| State | When |
|---|---|
| idle (breathing, blinking) | calm screens, empty states |
| listening (pulses with your voice) | recording a voice note |
| thinking (three bouncing dots) | Ask / brainstorm working |
| orbit (dots circling) | laptop processing saves |
| searching (eyes scanning, comet) | research online |
| burst (happy squish + sparks) | saved, done |
| alert (!) | deadline soon, something failed |
| notification (badge dot) | new finds / resurfaced idea |
| sleep (closed eyes, zzz) | laptop offline, night |
| wink | tap it |

### Phases
- **P1:** design system v2 (fonts, light+dark tokens, components) + the Lotus blob in loading/empty/Ask/voice/status/save.
- **P2:** Notes + Ideas (backend tables, editor, inbox), home redesigned as "Today" (ideas, tasks, deadlines, finds).
- **P3:** Brainstorm boards + auto-connections + graph view + streaming Ask with GraphRAG-lite.
- **P4:** resurfacing/incubation, tasks from notes into the calendar, flashcards, Obsidian two-way sync.

### Status (2026-09-28)
All four phases are built and deployed; app 0.8.0 published (0.7.0 installed on the phone at last check).
- **P1** Lotus Studio + Swan. **P2** Notes/Ideas tab, editor, Talk it out, Today home (jot, to-dos, ideas in motion),
  trash + restore, text captures → note/idea.
- **P3** graph (0.3.0), streaming Ask + GraphRAG-lite (graph neighbours before rerank), Brainstorm with Swan
  (expand / questions / next / connect), brainstorm boards (canvas, connect, Swan ideas).
- **P4** Revisit (`/v1/resurface`), dated to-dos → calendar/Coming up (`note_dues`), flashcards + SM-2 review,
  Obsidian two-way sync (`mindcore obsidian`, brain every 5 min, `~/Obsidian Vault/40 - mind-core/`).
- Lesson: gpt-oss-120b spends max_tokens on reasoning first → always `reasoning: {effort: "low"}` and budgets ≥1500.
- **Next:** hands-on phone test of everything since 0.3.0 (graph gestures, boards drag/connect, editor, review flip,
  Talk it out) + edge drawer (task #5); fix what that finds. Then: Obsidian flashcards export (#flashcards format for
  the Spaced Repetition plugin), board ↔ note links in the graph, GitHub description as one-liner for verified repos.

## 17. IELTS prep + University News (started 2026-09-30)

Two new feature areas riding on the existing worker/D1/app instead of a separate project — cheaper and faster than
a standalone build. Context: Aman is prepping for MS abroad (Ireland/Netherlands/Germany/NZ, Sept 2027 intake —
see the "MS Abroad Plan" doc) and hasn't taken IELTS yet.

### IELTS prep
- **Schema** (`migrations/0012_ielts.sql`): `ielts_resources` (curated technique/link content per skill),
  `ielts_tasks` (writing/speaking prompts, reading passages + answer keys in `content_json`), `ielts_attempts`
  (graded submissions — band + feedback for writing/speaking, raw score for reading/listening).
- **Backend** (`src/ielts.ts`): writing/speaking graded via `env.AI` (`gpt-oss-120b`) against the real official
  IELTS band descriptors embedded as a rubric in the prompt; reading/listening auto-scored against a stored
  answer key, converted to a band via the official raw-score table **only when the section has ≥35 questions**
  (a 5-question sample can't map to a band meaningfully — return accuracy only below that, this was a real bug
  caught during testing, see lesson below). `/v1/ielts/progress` gives latest band per skill + an overall estimate.
  `/v1/brain/ielts/seed` (brain-token only) bulk-loads resources/tasks — used once already with 19 resources
  (British Council/IDP/ielts.org/IELTS Liz links + real band 6→7→8 techniques from live research) and 7 starter
  tasks (2 Writing Task 2, 1 Task 1 chart, 3 Speaking parts, 1 original reading passage — content is
  LLM/hand-authored, not copied from Cambridge books, to avoid the copyright issue flagged in that research).
- **Lesson (confirms the P3 one above):** same `gpt-oss-120b` reasoning-budget bug hit again on the grading
  endpoint — `reasoning: {effort:"medium"}` + `max_tokens: 1200` returned an empty response on a full-length
  essay (reasoning ate the whole budget). Fixed to `effort:"low"` + `max_tokens: 2000`, plus made JSON extraction
  from the model's reply more robust (find first `{`...last `}` instead of a strict fence-strip). Verified
  end-to-end against production: a real ~200-word essay scored band 6.5 with specific, correct feedback.
  **Rule for any new `env.AI` call in this codebase: always low effort, always ≥1500-2000 max_tokens.**
- **Android UI** (`ui/IeltsScreen.kt`): Progress / Study Plan / Practice / History tabs — delegated to opencode
  (see below) against a precise API-contract spec, since it's mechanical Compose work once the backend contract
  is fixed.
- **Not yet built:** Listening section content (needs audio — no TTS/audio pipeline set up yet, deferred; link out
  to British Council's free scored Listening mock in the meantime instead of hosting audio ourselves).

### Fixed 2026-09-30: opencode's Kotlin was never actually compiled
opencode's delegated output for `IeltsScreen.kt`/`NewsScreen.kt` did not compile at all — every top-level
function used `defun` instead of `fun` (~22 occurrences), `MaterialTheme.colorScheme` was used as a type
instead of `ColorScheme`, a wrong package path (`material3.tabs.Tab`), `TabRow`/`Tab` called with
parameters that don't exist in this Compose version, custom `IconButton`/`FilterChip` wrappers duplicating
real M3 components with wrong parameter names (conflicting top-level overloads across the two files), a
`private fun` declared as a local function (illegal — desynced the parser for the rest of the file), `by
remember { mutableStateOf(...) }` properties accessed with stale `.value` syntax, `@Composable` functions
(`openLink`, `markSeen`) invoked from non-composable `clickable` lambdas, and a type mix-up passing an
`AttemptResult` into a function typed to take the wrong data class. There were also real runtime bugs beyond
compile errors: `"%d".format(Double)` (crashes at runtime — `IllegalFormatConversionException`) and
`.toInt()` truncation instead of one-decimal band display. None of this was caught because `compileDebugKotlin`
was apparently never actually run against the final files. **Lesson: always run the real compiler after
an opencode/dsh delegation, never trust "should compile" — see the CLAUDE.md delegation rule.**
Both files were rewritten clean rather than patched bug-by-bug given the density of issues.

### Redesigned as a space switcher, not a bolted-on screen
Aman's call: don't wire IELTS/News in as just another pushed route off Settings — it'd read as two
unrelated apps mashed together. Instead: a top-level **space switcher** (`AppSpace.MINDCORE` /
`AppSpace.ABROAD`), reusing the existing `PillTabs` component (`Components.kt`) so it's visually native
to the app, not a new pattern. The pill sits at the top of each space's home screen (`ForYouScreen`'s
Today tab, and the new `AbroadHomeScreen`) — same place, same look, both sides reachable from either.
- `MindCoreApp.kt`: added `space`/`abroadTab` state; the root `when` now branches on `space` before `tab`;
  `BottomBar` (`Chrome.kt`) takes a `tabs` param (defaulted to the existing MindCore set) so the Abroad
  space gets its own bottom tabs (Home / Practice / News) through the *same* glass chrome, not a new one.
  The floating "+" capture button is repurposed per space (capture sheet in MindCore, jump-to-Practice in
  Abroad) rather than duplicating the bottom bar.
- `AbroadHomeScreen.kt` (new): the Abroad space's "Today" — IELTS overall-band `HeroCard` (tap → Practice),
  quick-action row, latest 3 unseen news items, link out to the "MS Abroad Plan" doc. `IeltsScreen`/
  `NewsScreen` had their back-arrow header removed (they're bottom-tab peers now, not pushed modals) and
  point-fixed as above.
- First glass-button attempt put `GlassIconButton` (header icons) *inside* the same
  `Box(Modifier.layerBackdrop(backdrop))` that records screen content for the bottom bar's glass to sample —
  a glass surface trying to read a recording that includes itself. **This crashed the app on every launch**:
  confirmed via real logcat (wireless adb) as a RenderThread stack overflow (`Cause: stack pointer is not in
  a rw map`), not a hunch. Fixed by moving the space switcher + header/plan/news icon buttons to a floating
  top bar in `MindCoreApp.kt` that sits *outside* the recorded tree, as a sibling — same pattern `BottomBar`
  already used safely. `GlassIconButton` now carries a doc comment spelling out the constraint so it doesn't
  get misused into scrolling content again.
- Per Aman's follow-up ask, replaced the space switcher's separate-pills look with a real **sliding
  segmented toggle** (one track, an animated thumb that glides between labels) — `SpaceSwitcher` in
  `Components.kt`, using `onGloballyPositioned` to measure each segment's real x/width/height and
  `animateDpAsState` for the glide. First version used `fillMaxHeight()` for the thumb and it filled almost
  the whole screen — `fillMaxHeight` sizes against the *incoming* constraint from up the tree, not against
  a sibling's resolved size (that's what `matchParentSize()` is for, which doesn't support a different
  width). Fixed by capturing height alongside x/width per segment and setting it explicitly.
- Also fixed while testing live: `Ielts`/`news` list endpoints wrap their arrays in an object
  (`{"tasks": [...]}`) but `Api.kt` parsed them as bare `JSONArray`s — silently broke Practice/resources/
  attempts/news (no crash, just empty/wrong data, caught by a raw JSON parse-error string rendering in the
  UI). And `TaskCard` checked `task.taskType` ("task1"/"task2") instead of `task.skill` ("writing"/
  "speaking") to decide which submission form to show — no task ever showed one, pre-dates this session's
  rewrite, missed because it's a semantic bug, not a compile error.
- **Verified live on-device**, not just compiled: got wireless adb working (`adb connect <phone-ip>:<port>`,
  the port changes each time — get it fresh from Settings → Developer options → Wireless debugging), then
  actually installed, launched, screenshotted, and tapped through every space/tab after each fix, catching
  the stack-overflow crash and both API bugs this way rather than shipping blind again.
- Published via `mindcore release`: 0.9.1 (20) → 0.9.2 (21) after the Plan-screen work below.

### MS Abroad Plan, native in-app (2026-09-30)
Aman's ask: stop linking out to the Claude Docs plan for anything — should all be visible in the app
without needing Chrome or another session — plus wants much more content (fuller university list w/
wishlist, job-market/employment context per country, a scholarships guide). Backend: `src/plan.ts` +
`migrations/0014_plan.sql`/`0015_plan_market.sql`.
- `plan_actions`, `plan_universities` (+ `wishlisted` toggle column), `plan_market`, `plan_scholarships` —
  seeded with the **real content already researched** in the "MS Abroad Plan" doc (pulled via the Claude
  Docs connector, not re-typed from memory): 10 urgent action items, 7 top-recommendation universities, 19
  scholarships with amount/eligibility/deadline.
- `plan_market` is real, sourced, honestly-caveated content, not fabricated stats: Ireland (Stamp 1G, 24mo,
  + the 1 Mar 2026 salary-threshold change, + a note that it's Ireland-only — doesn't grant EU-wide work
  rights, Ireland isn't in Schengen either; EU Blue Card / 5-yr long-term residence are the real paths to
  intra-EU mobility later), Netherlands (zoekjaar orientation year; NL employment ~80.7% is general, not
  grad-specific — labeled as such), Germany (18-month post-grad job-seeking permit, distinct from the
  6-month external Job Seeker Visa), New Zealand (3-yr PSW visa; real Stats NZ/Universities NZ numbers —
  55% of international grads stay and work, ~15.7% land jobs below their qualification level vs 9.5% of
  domestic grads — the most solid official numbers of the four).
- `PlanScreen.kt` (new): pushed route (back-arrow header, not a bottom-tab peer), 4-way `PillTabs` —
  Actions / Universities / Market / Scholarships. Universities cards have a working wishlist star
  (optimistic UI + `POST /v1/plan/universities/:id/wishlist`, verified it actually persists server-side).
  `AbroadHomeScreen`'s "Full plan" and the floating bar's plan-link icon both `push("plan")` now instead of
  opening a browser.
- **Not done this pass** (flagged, not silently dropped): the university list is still the cross-country
  top-7, not the fuller per-country tables from the doc (Ireland alone had 7, Netherlands 4, Germany 5,
  Finland/Sweden 6, NZ 7 — more rows exist to pull in); no "future-proof course" or specific job-role/skill
  breakdown content yet (Aman asked for this — needs real research, didn't want to fabricate precision).
- Verified live end-to-end: all 4 tabs render with real content, wishlist toggle confirmed persisted via a
  direct API check, zero crashes across the whole session's testing.

### University News
- **Schema** (`migrations/0013_news.sql`): `uni_news` (university, country, source_url, headline, summary, kind,
  seen flag).
- **Backend** (`src/news.ts`): `/v1/news` (list, `?unseen=1` filter), `/v1/news/:id/seen`, and
  `/v1/brain/news/ingest` (brain-token only, dedupes by `sha1(source_url+headline)`) — fed by Firecrawl monitors
  watching the MS Abroad Plan shortlist's admissions/fees/scholarship pages (Ireland + NZ set up 2026-09-30,
  weekly cadence; Netherlands/Germany pages need their exact scholarship-page URLs confirmed before adding —
  the research only had domain-level references for those, not deep links).
  Diffs still need a small step to turn a raw monitor check into `uni_news` rows (an LLM call to turn "this text
  changed" into a `{headline, summary, kind}` — not built yet, monitors are running and collecting diffs in the
  meantime).
- **Android UI** (`ui/NewsScreen.kt`): delegated alongside IeltsScreen.kt in the same opencode run.

### Integration — done (see the Space Switcher and Plan-screen sections above for the actual history)
The original plan here (push routes off Settings, manual wiring) was superseded by the space-switcher
redesign. IELTS/News are bottom-tab peers in the Abroad space, not pushed routes.
- **Still open:** the diff→`uni_news` ingestion step for the Firecrawl monitors (raw page-diff → an LLM call
  producing `{headline, summary, kind}` rows) — monitors are running and collecting diffs, nothing turns them
  into visible news yet. Netherlands/Germany monitor URLs also still need their exact scholarship-page deep
  links confirmed (only had domain-level references).

### Job-market research + deeper content (2026-09-30, same day, continued)
Aman asked for research on "what's next" after the Plan screen shipped — future-proof specializations, job
roles/skills, and to keep expanding. Real research each time, not fabrication; gaps stated explicitly rather
than guessed at.
- **Synthesis:** applied AI/software engineering is more reliably employable than pure ML/data-science
  research at the entry level (consistent theme across industry sources) — Aman's actual portfolio (full-
  stack + RAG/agent integration, not theoretical ML) already matches that profile. Current shortlist doesn't
  need rethinking; the real gap is **MLOps and AI governance**, named as 2026 in-demand skills in Ireland
  specifically that his projects haven't touched.
- `plan_market` gained `skills_demand` (migration 0016) and `salary_range` (migration 0017) columns, filled
  with real per-country findings: Ireland's named skills (MLOps, ethical AI governance, generative AI
  integration), the EU-wide applied-AI-over-pure-research pattern, Germany's experience-favoring market (one
  recruiter's read: the 2026 "sweet spot" is 5-10 YOE, tempering fresh-grad expectations), and an honest
  "no NZ-specific data found" flag. Salary figures are presented as ranges with sourcing caveats — the
  Netherlands sources genuinely disagreed (one thread cited senior devs at €100-200k, an aggregator showed
  ~$45k average) and that disagreement is stated in the UI rather than resolved by picking one number.
- `plan_universities` expanded from the cross-country top-7 to 25 — pulled the fuller per-country tables
  already researched in the plan doc (Ireland's own list had 6 total, Netherlands 3 more, Germany 4 separate
  entries replacing the placeholder "RWTH/Stuttgart" combo, Finland/Sweden/France 4, NZ 5). Also deepened the
  previously-"not deep-dived" France/Finland/Sweden/Uppsala/Lund entries with real entry requirements and
  deadlines (CentraleSupélec's 240-ECTS Engineering/Math/Physics criterion is a direct match for Aman's
  degree; Helsinki's 2027 application window is a narrow 5–19 Jan 2027; Uppsala's sourced deadline looks like
  it's for the prior cycle — flagged for re-verification, not silently used).
- `PlanScreen.kt`'s Universities tab got a country filter chip row (25 flat cards was too much to scroll
  blind) — reuses `PillTabs`, no new component needed.
- Added 2 concrete `plan_actions` entries directly answering the MLOps skills gap: the free MLOps Zoomcamp
  course (datatalks.club — Docker/MLflow/monitoring/CI-CD, real portfolio project, not just a certificate),
  and redeploying an existing project (WhatsApp AI Agent or the job-search SaaS) with production-grade
  monitoring instead of ad-hoc deployment — turns the learning into a resume line, not just a course name.
- **Verified live on-device throughout** (wireless adb was still connected from earlier in the session) —
  caught one real mistake mid-session: a stray tap opened a GitHub link in a Chrome Custom Tab instead of the
  intended switcher pill; caught via `dumpsys window | grep mCurrentFocus` before continuing, not assumed.
  Lesson for next time: check foreground focus after every navigation tap, not just after crash-prone ones.
- Published as 0.9.4 (23) via `mindcore release`.
