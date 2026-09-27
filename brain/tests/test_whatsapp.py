from datetime import datetime

from mindcore.triage import triage
from mindcore.whatsapp import extract_links, normalize_url, parse_messages

IOS = """[3/21/26, 11:13:43 AM] You: ‎<image omitted>
[3/23/26, 9:30:52 PM] You: [Forwarded] Check this repo
https://github.com/karakeep-app/karakeep
[9/27/26, 10:49:55 AM] You: https://www.instagram.com/reel/Ddw0QuEu1Rq/?stkn=OWdsenp5YnF0aHhn
"""
ANDROID = """21/03/2026, 11:13 pm - You: IMG-20260321-WA0001.jpg (file attached)
23/03/2026, 9:30 am - You: https://youtu.be/abc123?si=xyz
second line of the same message
"""


def test_ios_format():
    msgs = parse_messages(IOS)
    assert len(msgs) == 3
    assert msgs[0].when == datetime(2026, 3, 21, 11, 13, 43) and msgs[0].media == "image"
    assert msgs[1].forwarded and msgs[1].text.startswith("Check this repo\n")
    links = extract_links(msgs)
    assert [l.url for l in links] == [
        "https://github.com/karakeep-app/karakeep",
        "https://instagram.com/reel/Ddw0QuEu1Rq/",
    ]
    assert links[0].title == "Check this repo"


def test_android_format_day_first_and_continuation():
    msgs = parse_messages(ANDROID)
    assert msgs[0].when == datetime(2026, 3, 21, 23, 13) and msgs[0].media_file == "IMG-20260321-WA0001.jpg"
    assert msgs[1].text.endswith("second line of the same message")
    assert extract_links(msgs)[0].url == "https://youtube.com/watch?v=abc123"


def test_normalize_merges_instagram_variants():
    a = normalize_url("https://www.instagram.com/someone/reel/ABC/?igsh=x")
    b = normalize_url("https://instagram.com/reels/ABC")
    assert a == b == "https://instagram.com/reel/ABC/"


def test_triage_own_repo_is_work():
    msgs = parse_messages("[1/1/26, 1:00:00 PM] You: https://github.com/mehtatechteam/raising-web\n")
    t = triage(extract_links(msgs)[0])
    assert t.shelf == "work" and t.mine
