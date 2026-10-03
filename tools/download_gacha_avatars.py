#!/usr/bin/env python3
"""
Download character and weapon portraits from the Wuthering Waves wiki.

The wiki is the only source of portraits that is maintained outside this repo, so
the alternative to this script is a hardcoded list that rots every time Kuro adds a
character. This script discovers the roster from the wiki itself, downloads what it
finds, and skips what is already on disk — so re-running it after a game update
picks up new characters and weapons without touching any code.

Output goes to app/src/main/assets/gacha_avatars/, which the app reads at runtime.
Images are downscaled to 256px and saved as WebP: the wiki serves 560x920 sprites at
100-350KB each, and the app shows them at 44dp, so the full size would cost ~40MB of
APK for no visible difference.

Idempotent by design: a file that already exists is left alone, so a re-run only
fetches what is new or was missing.

Usage:
    python3 tools/download_gacha_avatars.py            # download everything missing
    python3 tools/download_gacha_avatars.py --check    # report only, download nothing
    python3 tools/download_gacha_avatars.py --force    # re-download even if present
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://wutheringwaves.fandom.com/api.php"
USER_AGENT = "WuWaConfig-avatar-downloader/1.0 (on-device config tool)"
OUTPUT_DIR = Path("app/src/main/assets/gacha_avatars")

# Longest edge after downscaling. 256 is ~6x the 44dp the app renders, so the grid
# stays crisp on a high-density screen without shipping full sprites.
MAX_SIZE = 256
WEBP_QUALITY = 82

# The wiki's file naming, which is the only stable handle these images have.
# Verified against the live wiki: characters are "<Name> Full Sprite.png", weapons
# and materials are "Weapon <Name>.png".
CHARACTER_TEMPLATE = "{name} Full Sprite.png"
WEAPON_TEMPLATE = "Weapon {name}.png"

# MediaWiki caps a multi-title query at 50; stay under it.
BATCH = 40


def api(params: dict) -> dict:
    """One API call, with the params the MediaWiki API expects."""
    query = urllib.parse.urlencode({**params, "format": "json", "formatversion": "2"})
    request = urllib.request.Request(f"{API}?{query}", headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def category_members(category: str) -> list[str]:
    """Every non-subcategory member of a category, following pagination."""
    members: list[str] = []
    continue_from = None
    while True:
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": f"Category:{category}",
            "cmlimit": "500",
            "cmnamespace": "0",
        }
        if continue_from:
            params["cmcontinue"] = continue_from
        data = api(params)
        for member in data["query"]["categorymembers"]:
            # Subcategories are nested under "Category:"; the roster pages are not.
            if not member["title"].startswith("Category:"):
                members.append(member["title"])
        continue_from = data.get("continue", {}).get("cmcontinue")
        if not continue_from:
            break
    return members


def image_urls(file_names: list[str]) -> dict[str, str]:
    """Map wiki file name -> direct CDN URL, in batches.

    The CDN path contains a content hash that cannot be derived from the file name,
    so the only way to get a working URL is to ask the API. `Special:FilePath`
    would avoid that but answers 403 to non-browser clients.
    """
    urls: dict[str, str] = {}
    pending = list(file_names)
    while pending:
        batch, pending = pending[:BATCH], pending[BATCH:]
        titles = "|".join(f"File:{name}" for name in batch)
        data = api(
            {
                "action": "query",
                "prop": "imageinfo",
                "iiprop": "url",
                "titles": titles,
            }
        )
        for page in data["query"]["pages"]:
            # A missing file comes back with a negative page id and no imageinfo.
            info = page.get("imageinfo")
            if info:
                urls[page["title"].removeprefix("File:")] = info[0]["url"]
        time.sleep(0.2)
    return urls


def slug(name: str) -> str:
    """A filename-safe form of a character or weapon name."""
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def output_name(name: str, is_weapon: bool) -> str:
    """The asset filename for a roster entry.

    Weapons carry a prefix so a character and a weapon sharing a name cannot
    collide; the app picks the prefix from the record's resourceType.
    """
    return f"weapon_{slug(name)}.webp" if is_weapon else f"{slug(name)}.webp"


def download(url: str, destination: Path) -> bool:
    """Fetch one image, downscale it, and save it as WebP."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            raw = response.read()
        from io import BytesIO

        from PIL import Image

        with Image.open(BytesIO(raw)) as image:
            image = image.convert("RGBA")
            image.thumbnail((MAX_SIZE, MAX_SIZE), Image.LANCZOS)
            destination.parent.mkdir(parents=True, exist_ok=True)
            image.save(destination, "WEBP", quality=WEBP_QUALITY, method=6)
        return True
    except Exception as error:  # noqa: BLE001 - a failed image must not stop the run
        print(f"    ! {destination.name}: {error}")
        return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report only, download nothing")
    parser.add_argument("--force", action="store_true", help="re-download even if present")
    args = parser.parse_args()

    if not args.check:
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # (roster category, is_weapon)
    sources = [("Resonators", False), ("Weapons", True)]

    total = {"new": 0, "skipped": 0, "failed": 0}
    missing: list[str] = []

    for category, is_weapon in sources:
        try:
            roster = category_members(category)
        except Exception as error:  # noqa: BLE001
            print(f"Could not read Category:{category}: {error}", file=sys.stderr)
            return 1
        template = WEAPON_TEMPLATE if is_weapon else CHARACTER_TEMPLATE
        wanted = [template.format(name=name) for name in roster]
        print(f"Category:{category}: {len(roster)} entries")

        # Ask for every URL up front so the download phase is a straight loop.
        try:
            urls = image_urls(wanted)
        except Exception as error:  # noqa: BLE001
            print(f"Could not resolve image URLs: {error}", file=sys.stderr)
            return 1

        for name in roster:
            file_name = template.format(name=name)
            if file_name not in urls:
                missing.append(file_name)
                continue
            destination = OUTPUT_DIR / output_name(name, is_weapon)
            if destination.exists() and not args.force:
                total["skipped"] += 1
                continue
            if args.check:
                total["new"] += 1
                continue
            if download(urls[file_name], destination):
                total["new"] += 1
            else:
                total["failed"] += 1

    print()
    if args.check:
        print(f"Would download: {total['new']}")
    else:
        print(f"Downloaded: {total['new']}  Skipped: {total['skipped']}  Failed: {total['failed']}")
    if missing:
        print(f"No wiki file found for {len(missing)} entries, e.g.:")
        for name in missing[:10]:
            print(f"  - {name}")
    return 0 if total["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
