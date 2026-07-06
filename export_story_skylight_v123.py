#!/usr/bin/env python3
"""Generate Story Skylight V1–V4 web demo from Kotlin sources + shared assets."""

from __future__ import annotations

import json
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
SHARED = OUT / "shared"
RES = ROOT / "playgrounds/src/main/res"
EFFECT = OUT.parent / "effect-loading-1s"
ALBUM = OUT.parent / "album-v1234-demo"

REFRESH_NEW_STORIES = [
    {
        "label": "Cenis",
        "avatar": "inbox_avatar_cenis.png",
        "timeText": "now",
        "photos": ["lindsey_story_photo_1.png", "lindsey_story_photo_2.png", "lindsey_story_photo_3.png"],
    },
    {
        "label": "Tommy",
        "avatar": "inbox_avatar_tommy.png",
        "timeText": "now",
        "photos": ["maren_story_photo_1.jpg", "maren_story_photo_2.jpg", "maren_story_photo_3.jpg"],
    },
    {
        "label": "summer",
        "avatar": "inbox_avatar_summer.png",
        "timeText": "now",
        "photos": ["rayna_story_photo_1.jpg", "rayna_story_photo_2.jpg", "rayna_story_photo_3.jpg"],
    },
]

VARIANTS = {
    "v1": {
        "label": "V1",
        "pullThreshold": 24,
        "maxPullDistance": 72,
        "expandOnDrag": False,
        "storySlideEnabled": False,
        "autoExpandOnEnter": False,
        "startExpanded": True,
        "topDownStoryRevealEnabled": True,
        "lockStoryExpanded": True,
        "chainRefreshAfterExpand": False,
        "inboxTabUnreadDotOnce": True,
        "addUnreadStoriesOnRefresh": True,
        "refreshNewStories": REFRESH_NEW_STORIES,
        "releaseHintEnabled": False,
    },
    "v2": {
        "label": "V2",
        "pushThreshold": 12,
        "pullThreshold": 0,
        "maxPullDistance": 118,
        "expandOnDrag": True,
        "storySlideEnabled": False,
        "overlayMaskEnabled": False,
        "overlayOpacityEnabled": True,
        "overlayMinOpacity": 0.18,
        "overlayOpacityStartProgress": 0.2,
        "overlayOpacityEndProgress": 1,
        "autoExpandOnEnter": True,
        "autoExpandOncePerRun": True,
        "inboxTabUnreadDotOnce": True,
        "startExpanded": False,
        "topDownStoryRevealEnabled": False,
        "lockStoryExpanded": False,
        "chainRefreshAfterExpand": True,
        "allReadAutoCollapseEnabled": True,
        "allReadCollapsedHintEnabled": True,
        "allReadCollapsedHintText": "Pull down to view story",
        "releaseHintEnabled": False,
    },
    "v3": {
        "label": "V3",
        "pushThreshold": 12,
        "pullThreshold": 0,
        "maxPullDistance": 118,
        "expandOnDrag": True,
        "storySlideEnabled": False,
        "integratedSlideReveal": True,
        "integratedMaskEnabled": False,
        "integratedOpacityEnabled": True,
        "integratedOpacityMin": 0.18,
        "integratedOpacityStartProgress": 0.06,
        "integratedOpacityEndProgress": 0.85,
        "autoExpandOnEnter": True,
        "autoExpandOncePerRun": True,
        "inboxTabUnreadDotOnce": True,
        "startExpanded": True,
        "topDownStoryRevealEnabled": True,
        "lockStoryExpanded": False,
        "chainRefreshAfterExpand": True,
        "addUnreadStoriesOnRefresh": True,
        "refreshNewStories": REFRESH_NEW_STORIES,
        "allReadAutoCollapseEnabled": True,
        "allReadCollapsedHintEnabled": True,
        "allReadCollapsedHintText": "Pull down to view story",
        "releaseHintEnabled": False,
        "desktopEnabled": True,
    },
    "v4": {
        "label": "V4",
        "pullThreshold": 24,
        "maxPullDistance": 32,
        "expandOnDrag": False,
        "storySlideEnabled": True,
        "autoExpandOnEnter": True,
        "autoExpandOncePerRun": True,
        "inboxTabUnreadDotOnce": True,
        "startExpanded": False,
        "topDownStoryRevealEnabled": False,
        "lockStoryExpanded": False,
        "chainRefreshAfterExpand": False,
        "releaseHintEnabled": True,
        "releaseHintText": "Pull down to view story",
    },
}

MOTION = {
    "expandMs": 450,
    "collapseMs": 350,
    "storySlideMs": 300,
    "settleBackMs": 250,
    "autoExpandDelayMs": 400,
    "pushFlingVelocity": 1500,
    "pullDamping": 0.65,
    "pushThreshold": 30,
    "maxHeight": 118,
    "refreshThreshold": 32,
    "refreshIndicatorHeight": 56,
    "refreshMaxPullDistance": 56,
    "refreshDurationMs": 1200,
    "tabCrossfadeMs": 150,
    "storyDurationMs": 3500,
    "previewEnterMs": 180,
    "previewExitMs": 150,
    "photoCrossfadeMs": 250,
    "storyAddEnterMs": 320,
    "storyAddExitMs": 260,
    "effectCoverLoadMs": 1200,
    "albumEmergenceMs": 160,
}

INBOX_ROWS = [
    {
        "kind": "category",
        "title": "New Followers",
        "message": "Rhianna started following you.",
        "badge": 9,
        "icon": "inbox_category_followers_icon.png",
        "bg": "#00ABF4",
    },
    {
        "kind": "category",
        "title": "Activity",
        "message": "Jaela liked your video.",
        "badge": 1,
        "icon": "inbox_category_activity_icon.png",
        "bg": "#FF3B76",
    },
    {
        "kind": "category",
        "title": "System notifications",
        "message": "TikTok:Updates to your post performance",
        "timestamp": "1h",
        "badge": 1,
        "icon": "inbox_category_system_icon.png",
        "bg": "#32364B",
    },
    {
        "kind": "category",
        "title": "Featured partners",
        "message": "Here comes a new brand{brand_name}",
        "timestamp": "1h",
        "badge": 1,
        "icon": "inbox_category_partners_icon.png",
        "bg": "#0075DC",
    },
    {
        "kind": "avatar",
        "title": "Cenis Grimm",
        "message": "Hello how r u recently",
        "timestamp": "1h",
        "badge": 1,
        "avatar": "inbox_avatar_cenis.png",
        "online": True,
    },
    {
        "kind": "avatar",
        "title": "Tommy Tang",
        "message": "Hello how r u recently",
        "timestamp": "1h",
        "avatar": "inbox_avatar_tommy.png",
    },
    {
        "kind": "avatar",
        "title": "summer",
        "message": "Hello how r u recently",
        "timestamp": "1h",
        "avatar": "inbox_avatar_summer.png",
    },
]

SUGGESTED_ROWS = [
    {
        "title": "🦆🦆",
        "subtitle": "Friends with",
        "avatar": "inbox_suggested_duck.png",
        "stack": ["inbox_stack_avatar_1.png", "inbox_stack_avatar_2.png"],
        "overflow": "+6",
    },
    {
        "title": "Sunsun77",
        "subtitle": "Follows",
        "avatar": "inbox_suggested_sunsun.png",
        "stack": ["inbox_stack_avatar_1.png", "inbox_stack_avatar_2.png"],
        "stack_alt": True,
        "trailing": "inbox_stack_avatar_3.png",
    },
    {
        "title": "wanda",
        "subtitle": "You may know Wang",
        "avatar": "inbox_suggested_wanda.png",
    },
]

SKYLIGHT_STORIES = [
    {"label": "Lindsey", "avatar": "inbox_story_lindsey.png"},
    {"label": "Maren", "avatar": "inbox_story_maren.png"},
    {"label": "Alena", "avatar": "inbox_story_alena.png"},
    {"label": "Rayna", "avatar": "inbox_story_rayna.png"},
]

