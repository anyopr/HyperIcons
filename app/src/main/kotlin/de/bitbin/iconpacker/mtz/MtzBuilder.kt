package de.bitbin.iconpacker.mtz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import de.bitbin.iconpacker.data.Assignment
import de.bitbin.iconpacker.data.AssignmentSource
import de.bitbin.iconpacker.iconpack.ComponentKey
import de.bitbin.iconpacker.picker.IconBitmapStore
import de.bitbin.iconpacker.ui.loadPackDrawable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Produces a HyperOS/MIUI-compatible icon theme .mtz.
 *
 * Structure:
 *   mtz (zip)
 *     ├── description.xml       — theme manifest
 *     ├── preview/              — optional thumbnails (omitted for now)
 *     └── icons                 — nested zip of PNGs, one per package
 *
 * Inside the `icons` nested zip, each entry gets two names when it's a launcher
 * activity (`<package>.png` is MIUI's package-fallback name) and an
 * activity-qualified `<package>.<activity>.png` so per-activity targets like
 * share-sheet entries resolve to the right drawable.
 */
class MtzBuilder(
    private val context: Context,
    private val iconPackPackage: String,
    private val themeName: String = "Arcticons Icons",
    private val author: String = "icon-pack-to-mui",
) {

    fun build(
        assignments: List<Assignment>,
        outputFile: File,
    ) {
        val bitmapStore = IconBitmapStore(context)
        val pm = context.packageManager
        val launcherActivityCache = mutableMapOf<String, String?>()
        fun launcherActivityFor(pkg: String): String? = launcherActivityCache.getOrPut(pkg) {
            runCatching {
                pm.getLaunchIntentForPackage(pkg)?.component?.className
            }.getOrNull()
        }

        val iconsZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                val written = mutableSetOf<String>()
                // Per-package fallback: the launcher-activity icon wins when we see
                // one; otherwise keep the first themed icon as a stand-in so packages
                // without visible launchers (Print, Quick Share, MiShare) still resolve.
                val packageFallback = mutableMapOf<String, ByteArray>()
                for (a in assignments) {
                    if (a.source == AssignmentSource.SKIP) continue
                    val png = resolveIcon(a, bitmapStore) ?: continue
                    val key = ComponentKey(a.pkg, a.activity)
                    writeEntry(zos, "${key.pkg}.${key.activity}.png", png, written)
                    val isLauncher = launcherActivityFor(a.pkg) == a.activity
                    if (isLauncher) {
                        packageFallback[a.pkg] = png
                    } else {
                        packageFallback.putIfAbsent(a.pkg, png)
                    }
                }
                for ((pkg, png) in packageFallback) {
                    writeEntry(zos, "$pkg.png", png, written)
                }
            }
            baos.toByteArray()
        }

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                zos.putNextEntry(ZipEntry("description.xml"))
                zos.write(buildDescriptionXml().toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("icons"))
                zos.write(iconsZipBytes)
                zos.closeEntry()
            }
        }
    }

    private fun writeEntry(
        zos: ZipOutputStream,
        name: String,
        bytes: ByteArray,
        written: MutableSet<String>,
    ) {
        if (!written.add(name)) return
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun resolveIcon(
        a: Assignment,
        bitmapStore: IconBitmapStore,
    ): ByteArray? {
        val bitmap = when (a.source) {
            AssignmentSource.MANUAL -> bitmapStore.load(ComponentKey(a.pkg, a.activity))
            AssignmentSource.AUTO -> a.drawable?.let { loadDrawableBitmap(it) }
            AssignmentSource.SKIP -> null
        } ?: return null
        return ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
    }

    private fun loadDrawableBitmap(drawableName: String): Bitmap? {
        val d = loadPackDrawable(context, iconPackPackage, drawableName) ?: return null
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        return bmp
    }

    private fun buildDescriptionXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <theme>
            <title>${escape(themeName)}</title>
            <author>${escape(author)}</author>
            <designer>${escape(author)}</designer>
            <version>1</version>
            <platform>2</platform>
            <description>Icons only, generated from $iconPackPackage</description>
            <defaults>0</defaults>
            <hasWallpaper>false</hasWallpaper>
            <hasLockWallpaper>false</hasLockWallpaper>
        </theme>
    """.trimIndent()

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
