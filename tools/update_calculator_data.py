#!/usr/bin/env python3
"""Fetches material cost data from Wuthering Waves Fandom Wiki and generates
assets/config/calculator_materials.json.

Run manually:
    python3 tools/update_calculator_data.py

The script can be called from CI to keep the data fresh; it only rewrites the
asset when the fetched data differs from the existing file.
"""
import json
import re
import sys
import urllib.request
import urllib.parse
from pathlib import Path

API = "https://wutheringwaves.fandom.com/api.php"
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "config" / "calculator_materials.json"
# The hosted copy the app fetches at runtime. Committed to the repo and served
# (free, over HTTPS) by raw.githubusercontent.com. The GitHub Action refreshes
# this on a schedule; the app picks up changes via ETag without an app update.
PUBLISH = Path(__file__).resolve().parent / "data" / "calculator_materials.json"

CHAR_ASC_DATA = "Module:Resonator_Ascensions_and_Stats/data"
SKILL_DATA = "Module:Skill_Upgrade/data"
WEAPON_DATA = "Module:Weapon_Ascensions_and_Stats/data"

# The *code* modules, which is where the fixed cost tables live. Distinct from
# the /data modules above: those map each character to its material names, while
# these hold the shared per-phase and per-level numbers.
CHAR_ASC_MODULE = "Module:Character Ascensions and Stats"
SKILL_MODULE = "Module:Skill Upgrade"
WEAPON_MODULE = "Module:Weapon Ascensions and Stats"