FIGMA_ASSETS = {
    "figma/story_preview_like.svg": "https://www.figma.com/api/mcp/asset/6e37cc7b-044d-47d5-856a-d056f4c9e214",
    "figma/story_preview_share.svg": "https://www.figma.com/api/mcp/asset/14b9e419-9204-4ed1-b45d-73582e283f47",
    "figma/story_preview_camera.svg": "https://www.figma.com/api/mcp/asset/72523eff-e4b0-41e4-99df-4d53c853fe25",
    "figma/story_preview_close.svg": "https://www.figma.com/api/mcp/asset/eb4f1b90-b21f-4526-954b-f2f6147dcd1c",
    "figma/story_preview_top_overlay.svg": "https://www.figma.com/api/mcp/asset/3bba05e5-d916-4341-b96a-009f17e248c0",
    "figma/story_add_chevron.svg": "https://www.figma.com/api/mcp/asset/2e513a0b-201f-4f88-9928-f3fcbf578364",
    "figma/story_add_camera.svg": "https://www.figma.com/api/mcp/asset/7fd75c20-ef5a-4a07-b1a8-5c153865afeb",
    "figma/story_add_checkbox.svg": "https://www.figma.com/api/mcp/asset/6190f066-a875-4c28-a090-b590bbc00194",
    "figma/story_add_grid_01.jpg": "https://www.figma.com/api/mcp/asset/91dd1852-dc2c-4823-8ddd-93e8f32e3485",
    "figma/story_add_grid_02.jpg": "https://www.figma.com/api/mcp/asset/92e6955d-8c0e-4d7d-bc63-89b017e11c77",
    "figma/story_add_grid_03.jpg": "https://www.figma.com/api/mcp/asset/758d9e53-dd1e-447d-a8df-c39d528cb4ac",
    "figma/story_add_grid_04.jpg": "https://www.figma.com/api/mcp/asset/580d67fa-7b58-4e43-9d4a-1001feae196f",
    "figma/story_add_grid_05.jpg": "https://www.figma.com/api/mcp/asset/c0dc476b-0279-420a-a128-9c26fc826328",
    "figma/story_add_grid_06.jpg": "https://www.figma.com/api/mcp/asset/395c4bd2-2a19-4060-9c22-1418e81a0356",
    "figma/story_add_grid_07.jpg": "https://www.figma.com/api/mcp/asset/fb4e1ae2-0c1c-4e8b-a08d-a75cf0758363",
    "figma/story_add_grid_08.jpg": "https://www.figma.com/api/mcp/asset/72801c1c-551a-4344-a9d3-4aeb0f593315",
    "figma/story_add_grid_09.jpg": "https://www.figma.com/api/mcp/asset/87cd22cd-e7b1-44bb-a3ac-7e7093b68e6a",
    "figma/story_add_grid_10.jpg": "https://www.figma.com/api/mcp/asset/3637d83a-854b-49da-8fda-66e3515ad968",
    "figma/story_add_grid_11.jpg": "https://www.figma.com/api/mcp/asset/aa879f63-49ce-4d8a-86fb-abce5a30c82e",
    "figma/story_add_grid_12.jpg": "https://www.figma.com/api/mcp/asset/d31c1a53-d4df-4399-b20f-70a96cd333a1",
    "icons/feed/figma/story-lite/nav_create_dark.svg": "https://www.figma.com/api/mcp/asset/f8bf2513-a8e2-465f-941b-a0943247ba07",
    "icons/feed/figma/story-lite/nav_create_light.svg": "https://www.figma.com/api/mcp/asset/e4ee4ef9-a6f0-4657-984b-5e757a57ad1f",
    # Bottom nav — TUX Bottom Nav Bar (3391:30629); V1 inbox instance 3428:6030
    "icons/feed/figma/story-lite-3401/feed_home_active.svg": "https://www.figma.com/api/mcp/asset/34bff558-0740-43b1-b8a2-ed74d456b927",
    "icons/feed/figma/story-lite-3401/feed_explore_inactive.svg": "https://www.figma.com/api/mcp/asset/516c8228-a05b-41ec-9a6e-b93dd63aa945",
    "icons/feed/figma/story-lite-3401/feed_inbox_inactive.svg": "https://www.figma.com/api/mcp/asset/b61bd351-401e-400e-87e9-a4e6cfb5691f",
    "icons/feed/figma/story-lite-3401/feed_me_inactive.svg": "https://www.figma.com/api/mcp/asset/6c74ce13-fdc4-4e90-88d9-2b3a2504deb6",
    "icons/feed/figma/story-lite-3401/feed_create.svg": "https://www.figma.com/api/mcp/asset/df86485b-9347-4ca6-86a4-6004809d62a0",
    "icons/feed/figma/story-lite-3401/inbox_home_inactive.svg": "https://www.figma.com/api/mcp/asset/f98e2a33-ce95-4b40-889a-04bdfc0f9e36",
    "icons/feed/figma/story-lite-3401/inbox_explore_inactive.svg": "https://www.figma.com/api/mcp/asset/190d8552-77b4-4a66-b138-9e8af88b8237",
    "icons/feed/figma/story-lite-3401/inbox_inbox_active.svg": "https://www.figma.com/api/mcp/asset/a0db6b74-54a5-439b-b3e2-246d334a0b1f",
    "icons/feed/figma/story-lite-3401/inbox_me_inactive.svg": "https://www.figma.com/api/mcp/asset/c606f9c4-dfb2-4924-9e25-24385ec5f4da",
    "icons/feed/figma/story-lite-3401/inbox_create.svg": "https://www.figma.com/api/mcp/asset/6f501327-2612-47bc-be46-ecbda80dc5be",
    "inbox/inbox_section_info.png": "https://www.figma.com/api/mcp/asset/a90a41d1-b6f0-4feb-a58d-e89dc658da07",
    "inbox/inbox_story_create_ring.svg": "https://www.figma.com/api/mcp/asset/be06bba1-c9fe-47be-8811-2322f5b46b88",
    "inbox/inbox_story_create_base.png": "https://www.figma.com/api/mcp/asset/27e27669-b693-4bb8-839a-e0fa412dadac",
    "inbox/inbox_story_create_photo.jpg": "https://www.figma.com/api/mcp/asset/43c8b2e6-62b8-4551-a4c7-0820b54c5823",
    "inbox/inbox_story_create_badge_icon.svg": "https://www.figma.com/api/mcp/asset/a8e1f5f9-9ccf-4776-9e66-378cb27e643a",
    "inbox/inbox_story_create_badge_stroke.svg": "https://www.figma.com/api/mcp/asset/46de87a9-6cd3-43b2-a5f9-cbdd1c2b0bc1",
}

SL3401 = "story-lite-3401"

STORY_ADD_GRID = [(f"story_add_grid_{i:02d}.jpg", i == 1) for i in range(1, 13)]

STORY_PREVIEWS = {
    "Lindsey": {
        "avatar": "inbox_story_lindsey.png",
        "timeText": "3h ago",
        "photos": [
            "lindsey_story_photo_1.png",
            "lindsey_story_photo_2.png",
            "lindsey_story_photo_3.png",
            "lindsey_story_photo_4.png",
        ],
    },
    "Maren": {
        "avatar": "inbox_story_maren.png",
        "photos": ["maren_story_photo_1.jpg", "maren_story_photo_2.jpg", "maren_story_photo_3.jpg"],
    },
    "Alena": {
        "avatar": "inbox_story_alena.png",
        "photos": ["alena_story_photo_1.jpg", "alena_story_photo_2.jpg", "alena_story_photo_3.jpg"],
    },
    "Rayna": {
        "avatar": "inbox_story_rayna.png",
        "photos": ["rayna_story_photo_1.jpg", "rayna_story_photo_2.jpg", "rayna_story_photo_3.jpg"],
    },
    "Create": {
        "avatar": "inbox_story_create_photo.jpg",
        "timeText": "now",
        "photos": [
            "lindsey_story_photo_1.png",
            "lindsey_story_photo_2.png",
            "lindsey_story_photo_3.png",
            "lindsey_story_photo_4.png",
        ],
    },
}


