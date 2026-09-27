package app.mindcore.ui

// Real items from the M0 run on your saved reels (out/reels/items.json + the GitHub check),
// hardcoded until the app talks to the backend.

enum class Kind(val label: String) { Repo("Repo"), Tool("Tool"), Course("Course"), Job("Job") }

enum class Trust(val label: String) { Verified("Verified"), Unconfirmed("Unconfirmed"), Check("Check claims") }

data class Item(
    val kind: Kind,
    val name: String,
    val oneLine: String,
    val trust: Trust,
    val detail: String,
    val source: String,
)

val sampleItems = listOf(
    Item(Kind.Repo, "Archify", "Turns a codebase into a clickable animated diagram.", Trust.Verified,
        "tt-a1i/archify · ★72k · MIT", "Neeraj Chemburkar · reel"),
    Item(Kind.Repo, "Exercises Dataset", "1,324 gym exercises with GIFs, muscles and equipment.", Trust.Verified,
        "hasaneyldrm/exercises-dataset · ★22k · found in video frames", "Javi Niguez · reel"),
    Item(Kind.Tool, "OmniRoute", "Routes Claude Code to free models when you hit limits.", Trust.Check,
        "diegosouzapw/OmniRoute · ★70k · MIT · \"1.6B free tokens/month\" unverified", "Peter And Stewie · reel"),
    Item(Kind.Repo, "img2threejs", "Rebuilds an object from one photo as Three.js code.", Trust.Verified,
        "img2threejs/img2threejs · ★17k · Apache-2.0", "Trending OpenSource · reel"),
    Item(Kind.Job, "AMD · Software Engineer I", "Fresher role in Bengaluru.", Trust.Check,
        "\"22–40 LPA\" has no source yet · posting not checked", "Ayu · reel"),
    Item(Kind.Course, "Recursive Self-Improving Agents", "Stanford course, free on YouTube.", Trust.Unconfirmed,
        "\"\$850,000 course\" claim unverified", "Harnoor Singh · reel"),
    Item(Kind.Tool, "Context7", "MCP server that pulls live docs into Claude.", Trust.Unconfirmed,
        "repo not checked yet · from a list of 5 MCP servers", "Colton Dean · reel"),
)