def fetch_wikitext(title: str) -> str:
    url = f"{API}?action=parse&page={urllib.parse.quote(title)}&format=json&prop=wikitext"
    req = urllib.request.Request(url, headers={"User-Agent": "WuWaConfig-MaterialUpdater/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.load(resp)
    return data["parse"]["wikitext"]["*"]


def fetch_source_timestamp() -> str:
    """The wiki's last-edit time across every source page, as an ISO-8601 UTC string.

    Embedded in the asset as `sourceTimestamp` so the UI can show "data from …"
    without the app ever querying the wiki. The GitHub Action compares this to
    decide whether a regeneration is needed, so an unrelated page edit does not
    force a no-op commit.
    """
    titles = "|".join(
        [CHAR_ASC_DATA, SKILL_DATA, WEAPON_DATA, CHAR_ASC_MODULE, SKILL_MODULE, WEAPON_MODULE]
    )
    url = (
        f"{API}?action=query&format=json&prop=revisions"
        f"&rvprop=timestamp&titles={urllib.parse.quote(titles)}"
    )
    req = urllib.request.Request(url, headers={"User-Agent": "WuWaConfig-MaterialUpdater/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.load(resp)
    latest = ""
    for page in data.get("query", {}).get("pages", {}).values():
        for rev in page.get("revisions", []):
            ts = rev.get("timestamp", "")
            if ts > latest:
                latest = ts
    return latest


# ---------------------------------------------------------------------------
# Cost-table scraping
#
# The five fixed cost tables (character ascension, the three Forte tables, and
# weapon ascension per rarity) used to be hand-copied constants in this file.
# That is the exact failure mode AGENTS.md calls out for GameProfile: a stale
# hardcoded value fails *silently*, because the numbers stay plausible and no
# test can tell a transcribed table from a correct one.
#
# It had already gone wrong. The hand-copied character ascension table gave
# phase 1 a `local` (Local Speciality) cost of 4; the wiki's own row is
# `{ ['Shell Credit'] = 5000, [com[1]] = 4 }`, which has no local at all. Every
# character therefore overstated Local Speciality by 4 across a full 0->6
# ascension — a visible 64 where the wiki's own running total says 60. Scraping
# the Lua removes the possibility rather than fixing the instance.
# ---------------------------------------------------------------------------

# A row of a cost table, as written by the wiki. Keys are either a bare
# identifier (`Credit`, `wsm1`, `boss`) or a bracketed expression naming a
# material pool. The pool expression is what carries the meaning:
# `[com[3]] = 4` is "4 of this character's tier-3 common drop", and
# `[weapon_data.ascension[2]] = 8` is "8 of this weapon's tier-2 ascension
# material". Reducing those to plain names is the whole job of the parsers.
_ROW_RE = re.compile(r"\{\s*(?P<body>[^{}]*?)\s*\}\s*,?\s*(?:--(?P<comment>.*))?$")
_BRACKET_NUM_RE = re.compile(r"\[\s*(?P<pool>[A-Za-z_.\[\]0-9]+)\s*\]\s*=\s*(?P<value>\d+)")
_BRACKET_BARE_RE = re.compile(r"\[\s*'(?P<name>[^']+)'\s*\]\s*=\s*(?P<value>\d+)")
_BARE_NUM_RE = re.compile(r"(?P<name>Credit|wsm[1-4]|dwsm[1-4]|boss)\s*=\s*(?P<value>\d+)")
_RANK_RE = re.compile(r"need\s+Rank\.(\d+)")


def _pool_to_field(pool: str) -> str | None:
    """Maps a Lua pool expression onto one of our JSON field names.

    Returns None for anything unrecognised so an upstream rename degrades to
    "field dropped" rather than silently landing the cost in the wrong bucket.
    """
    pool = pool.strip()
    # The pool expression carries its own slot: `com[3]` is the tier-3 drop, so
    # split the trailing index off before matching the base name. Comparing the
    # whole expression against the bare name silently matches nothing, which
    # zeroes every common drop in the table rather than raising.
    index = re.search(r"\[(\d)\]$", pool)
    base = pool[: index.start()] if index else pool
    slot = index.group(1) if index else None
    if base == "locspec":
        return "local"
    if base == "boss":
        return "boss"
    if base == "com" and slot:
        return f"common{slot}"
    if base == "weapon_data.common" and slot:
        return f"common{slot}"
    if base == "weapon_data.ascension" and slot:
        return f"ascension{slot}"
    return None


def _row_fields(body: str) -> dict[str, int]:
    """Pulls every `field = quantity` pair out of one cost row."""
    fields: dict[str, int] = {}
    for match in _BRACKET_NUM_RE.finditer(body):
        field = _pool_to_field(match.group("pool"))
        if field:
            fields[field] = int(match.group("value"))
    for match in _BRACKET_BARE_RE.finditer(body):
        if match.group("name") == "Shell Credit":
            fields["shellCredit"] = int(match.group("value"))
    for match in _BARE_NUM_RE.finditer(body):
        name = match.group("name")
        field = {"Credit": "credit"}.get(name, name)
        fields[field] = int(match.group("value"))
    return fields


def _block_after(text: str, anchor: str) -> str:
    """Returns the brace-balanced table that *starts with* `anchor`.

    Brace counting rather than a regex, because these tables nest: the weapon
    rarity branches hold a list of rows inside a function, and a non-greedy
    `.*?` to the first `}` stops one row early.

    The anchor must include the opening `{`. Matching from "the next brace after
    this text" instead looks equivalent and is not: given a *function* anchor it
    silently returns the first inner table rather than the function body, which
    for the weapon module meant only the rarity-5 branch was ever parsed.
    """
    start = text.index(anchor) + len(anchor) - 1
    assert text[start] == "{", f"anchor {anchor!r} must end at its opening brace"
    open_at = start
    depth = 0
    for pos in range(open_at, len(text)):
        if text[pos] == "{":
            depth += 1
        elif text[pos] == "}":
            depth -= 1
            if depth == 0:
                return text[open_at + 1 : pos]
    raise ValueError(f"unbalanced braces after {anchor!r}")


def _rows(body: str) -> list[tuple[dict[str, int], int | None]]:
    """Parses each top-level `{ ... }` row, keeping any trailing `--` comment.

    The comment matters: the wiki encodes each row's ascension-rank gate there
    (`-- 1 to 2, need Rank.2`) and nowhere else. Rows without a gate yield None.
    """
    parsed = []
    pos = 0
    while True:
        open_at = body.find("{", pos)
        if open_at < 0:
            return parsed
        depth = 0
        for end in range(open_at, len(body)):
            if body[end] == "{":
                depth += 1
            elif body[end] == "}":
                depth -= 1
                if depth == 0:
                    break
        else:
            return parsed
        rest = body[end + 1 :]
        # Only the *first* line after the row belongs to this row's comment.
        # Splitting the whole remainder on "--" would swallow every later row's
        # comment into this one, so an ungated row silently inherited the next
        # row's rank — which is how "2 to 3" ended up gated at Rank.3.
        first_line = rest.split("\n", 1)[0]
        comment = first_line.split("--", 1)[1].strip() if "--" in first_line else None
        rank = _RANK_RE.search(comment) if comment else None
        parsed.append((_row_fields(body[open_at + 1 : end]), int(rank.group(1)) if rank else None))
        pos = end + 1


def parse_character_ascension(lua: str) -> list[dict]:
    rows = []
    for index, (fields, _) in enumerate(_rows(_block_after(lua, "local asc_costs = {")), start=1):
        rows.append({"phase": index, **{k: fields.get(k, 0) for k in ("shellCredit", "local", "common1", "common2", "common3", "common4", "boss")}})
    return rows


def parse_skill_costs(lua: str) -> dict[str, list[dict]]:
    """Parses `main5`, `inherent` and `statBonus` out of Module:Skill Upgrade.

    Each row also carries `unlockRank`, the ascension rank that gates it. That
    value only exists as a Lua comment, but it is load-bearing: the two
    secondary tables unlock on ascension rather than on the skill slider, so a
    calculator that ignores it under-reports a full character by half.
    """
    cost = _block_after(lua, "local cost = {")
    tables = {"skillMainCosts": "main5", "skillInherentCosts": "inherent", "skillStatBonusCosts": "statBonus"}
    out: dict[str, list[dict]] = {}
    for key, name in tables.items():
        rows = []
        for index, (fields, rank) in enumerate(_rows(_block_after(cost, f"['{name}'] = {{")), start=1):
            rows.append(
                {
                    "level": index,
                    **{k: fields.get(k, 0) for k in ("credit", "wsm1", "wsm2", "wsm3", "wsm4", "dwsm1", "dwsm2", "dwsm3", "dwsm4", "boss")},
                    **({"unlockRank": rank} if rank is not None else {}),
                }
            )
        out[key] = rows
    return out


def parse_weapon_ascension(lua: str) -> dict[str, list[dict]]:
    by_rarity = {}
    for rarity in (5, 4, 3, 2, 1):
        # Lua allows `then <statement>` with no block braces, so the branch
        # cannot be delimited by its own braces — anchor on the `return {` that
        # follows the `if` instead.
        guard = lua.index(f"if (weapon_data.rarity == {rarity}) then")
        rows = []
        for index, (fields, _) in enumerate(_rows(_block_after(lua[guard:], "return {")), start=1):
            rows.append({"phase": index, **{k: fields.get(k, 0) for k in ("shellCredit", "ascension1", "ascension2", "ascension3", "ascension4", "common1", "common2", "common3", "common4")}})
        by_rarity[str(rarity)] = rows
    return by_rarity


# A Lua string literal: single-quoted or double-quoted, allowing an escaped quote
# of the same kind (`\'` inside '...'). That escape is what the wiki writes for
# names like `Loong's Pearl` and `Sentinel's Dagger`.
#
# Two explicit alternatives rather than a named group plus a `(?!\k<q>)` guard:
# Python's `re` has no backreference inside a lookahead. The opening quote is
# consumed *before* the body loop — putting it inside the loop lets the engine
# match a lone `'` and stop, which silently emptied every material list.
_STRING_BODY = r"'(?:\\.|[^'\\])*'|\"(?:\\.|[^\"\\])*\""
_LUA_STRING_RE = re.compile(_STRING_BODY, re.DOTALL)
# Captures the body between the quotes. Must be escape-aware for the same reason
# as _STRING_BODY — the naive `'([^']*)'` form stops at the escaped quote in
# `Loong\'s Pearl` and fails the fullmatch, which surfaced as a null material.
_STRING_RE = re.compile(r"'(?:\\.|[^'\\])*'\s*$|^\s*\"(?:\\.|[^\"\\])*\"")


def _unescape(value: str) -> str:
    """Turn a Lua literal body into its real text."""
    return (
        value.replace("\\'", "'")
        .replace('\\"', '"')
        .replace("\\\\", "\\")
    )


def _strip_quotes(token: str) -> str | None:
    text = token.strip()
    match = _STRING_RE.fullmatch(text)
    if not match:
        return None
    # Drop the surrounding quotes, then resolve escapes.
    return _unescape(text[1:-1])


def _string_list(raw: str) -> list[str]:
    """Every quoted string in `raw`, in order.

    Splitting on "," is wrong here: a name can contain a comma and, more
    importantly, an escaped quote makes a naive quote-strip read the wrong span —
    which is how `Loong's Pearl` used to come out as `Loong`.
    """
    return [value for value in (_strip_quotes(m.group(0)) for m in _LUA_STRING_RE.finditer(raw)) if value]


def parse_lua_table(text: str, key: str) -> dict:
    """Very small parser for the Fandom Lua data modules."""
    # Extract nested fields from the return table.
    out = {}
    # Find each top-level entry: ['Name'] = { ... } or ["Name"] = { ... }.
    #
    # The closing brace is matched on indentation, not on an exact `\t},`:
    # the wiki's skill module is mixed — most entries close with a tab but some
    # (Cantarella, Jiyan, Chisa, Yuanwu) close with four spaces. Pinning the tab
    # silently dropped exactly those four characters' skill materials, which is
    # what the incompleteness check caught.
    pattern = re.compile(r"\[['\"]?([^'\"\]]+)['\"]?\]\s*=\s*\{(.*?)\n[ \t]+\},?\n", re.DOTALL)
    for m in pattern.finditer(text):
        name = m.group(1)
        body = m.group(2)
        record = {}
        # rarity
        rm = re.search(r"\[['\"]rarity['\"]\]\s*=\s*(\d+)", body)
        if rm:
            record["rarity"] = int(rm.group(1))
        # boss
        bm = re.search(rf"\[['\"]boss['\"]\]\s*=\s*({_STRING_BODY})", body)
        if bm:
            record["boss"] = _strip_quotes(bm.group(1))
        # local
        lm = re.search(rf"\[['\"]local['\"]\]\s*=\s*({_STRING_BODY})", body)
        if lm:
            record["local"] = _strip_quotes(lm.group(1))
        # common array
        cm = re.search(r"\[['\"]common['\"]\]\s*=\s*\{(.*?)\}", body, re.DOTALL)
        if cm:
            record["common"] = _string_list(cm.group(1))
        # ascension array
        am = re.search(r"\[['\"]ascension['\"]\]\s*=\s*\{(.*?)\}", body, re.DOTALL)
        if am:
            record["ascension"] = _string_list(am.group(1))
        # wsm array
        wm = re.search(r"\[['\"]wsm['\"]\]\s*=\s*\{(.*?)\}", body)
        if wm:
            record["wsm"] = _string_list(wm.group(1))
        # dwsm array
        dm = re.search(r"\[['\"]dwsm['\"]\]\s*=\s*\{(.*?)\}", body)
        if dm:
            record["dwsm"] = _string_list(dm.group(1))
        out[name] = record
    return out


def build_asset() -> dict:
    char_asc_text = fetch_wikitext(CHAR_ASC_DATA)
    skill_text = fetch_wikitext(SKILL_DATA)
    weapon_text = fetch_wikitext(WEAPON_DATA)

    chars = parse_lua_table(char_asc_text, "char")
    skills = parse_lua_table(skill_text, "skill")
    weapons = parse_lua_table(weapon_text, "weapon")

    characters = {}
    for name, data in chars.items():
        if not isinstance(data.get("rarity"), int):
            continue
        skill = skills.get(name, {})
        characters[name] = {
            "rarity": data.get("rarity"),
            "boss": data.get("boss"),
            "local": data.get("local"),
            "common": data.get("common", []),
            "wsm": skill.get("wsm", []),
            "dwsm": skill.get("dwsm", []),
            "skillBoss": skill.get("boss"),
        }

    weapon_list = {}
    for name, data in weapons.items():
        if not isinstance(data.get("rarity"), int):
            continue
        weapon_list[name] = {
            "rarity": data.get("rarity"),
            "ascension": data.get("ascension", []),
            "common": data.get("common", []),
            "baseAtk": data.get("base_atk"),
            "secondStatType": data.get("2nd_stat_type"),
            "secondStat": data.get("2nd_stat"),
        }

    return {
        "version": 1,
        "source": "wutheringwaves.fandom.com",
        "sourceTimestamp": fetch_source_timestamp(),
        "characterAscensionPhases": parse_character_ascension(fetch_wikitext(CHAR_ASC_MODULE)),
        **parse_skill_costs(fetch_wikitext(SKILL_MODULE)),
        "weaponAscensionByRarity": parse_weapon_ascension(fetch_wikitext(WEAPON_MODULE)),
        "characters": characters,
        "weapons": weapon_list,
    }


def main():
    print("Fetching character ascension data...")
    asset = build_asset()
    json_text = json.dumps(asset, indent=2, ensure_ascii=False)
    if OUT.exists() and OUT.read_text(encoding="utf-8") == json_text:
        print("No changes; asset is already up to date.")
        return
    OUT.write_text(json_text, encoding="utf-8")
    print(f"Wrote {OUT} ({len(json_text)} bytes)")
    PUBLISH.parent.mkdir(parents=True, exist_ok=True)
    PUBLISH.write_text(json_text, encoding="utf-8")
    print(f"Published {PUBLISH} for runtime fetch")


if __name__ == "__main__":
    main()