def asset(path: str) -> str:
    return f"../../shared/assets/{path}"


def download_figma_assets() -> None:
    for rel, url in FIGMA_ASSETS.items():
        out = SHARED / "assets" / rel
        out.parent.mkdir(parents=True, exist_ok=True)
        force = SL3401 in rel or rel.startswith("figma/story_dm_emoji") or rel.startswith("figma/story_preview_like") or rel.startswith("figma/story_preview_share") or rel.startswith("inbox/inbox_story_create")
        if out.is_file() and out.stat().st_size > 100 and not force:
            if rel.endswith(".svg"):
                _normalize_figma_svg(out)
            continue
        result = subprocess.run(
            ["curl", "-sfL", url, "-o", str(out)],
            capture_output=True,
            text=True,
        )
        if result.returncode != 0 or not out.is_file() or out.stat().st_size == 0:
            print(f"  warn: failed {rel}: {result.stderr.strip() or 'empty response'}")
        else:
            if rel.endswith(".svg"):
                _normalize_figma_svg(out)
            print(f"  downloaded {rel} ({out.stat().st_size} bytes)")

    for name in ("story_preview_like", "story_preview_share"):
        svg = SHARED / "assets" / "figma" / f"{name}.svg"
        png = SHARED / "assets" / "figma" / f"{name}.png"
        if svg.is_file():
            _svg_to_png(svg, png, size=56)
            if png.is_file():
                print(f"  rasterized figma/{name}.png ({png.stat().st_size} bytes)")


