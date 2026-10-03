package com.wuwaconfig.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/**
 * Character and weapon portraits for the gacha views.
 *
 * Portraits are read from bundled assets: `gacha_avatars/{slug}.webp` for characters
 * and `gacha_avatars/weapon_{slug}.webp` for weapons. Those files come from
 * `tools/download_gacha_avatars.py`, which discovers the roster from the wiki rather
 * than hardcoding it — so a new character is picked up by re-running the script, not
 * by editing code.
 *
 * The filename is derived from the name and the record's `resourceType`, so there is
 * no per-character entry anywhere in the app. That derivation is the whole point:
 * the roster is data, not code.
 *
 * When a portrait is missing the grid shows a coloured initial instead. That is a
 * deliberate choice, not a placeholder: most rows on a global account are 3★
 * materials whose wiki file name does not match the name the gacha endpoint returns,
 * so they resolve to nothing, and a grid of broken-image icons would be worse than a
 * grid of distinct, legible initials.
 */
object GachaAvatar {
    internal const val ASSET_DIR = "gacha_avatars"

    /**
     * The filenames present in the asset directory, cached after the first read.
     *
     * Listing assets is a filesystem walk, so it happens once per process rather
     * than once per avatar. A failed listing yields an empty set, which degrades
     * every avatar to the initial rather than crashing the grid.
     */
    @Volatile
    private var cachedAssets: Set<String>? = null

    private fun availableAssets(context: Context): Set<String> {
        cachedAssets?.let { return it }
        return synchronized(this) {
            cachedAssets
                ?: runCatching { context.assets.list(ASSET_DIR)?.toSet() ?: emptySet() }
                    .getOrDefault(emptySet())
                    .also { cachedAssets = it }
        }
    }

    /**
     * The bundled asset filename for [name], or null when it is not present.
     *
     * [resourceType] picks the prefix: the record endpoint labels characters
     * `Resonator` and weapons `Weapon`, and the script names the files to match.
     * Anything else is treated as a character, which is the common case.
     *
     * A leading `Weapon ` is stripped before slugifying. The gacha endpoint names
     * the base 3★ weapons `Weapon Broadblade41` while the wiki file is
     * `Broadblade41`, so without this the slug would carry a doubled `weapon_`
     * prefix and never match. Stripping is a no-op for a name that has no prefix,
     * so it is safe either way.
     */
    fun assetFile(
        context: Context,
        name: String,
        resourceType: String,
    ): String? {
        val base = name.removePrefix("Weapon ").removePrefix("weapon ")
        val slug = base.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val candidate =
            if (resourceType.equals("Weapon", ignoreCase = true)) {
                "weapon_$slug.webp"
            } else {
                "$slug.webp"
            }
        return candidate.takeIf { it in availableAssets(context) }
    }
}

/**
 * A portrait for [name], or a coloured initial when none resolves.
 *
 * The asset is checked before the image is requested, so a missing portrait never
 * reaches Coil as a failing load — it goes straight to the initial.
 */
@Composable
fun GachaAvatar(
    name: String,
    resourceType: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val asset = remember(name, resourceType) { GachaAvatar.assetFile(context, name, resourceType) }

    if (asset != null) {
        AsyncImage(
            model = "file:///android_asset/${GachaAvatar.ASSET_DIR}/$asset",
            contentDescription = name,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        AvatarInitial(name, size)
    }
}

/** The coloured-initial fallback. */
@Composable
private fun AvatarInitial(
    name: String,
    size: Dp,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * A stable colour for [name].
 *
 * Derived from the name rather than the pull count or position, so re-sorting the
 * grid does not recolour every row. Spread across a palette dark enough for a
 * white initial to stay legible.
 */
private fun avatarColor(name: String): Color {
    val palette =
        listOf(
            Color(0xFF5C6BC0),
            Color(0xFF26A69A),
            Color(0xFFEF5350),
            Color(0xFFAB47BC),
            Color(0xFF42A5F5),
            Color(0xFFFFA726),
            Color(0xFF66BB6A),
            Color(0xFFEC407A),
        )
    val index = (name.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}
