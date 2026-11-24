package com.wuwaconfig.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rendering contract for the dashboard glass card.
 *
 * The Android 16 compositor artifact was caused by modifier topology, not by
 * a colour value: a shadow layer around the translucent card exposed its
 * rectangular dirty regions as bands behind individual text rows whenever a
 * wallpaper was set.
 *
 * A normal JVM test cannot rasterise that Android-specific composition, so
 * this contract pins the structural invariant that prevents the regression:
 * SurfaceCard must render on the Material Card surface with no shadow-bearing
 * layer of its own. Glass depth comes from the rim, wash, frosted interior
 * and sheen.
 */
class GlassCardRenderingContractTest {
    @Test
    fun `SurfaceCard renders without a shadow-bearing layer`() {
        val source = componentsSourceFile().readText()
        val functionBody =
            source
                .substringAfter("private fun SurfaceCard(")
                .substringBefore("\n/**")
        val code =
            functionBody
                .lineSequence()
                .map { it.substringBefore("//").trim() }
                .joinToString("\n")

        assertTrue(
            "SurfaceCard must paint its body on the Card surface",
            code.contains("containerColor = fill"),
        )
        assertTrue(
            "SurfaceCard must still draw the specular rim",
            code.contains(".surfaceEdge("),
        )
        assertFalse(
            "SurfaceCard must not create a graphics layer around translucent content",
            code.contains(".graphicsLayer"),
        )
        assertFalse(
            "SurfaceCard must not create a shadow layer around translucent content",
            code.contains("Modifier.shadow") || code.contains(".shadow("),
        )
        assertFalse(
            "SurfaceCard must not give the Card an elevation layer",
            code.contains("CardDefaults.cardElevation("),
        )
        assertFalse(
            "SurfaceCard must not assign raw shadowElevation to a nested layer",
            code.contains("shadowElevation ="),
        )
    }

    private fun componentsSourceFile(): File {
        val relativePaths =
            listOf(
                "app/src/main/java/com/wuwaconfig/app/ui/components/Components.kt",
                "src/main/java/com/wuwaconfig/app/ui/components/Components.kt",
            )
        var directory: File? = File(".").absoluteFile
        val tried = mutableListOf<String>()

        while (directory != null) {
            for (relativePath in relativePaths) {
                val candidate = File(directory, relativePath)
                tried += candidate.path
                if (candidate.isFile) return candidate
            }
            directory = directory.parentFile
        }

        throw AssertionError(
            "Components.kt not found walking up from user.dir=${System.getProperty("user.dir")}. " +
                "Tried:\n  ${tried.joinToString("\n  ")}",
        )
    }
}
