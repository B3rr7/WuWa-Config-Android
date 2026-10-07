package com.wuwaconfig.app.ui.components

import android.content.Context

/**
 * Resolves an upgrade-material name to a bundled icon.
 *
 * The icons live in `material_icons/`, written by `tools/download_material_icons.py`
 * from the wiki's `Item <Name>.png` namespace. That script derives its list from
 * `assets/config/calculator_materials.json`, so it can only contain icons for
 * materials the calculator can actually name — which is the point: a category
 * crawl would pull hundreds of icons no screen ever shows.
 *
 * ## Why the lookup is normalised rather than an exact filename
 *
 * Filenames are `slug(name)`, i.e. every non-alphanumeric run folded to `_`
 * ([slug] in the downloader). Matching the same way here means a future name
 * with a new kind of punctuation resolves without a script change, and it is
 * already consistent with how [PortraitAssets] finds portraits — the same
 * join-between-two-spellings problem, solved once in each direction.
 *
 * A miss is not an error: the caller renders the quantity alone rather than an
 * empty box, so a partially-fetched bundle degrades instead of looking broken.
 */
object MaterialIcons {
    private const val ASSET_DIR = "material_icons"
    private const val URI_PREFIX = "file:///android_asset/"
    private const val EXTENSION = ".webp"

    @Volatile
    private var cached: Set<String>? = null

    /**
     * The asset URI for [name], or null when the bundle has no icon for it.
     *
     * Listing `assets/` is a filesystem walk, so the filename set is cached after
     * the first read. A failed listing yields an empty set, which hides every
     * icon rather than throwing — the same failure contract as
     * [GachaAvatar.availableAssets].
     */
    fun uri(
        context: Context,
        name: String,
    ): String? {
        val files =
            cached ?: synchronized(this) {
                cached ?: runCatching {
                    context.assets.list(ASSET_DIR)?.toSet().orEmpty()
                }.getOrDefault(emptySet()).also { cached = it }
            }
        val file = "${slug(name)}$EXTENSION"
        return if (file in files) "$URI_PREFIX$ASSET_DIR/$file" else null
    }

    /** The downloader's filename form: lowercase, non-alphanumeric runs to `_`. */
    internal fun slug(name: String): String =
        name.lowercase()
            .replace(NON_ALNUM, "_")
            .trim('_')

    private val NON_ALNUM = Regex("[^a-z0-9]+")
}
