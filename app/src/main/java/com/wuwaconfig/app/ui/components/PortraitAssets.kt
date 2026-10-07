package com.wuwaconfig.app.ui.components

import android.content.Context

/**
 * Resolves a calculator character/weapon name to a bundled portrait.
 *
 * The portraits already ship for the gacha views, under `gacha_avatars/` with a
 * `.webp` extension (written by `tools/download_gacha_avatars.py`), so the
 * calculator reuses them instead of shipping a second copy of the same art.
 *
 * ## Why this exists instead of reusing [GachaAvatar.assetFile]
 *
 * That helper slugifies with `[^a-z0-9]+ -> "_"`, which is right for gacha records
 * but wrong for wiki names. The five 2★ weapons are named `Broadblade#41`,
 * `Sword#18`, `Pistols#26`, `Gauntlets#21D` and `Rectifier#25` on the wiki, and the
 * download script slugged `#` away rather than to an underscore, so their files are
 * `weapon_broadblade41.webp` and friends. The gacha rule asks for
 * `weapon_broadblade_41.webp` and misses all five — silently, because a miss just
 * renders the initial.
 *
 * So this matches on [normalizeKey], which drops every non-alphanumeric character
 * on both sides. That makes spaces, apostrophes, `#`, `:` and `-` all irrelevant, so
 * `Daybreaker's Spine` and `daybreaker_s_spine` meet in the middle. Against the
 * shipped bundle this resolves 49/53 characters and 113/113 weapons; the four
 * `Rover-*` forms fall through to [ROVER_ALIASES], since the bundle carries a single
 * shared `rover.webp` for what the game treats as one character in four elemental
 * forms.
 *
 * As in [GachaAvatar], a miss is not an error — the caller draws a coloured initial.
 */
object PortraitAssets {
    private const val ASSET_DIR = "gacha_avatars"
    private const val URI_PREFIX = "file:///android_asset/"
    private const val WEAPON_PREFIX = "weapon_"
    private const val EXTENSION = ".webp"

    /** Wiki names whose portrait is bundled under a different, shared file. */
    private val ROVER_ALIASES = mapOf("rover" to "rover$EXTENSION")

    /** Bundled filenames keyed by [normalizeKey], built once per process. */
    @Volatile
    private var characters: Map<String, String>? = null

    @Volatile
    private var weapons: Map<String, String>? = null

    /**
     * The asset URI for [name], or null when the bundle has no portrait for it.
     *
     * Listing `assets/` is a filesystem walk, so both indexes are cached after the
     * first read. A failed listing yields empty maps, which degrades every row to
     * an initial rather than throwing — the same failure contract as
     * [GachaAvatar.availableAssets].
     */
    fun characterUri(
        context: Context,
        name: String,
    ): String? = resolve(context, name, characters, weapon = false)

    fun weaponUri(
        context: Context,
        name: String,
    ): String? = resolve(context, name, weapons, weapon = true)

    private fun resolve(
        context: Context,
        name: String,
        index: Map<String, String>?,
        weapon: Boolean,
    ): String? {
        val table =
            index ?: runCatching {
                val files = context.assets.list(ASSET_DIR)?.toList().orEmpty()
                buildIndex(files, weapon).also {
                    if (weapon) weapons = it else characters = it
                }
            }.getOrDefault(emptyMap())
        val key = normalizeKey(name)
        val file = table[key] ?: ROVER_ALIASES[normalizeKey(name.substringBefore('-'))]
        return file?.let { "$URI_PREFIX$ASSET_DIR/$it" }
    }

    /** Lowercased alphanumerics only. See the class KDoc for why `#` must vanish. */
    internal fun normalizeKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Indexes [files] by normalized key.
     *
     * A weapon's key is taken from the filename *after* `weapon_`, so the lookup
     * side can stay a plain normalized weapon name with no prefix to strip.
     */
    internal fun buildIndex(
        files: List<String>,
        weapon: Boolean,
    ): Map<String, String> {
        val index = LinkedHashMap<String, String>()
        files.forEach { file ->
            if (!file.endsWith(EXTENSION)) return@forEach
            var stem = file.removeSuffix(EXTENSION)
            if (weapon) {
                if (!stem.startsWith(WEAPON_PREFIX)) return@forEach
                stem = stem.removePrefix(WEAPON_PREFIX)
            } else if (stem.startsWith(WEAPON_PREFIX)) {
                return@forEach
            }
            index.putIfAbsent(normalizeKey(stem), file)
        }
        return index
    }
}