def _normalize_figma_svg(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = text.replace('preserveAspectRatio="none"', 'preserveAspectRatio="xMidYMid meet"')
    text = text.replace('fill="var(--fill-0, white)"', 'fill="#ffffff"')
    text = text.replace("fill='var(--fill-0, white)'", "fill='#ffffff'")
    text = text.replace('stroke="var(--stroke-0, white)"', 'stroke="#ffffff"')
    text = text.replace("stroke='var(--stroke-0, white)'", "stroke='#ffffff'")
    slug = path.stem.replace(".", "_").replace("-", "_")
    text = text.replace("filter0_dd_0_4", f"filter0_dd_{slug}")
    path.write_text(text, encoding="utf-8")


def _svg_to_png(svg_path: Path, png_path: Path, *, size: int = 56) -> None:
    if not svg_path.is_file():
        return
    result = subprocess.run(
        ["rsvg-convert", "-w", str(size), "-h", str(size), str(svg_path), "-o", str(png_path)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"  warn: png convert failed {png_path.name}: {result.stderr.strip()}")


def badge_html(count: int) -> str:
    return f'<span class="inbox-badge">{count}</span>'


def message_line(message: str, timestamp: str | None = None, *, compact: bool = False) -> str:
    ts = ""
    if timestamp:
        ts = (
            f'<span class="inbox-ts-gap">'
            f'<img src="{asset("inbox/inbox_timestamp_dot.png")}" width="2" height="2" alt="" />'
            f'<span class="inbox-ts">{timestamp}</span></span>'
        )
    cls = "inbox-msg inbox-msg-regular" if compact else "inbox-msg"
    return f'<div class="{cls}"><span>{message}</span>{ts}</div>'


def inbox_cell(row: dict) -> str:
    trailing = badge_html(row["badge"]) if row.get("badge") else ""
    title_cls = "inbox-cell-title"
    if row["kind"] == "avatar" and not row.get("badge"):
        title_cls += " inbox-title-semibold"
    if row["kind"] == "category":
        leading = (
            f'<div class="inbox-leading inbox-leading-cat" style="background:{row["bg"]}">'
            f'<img src="{asset("inbox/" + row["icon"])}" width="28" height="28" alt="" /></div>'
        )
        msg = message_line(row["message"], row.get("timestamp"))
    else:
        online = (
            f'<img class="inbox-online-dot" src="{asset("inbox/inbox_avatar_online_dot.png")}" width="16" height="16" alt="" />'
            if row.get("online")
            else ""
        )
        leading = (
            f'<div class="inbox-leading inbox-leading-avatar">'
            f'<img class="inbox-avatar-img" src="{asset("inbox/" + row["avatar"])}" alt="" />'
            f'<img class="inbox-avatar-border" src="{asset("inbox/inbox_avatar_border.png")}" alt="" />'
            f"{online}</div>"
        )
        msg = message_line(row["message"], row.get("timestamp"), compact=not row.get("badge"))
    return (
        f'<div class="inbox-cell">'
        f"{leading}"
        f'<div class="inbox-cell-body">'
        f'<div class="{title_cls}">{row["title"]}</div>{msg}</div>'
        f'<div class="inbox-cell-trailing">{trailing}</div></div>'
    )


def suggested_cell(row: dict) -> str:
    stack_html = ""
    if row.get("stack"):
        imgs = "".join(
            f'<img class="inbox-stack-av" src="{asset("inbox/" + s)}" alt="" />' for s in row["stack"]
        )
        if row.get("overflow"):
            stack_html = (
                f'<span class="inbox-stack">'
                f"{imgs}"
                f'<span class="inbox-stack-overflow">'
                f'<img src="{asset("inbox/inbox_stack_overflow.png")}" alt="" />'
                f'<span>{row["overflow"]}</span></span></span>'
            )
        elif row.get("trailing"):
            bg = "inbox_stack_overflow_alt.png" if row.get("stack_alt") else "inbox_stack_overflow.png"
            stack_html = (
                f'<span class="inbox-stack">'
                f"{imgs}"
                f'<img class="inbox-stack-av" src="{asset("inbox/" + row["trailing"])}" alt="" /></span>'
            )
        else:
            stack_html = f'<span class="inbox-stack">{imgs}</span>'
    subtitle = (
        f'<div class="inbox-suggested-sub"><span>{row["subtitle"]}</span>{stack_html}</div>'
        if row.get("stack")
        else f'<div class="inbox-suggested-sub plain">{row["subtitle"]}</div>'
    )
    return (
        f'<div class="inbox-cell inbox-suggested">'
        f'<div class="inbox-leading inbox-leading-avatar suggested">'
        f'<img class="inbox-avatar-img" src="{asset("inbox/" + row["avatar"])}" alt="" />'
        f'<img class="inbox-avatar-border" src="{asset("inbox/inbox_avatar_border.png")}" alt="" /></div>'
        f'<div class="inbox-cell-body">'
        f'<div class="inbox-cell-title">{row["title"]}</div>{subtitle}</div>'
        f'<div class="inbox-suggested-actions">'
        f'<button type="button" class="inbox-follow-btn">Follow</button>'
        f'<span class="inbox-suggested-dismiss" aria-label="Dismiss">'
        f'<img src="{asset("inbox/inbox_dismiss_x.svg")}" width="16" height="16" alt="" />'
        f"</span></div></div>"
    )


FIGMA_CREATE_VARIANTS = ("v1", "v3")


def skylight_create_item(variant: str = "") -> str:
    if variant in FIGMA_CREATE_VARIANTS:
        return (
            f'<button type="button" class="skylight-item" data-skylight-action="create" data-figma="3461:43970">'
            f'<span class="skylight-avatar-slot">'
            f'<img class="skylight-create-ring" src="{asset("inbox/inbox_story_create_ring.svg")}" alt="" />'
            f'<span class="skylight-create-body">'
            f'<img class="skylight-create-photo" src="{asset("inbox/inbox_story_create_photo.jpg")}" alt="" />'
            f'<img class="skylight-create-base" src="{asset("inbox/inbox_story_create_base.png")}" alt="" />'
            f'<span class="skylight-plus-badge">'
            f'<img class="skylight-create-badge-icon" src="{asset("inbox/inbox_story_create_badge_icon.svg")}" alt="" />'
            f'<img class="skylight-create-badge-stroke" src="{asset("inbox/inbox_story_create_badge_stroke.svg")}" alt="" />'
            f"</span></span></span>"
            f'<span class="skylight-label">Create</span></button>'
        )
    return (
        f'<button type="button" class="skylight-item" data-skylight-action="create">'
        f'<span class="skylight-avatar-slot">'
        f'<img class="skylight-create-bg" src="{asset("inbox/inbox_story_create.png")}" alt="" />'
        f'<span class="skylight-plus-badge">'
        f'<img class="skylight-plus-icon" src="{asset("inbox/inbox_story_plus.png")}" alt="" />'
        f'<img class="skylight-plus-stroke" src="{asset("inbox/inbox_story_plus_stroke.png")}" alt="" />'
        f"</span></span>"
        f'<span class="skylight-label">Create</span></button>'
    )


def skylight_item(label: str, avatar: str, is_create: bool = False, variant: str = "") -> str:
    if is_create:
        return skylight_create_item(variant)
    return (
        f'<button type="button" class="skylight-item" data-story-label="{label}">'
        f'<span class="skylight-avatar-slot">'
        f'<img class="skylight-ring" src="{asset("inbox/inbox_story_ring.png")}" alt="" />'
        f'<img class="skylight-avatar" src="{asset("inbox/" + avatar)}" alt="{label}" /></span>'
        f'<span class="skylight-label">{label}</span></button>'
    )


def skylight_row_html(variant: str = "") -> str:
    items = [skylight_item("", "", is_create=True, variant=variant)]
    items += [skylight_item(s["label"], s["avatar"]) for s in SKYLIGHT_STORIES]
    return f'<div class="skylight-row" id="skylightRow">{"".join(items)}</div>'


def inbox_cells_html() -> str:
    cells = "".join(inbox_cell(r) for r in INBOX_ROWS)
    section = (
        f'<div class="inbox-section-title">'
        f'<span>Suggested account</span>'
        f'<img src="{asset("inbox/inbox_section_info.png")}" width="12" height="12" alt="" /></div>'
    )
    suggested = "".join(suggested_cell(r) for r in SUGGESTED_ROWS)
    return cells + section + suggested


def nav_lite_cell(label: str, icon: str, *, active: bool = False, attrs: str = "") -> str:
    """Story Lite bottom nav cell — single TUX tab icon per state."""
    a = asset
    tab_id = {"Home": "home", "Explore": "explore", "Inbox": "inbox", "Me": "me"}[label]
    cls = "feed-nav-cell"
    if active:
        cls += " active"
    nav_attrs = f'data-feed-nav="{tab_id}"'
    if attrs:
        nav_attrs += f" {attrs}"
    return (
        f'<button type="button" class="{cls}" {nav_attrs}>'
        f'<span class="feed-nav-icon feed-nav-icon--lite">'
        f'<img src="{a(f"icons/feed/figma/{icon}")}" alt="" />'
        f'</span><span class="feed-nav-label">{label}</span></button>'
    )


def feed_create_btn(*, btn_id: str = "feedCreateBtn", variant: str = "feed") -> str:
    a = asset
    id_attr = f' id="{btn_id}"' if btn_id else ""
    create_asset = f"icons/feed/figma/{SL3401}/inbox_create.svg" if variant == "inbox" else f"icons/feed/figma/{SL3401}/feed_create.svg"
    return (
        f'<button{id_attr} type="button" class="feed-nav-create" data-feed-nav="create" aria-label="Create">'
        f'<span class="feed-nav-create-frame">'
        f'<img src="{a(create_asset)}" alt="" />'
        f"</span></button>"
    )


def desktop_html() -> str:
    a = asset

    def app(app_id: str, icon: str, label: str, *, extra_cls: str = "", btn_id: str = "") -> str:
        icon_cls = " desktop-app-icon-tiktok" if app_id == "tiktok" else ""
        return (
            f'<button type="button" class="desktop-app{extra_cls}"{btn_id} data-desktop-app="{app_id}">'
            f'<span class="desktop-app-icon{icon_cls}">'
            f'<img class="desktop-app-icon-img" src="{a(f"images/{icon}")}" alt="" />'
            f"</span>"
            f'<span class="desktop-app-label">{label}</span></button>'
        )

    buttons = [
        app("gmail", "desktop_app_gmail.png", "Gmail"),
        app("google", "desktop_app_google.png", "Google"),
        app("google-maps", "desktop_app_google_maps.png", "Google Maps"),
        app("youtube", "desktop_app_youtube.png", "YouTube"),
        app("tiktok", "tiktok_lite_icon.png", "TikTok Lite", extra_cls=" desktop-app-tiktok", btn_id=' id="desktopTikTokBtn"'),
        app("instagram", "desktop_app_instagram.png", "Instagram"),
        app("whatsapp", "desktop_app_whatsapp.png", "WhatsApp"),
        app("x", "desktop_app_x.png", "X"),
    ]
    return (
        f'<div id="layer-desktop" class="flow-layer flow-layer-desktop" data-route="desktop" aria-hidden="true">'
        f'<div class="desktop-wallpaper" aria-hidden="true"></div>'
        f'<div class="desktop-app-grid" aria-label="Desktop apps">{"".join(buttons)}</div>'
        f"</div>"
    )


def feed_html() -> str:
    a = asset
    nav = (
        nav_lite_cell("Home", f"{SL3401}/feed_home_active.svg", active=True)
        + nav_lite_cell("Explore", f"{SL3401}/feed_explore_inactive.svg")
        + feed_create_btn()
        + nav_lite_cell("Inbox", f"{SL3401}/feed_inbox_inactive.svg", attrs='id="feedInboxBtn"')
        + nav_lite_cell("Me", f"{SL3401}/feed_me_inactive.svg")
    )
    return f"""
      <div id="layer-feed" class="flow-layer flow-layer-feed" data-route="feed">
        <div class="feed-video">
          <video id="feedVideo" class="feed-video-media" src="{a('video/feed_video_01.mp4')}" autoplay muted loop playsinline disablePictureInPicture preload="auto" aria-hidden="true"></video>
        </div>
        <div class="feed-gradient"></div>
        <div class="feed-top-bar">
          <button class="feed-icon-btn" type="button" aria-label="Live">
            <img class="feed-icon-template" src="{a('icons/feed/icon_live_entrance.png')}" width="24" height="24" alt="" /></button>
          <div class="feed-tabs">
            <button type="button" class="feed-tab" data-feed-tab="following"><span>Following</span><span class="feed-tab-badge"></span></button>
            <button type="button" class="feed-tab" data-feed-tab="friends"><span>Friends</span></button>
            <button type="button" class="feed-tab active" data-feed-tab="foryou"><span>For You</span></button>
          </div>
          <button class="feed-icon-btn" type="button" aria-label="Search">
            <img class="feed-icon-template" src="{a('icons/feed/icon_magnifying_glass.png')}" width="24" height="24" alt="" /></button>
        </div>
        <div class="feed-interaction">
          <div class="feed-avatar-section">
            <div class="feed-avatar-wrap">
              <div class="feed-avatar-ring"><img class="feed-avatar" src="{a('images/feed_avatar.png')}" alt="" /></div>
              <img class="feed-follow" src="{a('icons/feed_follow.svg')}?v=2" width="28" height="20" alt="" /></div></div>
          <div class="feed-action feed-action-like"><img class="feed-action-icon" src="{a('icons/feed/icon_color_like_shadow.png')}" width="32" height="32" alt="" /><span class="feed-action-label">991K</span></div>
          <div class="feed-action feed-action-comment"><img class="feed-action-icon" src="{a('icons/feed/icon_color_comment_shadow.png')}" width="32" height="32" alt="" /><span class="feed-action-label">3456</span></div>
          <div class="feed-action feed-action-share"><img class="feed-action-icon" src="{a('icons/feed/icon_color_share_shadow.png')}" width="32" height="32" alt="" /><span class="feed-action-label">1256</span></div>
          <div class="feed-music-section"><img class="feed-music-disk" src="{a('images/feed_music_disk.png')}" width="40" height="40" alt="" /></div>
        </div>
        <div class="feed-info">
          <div class="feed-username">jennybush</div>
          <div class="feed-caption">Beautiful sunset vibes 🌅 #fyp</div>
          <div class="feed-translation">See translation</div>
          <div class="feed-sound-row">
            <img class="feed-icon-template" src="{a('icons/feed/icon_music_note_s.png')}" width="16" height="16" alt="" />
            <span>original sound - jennybush</span></div>
        </div>
        <div class="feed-bottom-nav" data-name="Bottom Nav Bar" data-figma="3391:30629">{nav}</div>
      </div>"""


def inbox_layer_html(variant: str = "") -> str:
    a = asset
    nav = f"""
        <div class="inbox-navbar" data-name="Navigation Bar" data-figma="3391:29146">
          <button type="button" class="inbox-nav-tap" aria-label="New"><img src="{a('inbox/inbox_nav_circle_plus.png')}" width="24" height="24" alt="" /></button>
          <div class="inbox-nav-center">
            <span class="inbox-nav-title">Inbox</span>
            <span class="inbox-account-chip">
              <img src="{a('inbox/inbox_account_status_dot.png')}" width="8" height="8" alt="" />
              <img src="{a('inbox/inbox_account_chevron.png')}" width="8" height="8" alt="" /></span></div>
          <div class="inbox-nav-tap-spacer"></div></div>"""
    bottom_nav = f"""
        <div class="inbox-bottom-nav" data-name="Bottom Nav Bar" data-figma="3391:30629">
          {nav_lite_cell("Home", f"{SL3401}/inbox_home_inactive.svg", attrs='data-inbox-nav="home"')}
          {nav_lite_cell("Explore", f"{SL3401}/inbox_explore_inactive.svg")}
          {feed_create_btn(btn_id="", variant="inbox")}
          {nav_lite_cell("Inbox", f"{SL3401}/inbox_inbox_active.svg", active=True, attrs='data-inbox-nav="inbox"')}
          {nav_lite_cell("Me", f"{SL3401}/inbox_me_inactive.svg")}
        </div>"""
    return f"""
      <div id="layer-inbox" class="flow-layer flow-layer-inbox" data-route="inbox">
        {nav}
        <div id="inboxContent" class="inbox-content">
          <div id="inboxRevealRoot" class="inbox-reveal-root">
            <div id="storyRefreshIndicator" class="story-refresh-indicator" aria-hidden="true">
              <div class="tux-dualball" data-refresh-ball>
                <span class="tux-dualball-dot tux-dualball-dot-a"></span>
                <span class="tux-dualball-dot tux-dualball-dot-b"></span>
              </div>
            </div>
            <div id="storyRevealSlot" class="story-reveal-slot">{skylight_row_html(variant)}</div>
            <div id="storyReleaseHint" class="story-release-hint" aria-hidden="true">Pull down to view story</div>
            <div id="inboxListLayer" class="inbox-list-layer">
              <div id="inboxScroll" class="inbox-scroll">{inbox_cells_html()}</div>
            </div>
          </div>
        </div>
        {bottom_nav}
      </div>"""


def album_html() -> str:
    """Abulm V1 album (Effect Loading 1.2s) — simplified from effect-loading-1s."""
    a = asset
    presets = [
        "album_v4_effect_1.png",
        "album_v4_effect_2.png",
        "album_v4_effect_ai.png",
        "album_v4_effect_4.png",
        "album_v4_effect_5.png",
    ]
    preset_btns = "".join(
        f'<button class="effect-preset-btn" type="button" data-index="{i}" disabled>'
        f'<img class="effect-preset-placeholder" src="{a("images/album_v4_effect_cover_placeholder.svg")}" alt="" />'
        f'<img class="effect-preset-img" src="{a("images/" + name)}" alt="" /></button>'
        for i, name in enumerate(presets)
    )
    tiles = [
        ("album_v4_tile_1.png", "2.7 MB", "00:07"),
        ("album_v4_tile_2.png", "2.7 MB", None),
        ("album_v4_tile_3.png", "2.7 MB", None),
        ("album_v4_tile_4.png", "2.7 MB", None),
        ("album_v4_tile_5.png", "2.7 MB", "00:07"),
        ("album_v4_tile_6.png", "2.7 MB", "00:07"),
        ("album_v4_tile_7.png", "2.7 MB", None),
        ("album_v4_tile_8.png", "2.7 MB", None),
        ("album_v4_tile_9.png", "2.7 MB", "00:07"),
        ("album_v4_tile_10.png", "2.7 MB", None),
        ("album_v4_tile_11.png", "2.7 MB", None),
        ("album_v4_tile_12.png", None, None),
    ]
    grid = ""
    for i in range(0, len(tiles), 3):
        row = tiles[i : i + 3]
        cells = ""
        for name, size, dur in row:
            size_h = f'<span class="tile-size">{size}</span>' if size else ""
            dur_h = f'<span class="tile-duration">{dur}</span>' if dur else ""
            cells += f'<div class="grid-tile"><img src="{a("images/" + name)}" alt="" />{size_h}{dur_h}</div>'
        grid += f'<div class="grid-row">{cells}</div>'
    return f"""
      <div id="albumEmergence" class="album-emergence">
        <div id="screen-album" class="screen screen-album">
          <div class="album-root">
            <div class="album-nav">
              <button id="albumCloseBtn" class="nav-tap" type="button"><img src="{a('icons/album_v4_ic_close.svg')}" width="24" height="24" alt="" /></button>
              <div class="nav-spacer"></div>
              <div class="nav-trailing">
                <div id="navDrafts" class="nav-layer"><div class="drafts-btn"><span>12 Drafts</span></div></div>
                <div id="navCamera" class="nav-layer"><button id="navCameraBtn" class="nav-tap" type="button"><img src="{a('icons/album_v4_ic_nav_camera.svg')}" width="24" height="24" alt="" /></button></div>
              </div>
              <div id="navTitle" class="nav-title" aria-hidden="true"><span>Recents</span><img src="{a('icons/album_v4_ic_chevron_down.svg')}" width="16" height="16" alt="" /></div>
            </div>
            <div class="album-scroll-wrap">
              <div id="tabPinnedOverlay" class="tabs-pinned"><div class="tabs-row"><button class="tab-item active" data-tab="0">All</button><button class="tab-item" data-tab="1">Photos</button><button class="tab-item" data-tab="2">Videos</button></div><div class="tab-sep"></div></div>
              <div id="albumScroll" class="album-scroll">
                <div id="cameraHeader" class="camera-header camera-header--v1" style="height:104px">
                  <div class="entry-row-v1"><button class="entry-btn entry-btn--camera" type="button"><img src="{a('icons/album_v4_ic_camera.svg')}" width="28" height="28" alt="" /><span>Camera</span></button>
                  <button id="effectsEntryBtn" class="entry-btn entry-btn--effect" type="button"><img src="{a('icons/album_v4_ic_effects.svg')}" width="26" height="26" alt="" /><span>Effect</span></button></div>
                </div>
                <div class="recents recents-v48"><div class="recents-left"><span>Recents</span><img src="{a('icons/album_v4_ic_chevron_down.svg')}" width="16" height="16" alt="" /></div>
                <div class="storage-pill"><span>5.9 MB</span><div class="eye-wrap"><img src="{a('icons/album_v4_ic_eye.svg')}" width="16" height="16" alt="" /></div></div></div>
                <div id="tabsSection" class="tabs-section"><div class="tabs-row"><button class="tab-item active" data-tab="0">All</button><button class="tab-item" data-tab="1">Photos</button><button class="tab-item" data-tab="2">Videos</button></div><div class="tab-sep"></div></div>
                <div class="grid-wrap">{grid}</div>
              </div>
            </div>
            <div class="select-bar"><div class="select-row"><div class="checkbox-outer"><div class="checkbox-inner"></div></div><span>Select multiple</span></div></div>
          </div>
        </div>
      </div>"""


def story_add_html() -> str:
    a = asset
    tile_html = [
        f'<div class="story-add-tile"><img src="{a(f"figma/{name}")}" alt="" />'
        + ('<span class="story-add-duration">00:07</span>' if has_dur else "")
        + "</div>"
        for name, has_dur in STORY_ADD_GRID
    ]
    rows = "".join(
        f'<div class="story-add-grid-row">{"".join(tile_html[i : i + 3])}</div>'
        for i in range(0, 12, 3)
    )
    return f"""
      <div id="storyAddSheet" class="story-add-sheet" hidden>
        <div class="story-add-nav">
          <button type="button" id="storyAddClose" class="story-add-nav-btn" aria-label="Close">
            <img src="{a('figma/story_preview_close.svg')}" width="24" height="24" alt="" />
          </button>
          <span class="story-add-title">Add to Story</span>
          <button type="button" id="storyAddCameraNav" class="story-add-nav-camera" hidden aria-label="Camera">
            <img src="{a('figma/story_add_camera.svg')}" width="24" height="24" alt="" />
          </button>
        </div>
        <div id="storyAddScroll" class="story-add-scroll">
          <div class="story-add-camera-section">
            <div class="story-add-camera-card">
              <img src="{a('figma/story_add_camera.svg')}" width="28" height="28" alt="" />
              <span>Camera</span>
            </div>
          </div>
          <div class="story-add-recents">
            <span>Recents</span>
            <img src="{a('figma/story_add_chevron.svg')}" width="16" height="16" alt="" />
          </div>
          <div class="story-add-tabs">
            <button class="active" type="button">All</button>
            <button type="button">Photos</button>
            <button type="button">Videos</button>
          </div>
          <div class="story-add-grid">{rows}</div>
        </div>
        <div class="story-add-footer">
          <div class="story-add-select">
            <img class="story-add-checkbox" src="{a('figma/story_add_checkbox.svg')}" width="24" height="24" alt="" />
            <span>Multiple select</span>
          </div>
          <div class="story-add-home-spacer" aria-hidden="true"></div>
        </div>
      </div>"""


def story_preview_html() -> str:
    a = asset
    return f"""
      <div id="storyPreview" class="story-preview" hidden>
        <div class="story-preview-screen">
          <div id="storyPhotoWrap" class="story-photo-wrap">
            <img id="storyPhoto" src="" alt="" />
            <img class="story-photo-overlay-top" src="{a('figma/story_preview_top_overlay.svg')}" alt="" />
            <div class="story-tap-zones">
              <button type="button" id="storyTapPrev" class="story-tap-prev" aria-label="Previous"></button>
              <button type="button" id="storyTapNext" class="story-tap-next" aria-label="Next"></button>
            </div>
          </div>
          <div class="story-preview-top-ui">
            <div id="storyProgressRow" class="story-progress-row"></div>
            <div class="story-preview-topbar">
              <div class="story-preview-header">
                <img id="storyPreviewAvatar" class="story-preview-avatar" src="" alt="" />
                <div class="story-preview-meta">
                  <span id="storyPreviewName"></span><span id="storyPreviewTime">· 3h ago</span>
                </div>
              </div>
              <div class="story-preview-actions">
                <img class="story-preview-camera" src="{a('figma/story_preview_camera.svg')}" width="24" height="24" alt="" />
                <button type="button" id="storyPreviewClose" class="story-preview-close" aria-label="Close">
                  <img src="{a('figma/story_preview_close.svg')}" width="24" height="24" alt="" />
                </button>
              </div>
            </div>
          </div>
        </div>
        <div class="story-preview-interaction" data-name="Interaction" data-figma="3430:6689">
          <div class="story-dm-row">
            <div class="story-message-bubble" data-figma="3430:6692">
              <span class="story-message-text">Message....</span>
              <div class="story-message-emojis" aria-hidden="true" data-figma="3430:6695">
                <img src="{a('figma/story_dm_emoji_1.png')}" width="28" height="28" alt="" />
                <img src="{a('figma/story_dm_emoji_2.png')}" width="28" height="28" alt="" />
                <img src="{a('figma/story_dm_emoji_3.png')}" width="28" height="28" alt="" />
              </div>
            </div>
            <div class="story-preview-actions-row" data-figma="3430:6701">
              <img src="{a('figma/story_preview_like.png')}" width="28" height="28" alt="" />
              <img src="{a('figma/story_preview_share.png')}" width="28" height="28" alt="" />
            </div>
          </div>
        </div>
      </div>"""


CSS_VERSIONS = {
    "v1": 50,
    "v2": 35,
    "v3": 50,
    "v4": 33,
}


def variant_html(vid: str, cfg: dict) -> str:
    config = {**MOTION, **cfg, "id": vid, "startOnFeed": True}
    a = asset
    css_version = CSS_VERSIONS.get(vid, 44)
    desktop_layer = desktop_html() if vid == "v3" else ""
    embed_cls = f"variant-embed variant-{vid}" if vid == "v3" else "variant-embed"
    return f"""<!DOCTYPE html>
<html class="{embed_cls}" lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=360, initial-scale=1, maximum-scale=1, user-scalable=no" />
  <meta name="theme-color" content="#000000" />
  <title>{cfg['label']}</title>
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;600;700&display=swap" rel="stylesheet" />
  <link rel="stylesheet" href="../../shared/skylight.css?v={css_version}" />
</head>
<body class="{embed_cls}">
  <div class="phone" id="phone">
    <div class="status-bar" id="statusBar">
      <div class="status-time-wrap"><span class="status-time">8:00</span></div>
      <div class="status-icons" aria-hidden="true">
        <div class="status-network">
          <img class="status-wifi" src="{a('icons/status_wifi.svg')}" width="16" height="16" alt="" />
          <img class="status-signal" src="{a('icons/status_signal.svg')}" width="16" height="16" alt="" />
        </div>
        <img class="status-battery" src="{a('icons/status_battery.svg')}" width="16" height="16" alt="" />
      </div>
    </div>
    <div id="flowRoot" class="flow-root">
      {desktop_layer}
      {feed_html()}
      {inbox_layer_html(vid)}
    </div>
    {story_add_html()}
    {story_preview_html()}
    <div class="system-home-indicator" data-name="System/Home Indicator" data-figma="3285:55821" aria-hidden="true"><div class="system-home-indicator-handle"></div></div>
  </div>
  <script>window.__SKYLIGHT_VARIANT__ = {json.dumps(config, ensure_ascii=False)};
window.__STORY_PREVIEWS__ = {json.dumps(STORY_PREVIEWS, ensure_ascii=False)};</script>
  <script src="../../shared/skylight-core.js?v=125"></script>
</body>
</html>"""


def copy_assets() -> None:
    dst_icons = SHARED / "assets/icons"
    dst_images = SHARED / "assets/images"
    dst_inbox = SHARED / "assets/inbox"
    dst_story = SHARED / "assets/story"
    dst_video = SHARED / "assets/video"
    for d in (dst_icons, dst_images, dst_inbox, dst_story, dst_video):
        d.mkdir(parents=True, exist_ok=True)

    if EFFECT.is_dir():
        src_assets = EFFECT / "assets"
        if (src_assets / "icons").is_dir():
            shutil.copytree(src_assets / "icons", dst_icons, dirs_exist_ok=True)
        if (src_assets / "images").is_dir():
            shutil.copytree(src_assets / "images", dst_images, dirs_exist_ok=True)

    for pattern in ("inbox_*.png", "story_*.png", "story_add_tile_*.jpg"):
        for f in (RES / "drawable").glob(pattern):
            dest = dst_inbox if f.name.startswith("inbox_") else dst_story
            shutil.copy2(f, dest / f.name)

    raw_video = RES / "raw" / "feed_video_01.mp4"
    if raw_video.is_file():
        shutil.copy2(raw_video, dst_video / "feed_video_01.mp4")

    if ALBUM.is_dir():
        shared_assets = ALBUM / "shared/assets"
        if shared_assets.is_dir():
            for sub in ("icons", "images"):
                src = shared_assets / sub
                if src.is_dir():
                    shutil.copytree(src, SHARED / "assets" / sub, dirs_exist_ok=True)

    figma_dir = SHARED / "assets" / "figma"
    figma_dir.mkdir(parents=True, exist_ok=True)

    for name in ("story_dm_emoji_1.png", "story_dm_emoji_2.png", "story_dm_emoji_3.png"):
        src = figma_dir / name
        if src.is_file():
            shutil.copy2(src, RES / "drawable" / name)

    nodpi = RES / "drawable-nodpi"
    if nodpi.is_dir():
        for f in nodpi.glob("*"):
            name = f.name.lower()
            if "story" in name or "multi_photo" in name:
                dest = dst_story if "story" in name else dst_images
                shutil.copy2(f, dest / f.name)

    dismiss_svg = dst_inbox / "inbox_dismiss_x.svg"
    if not dismiss_svg.is_file():
        bundled = OUT / "shared/assets/inbox/inbox_dismiss_x.svg"
        if bundled.is_file():
            shutil.copy2(bundled, dismiss_svg)


def sync_android_reference() -> None:
    """Mirror Android Story Skylight source + res assets alongside the web demo."""
    ref = OUT / "android-reference"
    pkg = "com/example/designlab/playgrounds"
    kotlin_root = ref / "kotlin" / pkg
    kotlin_root.mkdir(parents=True, exist_ok=True)

    playground_java = ROOT / "playgrounds/src/main/java/com/example/designlab/playgrounds"
    for sub in ("figmainbox", "feed"):
        src_dir = playground_java / sub
        if not src_dir.is_dir():
            continue
        dst_dir = kotlin_root / sub
        dst_dir.mkdir(parents=True, exist_ok=True)
        for f in src_dir.glob("*.kt"):
            shutil.copy2(f, dst_dir / f.name)

    abulm_v1 = playground_java / "abulmv1" / "AbulmV1Screen.kt"
    if abulm_v1.is_file():
        dst = kotlin_root / "abulmv1"
        dst.mkdir(parents=True, exist_ok=True)
        shutil.copy2(abulm_v1, dst / abulm_v1.name)

    main_activity = ROOT / "app/src/main/java/com/example/designlab/MainActivity.kt"
    if main_activity.is_file():
        app_dst = ref / "kotlin/com/example/designlab"
        app_dst.mkdir(parents=True, exist_ok=True)
        shutil.copy2(main_activity, app_dst / "MainActivity.kt")

    res_drawable = ref / "res/drawable"
    res_nodpi = ref / "res/drawable-nodpi"
    res_values = ref / "res/values"
    res_raw = ref / "res/raw"
    for d in (res_drawable, res_nodpi, res_values, res_raw):
        d.mkdir(parents=True, exist_ok=True)

    for pattern in ("inbox_*.png", "story_*.png", "story_add_tile_*.jpg"):
        for f in (RES / "drawable").glob(pattern):
            shutil.copy2(f, res_drawable / f.name)

    nodpi = RES / "drawable-nodpi"
    if nodpi.is_dir():
        for f in nodpi.glob("*"):
            if "story" in f.name.lower() or "multi_photo" in f.name.lower():
                shutil.copy2(f, res_nodpi / f.name)

    colors_inbox = RES / "values/colors_inbox.xml"
    if colors_inbox.is_file():
        shutil.copy2(colors_inbox, res_values / "colors_inbox.xml")

    raw_video = RES / "raw/feed_video_01.mp4"
    if raw_video.is_file():
        shutil.copy2(raw_video, res_raw / "feed_video_01.mp4")

    readme = ref / "README.md"
    readme.write_text(
        "# Android reference\n\n"
        "Mirrored from DesignLab Android for Story Skylight V1–V4.\n\n"
        "- `kotlin/` — `figmainbox`, `feed`, `AbulmV1Screen`, `MainActivity`\n"
        "- `res/drawable/` — inbox + story bitmaps\n"
        "- `res/drawable-nodpi/` — story preview photos\n"
        "- `res/raw/` — feed video\n\n"
        "Web demo assets live under `../shared/assets/` (same files, web paths).\n",
        encoding="utf-8",
    )


def write_preview_shell() -> None:
    preview_html = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Story Skylight V1–V4</title>
  <link rel="stylesheet" href="preview.css?v=39" />
</head>
<body>
  <div id="shell">
    <header class="toolbar" id="toolbar">
      <div class="tool-group">
        <select id="variantSelect" aria-label="实验组"></select>
      </div>
      <div class="tool-group">
        <button type="button" class="icon-btn" id="zoomOutBtn" title="缩小" aria-label="缩小">
          <svg class="toolbar-icon" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
            <path d="M152,112a8,8,0,0,1-8,8H80a8,8,0,0,1,0-16h64A8,8,0,0,1,152,112Zm77.66,117.66a8,8,0,0,1-11.32,0l-50.06-50.07a88.11,88.11,0,1,1,11.31-11.31l50.07,50.06A8,8,0,0,1,229.66,229.66ZM112,184a72,72,0,1,0-72-72A72.08,72.08,0,0,0,112,184Z"></path>
          </svg>
        </button>
        <span class="zoom-label" id="zoomLabel">100%</span>
        <button type="button" class="icon-btn" id="zoomInBtn" title="放大" aria-label="放大">
          <svg class="toolbar-icon" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
            <path d="M152,112a8,8,0,0,1-8,8H120v24a8,8,0,0,1-16,0V120H80a8,8,0,0,1,0-16h24V80a8,8,0,0,1,16,0v24h24A8,8,0,0,1,152,112Zm77.66,117.66a8,8,0,0,1-11.32,0l-50.06-50.07a88.11,88.11,0,1,1,11.31-11.31l50.07,50.06A8,8,0,0,1,229.66,229.66ZM112,184a72,72,0,1,0-72-72A72.08,72.08,0,0,0,112,184Z"></path>
          </svg>
        </button>
        <button type="button" class="icon-btn" id="zoomFitBtn" title="自适应" aria-label="自适应">
          <svg class="toolbar-icon" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
            <path d="M200,80v32a8,8,0,0,1-16,0V88H160a8,8,0,0,1,0-16h32A8,8,0,0,1,200,80ZM96,168H72V144a8,8,0,0,0-16,0v32a8,8,0,0,0,8,8H96a8,8,0,0,0,0-16ZM232,56V200a16,16,0,0,1-16,16H40a16,16,0,0,1-16-16V56A16,16,0,0,1,40,40H216A16,16,0,0,1,232,56ZM216,200V56H40V200H216Z"></path>
          </svg>
        </button>
        <button type="button" class="icon-btn" id="createBorderBtn" title="Create 边框：关闭" aria-label="Create 边框" aria-pressed="false">
          <svg class="toolbar-icon toolbar-icon-outline" viewBox="0 0 256 256" fill="none" stroke="currentColor" stroke-width="18" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="128" cy="128" r="66"></circle>
            <path d="M128 96v64"></path>
            <path d="M96 128h64"></path>
          </svg>
        </button>
        <button type="button" class="icon-btn" id="measureBtn" title="测量" aria-label="测量" aria-pressed="false">
          <svg class="toolbar-icon" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
            <path d="M229.66,218.34l-50.07-50.06a88.11,88.11,0,1,0-11.31,11.31l50.06,50.07a8,8,0,0,0,11.32-11.32ZM40,112a72,72,0,1,1,72,72A72.08,72.08,0,0,1,40,112Zm96,0a8,8,0,0,1-8,8H88a8,8,0,0,1,0-16h40A8,8,0,0,1,136,112Zm32,32a8,8,0,0,1-8,8H88a8,8,0,0,1,0-16h72A8,8,0,0,1,168,144Z"></path>
          </svg>
        </button>
      </div>
      <div class="toolbar-spacer"></div>
      <div class="tool-group">
        <button type="button" class="icon-btn" id="exitBtn" title="退出到桌面" aria-label="退出到桌面" hidden>
          <svg class="toolbar-icon toolbar-icon-outline" viewBox="0 0 256 256" fill="none" stroke="currentColor" stroke-width="18" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="48" y="48" width="160" height="120" rx="16"></rect>
            <path d="M88 208h80"></path>
            <path d="M128 168v40"></path>
            <path d="M88 108h80"></path>
            <path d="M108 88 88 108l20 20"></path>
          </svg>
        </button>
        <button type="button" class="icon-btn" id="demoBtn" title="演示" aria-label="演示">
          <svg class="toolbar-icon toolbar-icon-outline" viewBox="0 0 256 256" fill="none" stroke="currentColor" stroke-width="18" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="40" y="56" width="176" height="112" rx="16"></rect>
            <path d="M104 200h48"></path>
            <path d="M128 168v32"></path>
          </svg>
        </button>
        <button type="button" class="icon-btn" id="reloadBtn" title="重启演示" aria-label="重启">
          <svg class="toolbar-icon" viewBox="0 0 256 256" fill="currentColor" aria-hidden="true">
            <path d="M240,56v48a8,8,0,0,1-8,8H184a8,8,0,0,1,0-16H211.4L184.81,71.64l-.25-.24a80,80,0,1,0-1.67,114.78,8,8,0,0,1,11,11.63A95.44,95.44,0,0,1,128,224h-1.32A96,96,0,1,1,195.75,60L224,85.8V56a8,8,0,1,1,16,0Z"></path>
          </svg>
        </button>
      </div>
    </header>
    <main class="stage" id="stage">
      <div class="stage-caption" aria-hidden="true">
        <span id="variantCaption">V1</span>
        <span>360 × 800</span>
      </div>
      <div id="phone-wrap">
        <iframe id="app-frame" title="Story Skylight preview" src="variants/v1/index.html" scrolling="no" style="pointer-events: none;"></iframe>
      </div>
    </main>
  </div>
  <div id="touch-cursor" aria-hidden="true"></div>
  <div id="measure-highlight" class="measure-highlight" aria-hidden="true"></div>
  <div id="measure-gap-highlight" class="measure-gap-highlight" aria-hidden="true"></div>
  <div id="measure-spacing-guides" class="measure-spacing-guides" aria-hidden="true"></div>
  <div id="measure-panel" class="measure-panel" hidden></div>
  <div id="hoverDestinationPanel" class="hover-destination-panel" hidden aria-live="polite">
    <div class="hover-mini-phone" aria-hidden="true">
      <div class="hover-mini-phone-viewport" id="hoverMiniViewport">
        <iframe id="hoverMiniFrame" title="Hover destination preview" scrolling="no" tabindex="-1"></iframe>
      </div>
    </div>
    <div class="hover-destination-meta">
      <div class="hover-destination-title" id="hoverDestinationTitle"></div>
      <div class="hover-destination-subtitle" id="hoverDestinationSubtitle"></div>
    </div>
  </div>
  <script src="shared/tux-color-resolver.js"></script>
  <script src="shared/tux-typography-resolver.js"></script>
  <script src="preview-measure.js?v=5"></script>
  <script src="preview.js?v=54"></script>
</body>
</html>"""
    (OUT / "preview.html").write_text(preview_html, encoding="utf-8")

    for vid in ("v1", "v2", "v3", "v4"):
        label = vid.upper()
        variant_preview = preview_html.replace(
            '<html lang="zh-CN">',
            f'<html lang="zh-CN" data-default-variant="{vid}">',
        ).replace(
            "<title>Story Skylight V1–V4</title>",
            f"<title>Story Skylight {label}</title>",
        ).replace(
            '<span id="variantCaption">V1</span>',
            f'<span id="variantCaption">{label}</span>',
        ).replace(
            'src="variants/v1/index.html"',
            f'src="variants/{vid}/index.html"',
        )
        (OUT / f"preview-{vid}.html").write_text(variant_preview, encoding="utf-8")

    preview_js_src = (ALBUM / "preview.js").read_text(encoding="utf-8")
    preview_js = preview_js_src.replace(
        "const VARIANTS = [\n    { id: 'v1', label: 'V1', path: 'variants/v1/index.html' },\n    { id: 'v2', label: 'V2', path: 'variants/v2/index.html' },\n    { id: 'v3', label: 'V3', path: 'variants/v3/index.html' },\n  ];",
        "const VARIANTS = [\n    { id: 'v1', label: 'V1', path: 'variants/v1/index.html' },\n    { id: 'v2', label: 'V2', path: 'variants/v2/index.html' },\n    { id: 'v3', label: 'V3', path: 'variants/v3/index.html' },\n    { id: 'v4', label: 'V4', path: 'variants/v4/index.html' },\n  ];",
    ).replace(
        "album:reload",
        "skylight:reload",
    ).replace(
        "    reloadBtn: document.getElementById('reloadBtn'),\n    measureBtn:",
        "    reloadBtn: document.getElementById('reloadBtn'),\n    variantCaption: document.getElementById('variantCaption'),\n    measureBtn:",
    ).replace(
        "    els.variantSelect.value = variantId;\n    updateUrl(variantId);",
        "    els.variantSelect.value = variantId;\n    if (els.variantCaption) els.variantCaption.textContent = meta.label;\n    updateUrl(variantId);",
    ).replace(
        "const PREVIEW_BUILD = '3';",
        "const PREVIEW_BUILD = '156';",
    )
    preview_js_path = OUT / "preview.js"
    if not preview_js_path.is_file():
        (OUT / "preview.js").write_text(preview_js, encoding="utf-8")

    index_html = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Story Skylight V1–V4</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { min-height: 100vh; background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; display: flex; align-items: flex-start; justify-content: center; padding: 32px 16px; }
    .menu { width: min(420px, 100%); background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 8px 24px rgba(0,0,0,.08); }
    .menu-item { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px; color: #161823; text-decoration: none; border-bottom: 1px solid rgba(0,0,0,.06); font-size: 15px; font-weight: 600; }
    .menu-item:last-child { border-bottom: 0; }
    .menu-item:hover { background: #fafafa; }
  </style>
</head>
<body>
  <nav class="menu" aria-label="Story Skylight variants">
    <a class="menu-item" href="preview-v1.html">V1 — Integrated（锁定展开）</a>
    <a class="menu-item" href="preview-v2.html">V2 — Overlay + 链式刷新</a>
    <a class="menu-item" href="preview-v3.html">V3 — Integrated + 链式刷新</a>
    <a class="menu-item" href="preview-v4.html">V4 — Overlay + Release hint</a>
  </nav>
</body>
</html>"""
    (OUT / "index.html").write_text(index_html, encoding="utf-8")


def build_css() -> None:
    feed_css = (EFFECT / "styles.css").read_text(encoding="utf-8") if (EFFECT / "styles.css").is_file() else ""
    inbox_css_path = OUT / "shared" / "skylight-inbox.css"
    inbox_part = inbox_css_path.read_text(encoding="utf-8") if inbox_css_path.is_file() else ""
    (SHARED / "skylight.css").write_text(feed_css + "\n" + inbox_part, encoding="utf-8")


def main() -> None:
    download_figma_assets()
    copy_assets()
    sync_android_reference()
    write_preview_shell()
    build_css()
    manifest = {"variants": list(VARIANTS.keys()), "motion": MOTION}
    (OUT / "variants-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    for vid, cfg in VARIANTS.items():
        vdir = OUT / "variants" / vid
        vdir.mkdir(parents=True, exist_ok=True)
        (vdir / "index.html").write_text(variant_html(vid, cfg), encoding="utf-8")
        (vdir / "config.json").write_text(json.dumps({**MOTION, **cfg}, indent=2), encoding="utf-8")
    print(f"Generated Story Skylight demo at {OUT}")


if __name__ == "__main__":
    main()
