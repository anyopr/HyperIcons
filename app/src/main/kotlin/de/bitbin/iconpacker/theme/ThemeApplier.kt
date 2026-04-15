package de.bitbin.iconpacker.theme

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

sealed class ApplyResult {
    data class Launched(val via: String) : ApplyResult()
    data class NoHandler(val exportedTo: File) : ApplyResult()
}

private const val THEME_MANAGER_PKG = "com.android.thememanager"
// Reverse-engineered from MIUI Theme Tester (com.arteneta.miuithemetester).
// Their HomeActivity invokes this exact component with `theme_file_path` pointing
// to an absolute filesystem path (NOT a content:// URI), and flag 0x8000.
private const val APPLY_FOR_SCREENSHOT = "com.android.thememanager.ApplyThemeForScreenshot"

object ThemeApplier {

    fun apply(context: Context, mtz: File): ApplyResult {
        // The thememanager process needs filesystem-readable access to the .mtz.
        // Publish to Downloads — picker still appears (HyperOS 3 always opens it),
        // but at least the file lands somewhere the user can find.
        val published = saveToDownloads(context, mtz)

        android.util.Log.w(
            "IconPacker",
            "ApplyThemeForScreenshot path=${published.absolutePath} exists=${published.exists()} size=${published.length()}",
        )
        val intent = Intent().apply {
            component = ComponentName(THEME_MANAGER_PKG, APPLY_FOR_SCREENSHOT)
            putExtra("api_called_from", THEME_MANAGER_PKG)
            putExtra("theme_file_path", published.absolutePath)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (context.packageManager.resolveActivity(intent, 0) != null) {
            runCatching {
                context.startActivity(intent)
                return ApplyResult.Launched("ApplyThemeForScreenshot")
            }
        }

        // Fallback: chooser via FileProvider URI. MTZ Tester picks this up.
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            mtz,
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setData(uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context.packageManager.resolveActivity(viewIntent, 0) != null) {
            context.startActivity(
                Intent.createChooser(viewIntent, "Apply theme")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return ApplyResult.Launched("chooser")
        }

        return ApplyResult.NoHandler(saveToDownloads(context, mtz))
    }

    /**
     * Copies the .mtz to the public Downloads directory so it can be opened later
     * by any file manager (e.g., to feed into MTZ Tester). Returns the actual
     * filesystem path MediaStore wrote to — which may differ from the
     * caller-supplied name if MediaStore disambiguated a collision.
     */
    fun saveToDownloads(context: Context, mtz: File): File {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, mtz.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val target: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (target != null) {
                resolver.openOutputStream(target)?.use { os ->
                    mtz.inputStream().use { it.copyTo(os) }
                }
                resolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null, null,
                )
                val realPath = resolver.query(
                    target,
                    arrayOf(MediaStore.Downloads.DATA),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
                if (realPath != null) return File(realPath)
                return File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    mtz.name,
                )
            }
        }
        val exports = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val dest = File(exports, mtz.name)
        mtz.copyTo(dest, overwrite = true)
        return dest
    }
}
