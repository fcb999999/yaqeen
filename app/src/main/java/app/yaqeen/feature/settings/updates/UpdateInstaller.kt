package app.yaqeen.feature.settings.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** كل ما يخص تشغيل المثبّت وصلاحية "تثبيت التطبيقات غير المعروفة". */
object UpdateInstaller {
    const val PLAY_STORE_PACKAGE = "com.android.vending"
    const val APK_MIME = "application/vnd.android.package-archive"

    /** مجلد الملفات المنزَّلة (يطابق files-path name="downloads" في res/xml/file_paths.xml). */
    fun downloadsDir(context: Context): File = File(context.filesDir, "downloads").apply { mkdirs() }

    /** هل رُخّص لهذا التطبيق تثبيت حزم من مصادر غير المتجر؟ */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** يفتح شاشة منح صلاحية "تثبيت التطبيقات غير المعروفة" لهذا التطبيق. */
    fun openUnknownSourcesSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    /** يطلق مثبّت النظام للملف عبر FileProvider (الملف يجب أن يكون داخل filesDir/downloads). */
    fun launchInstall(context: Context, apk: File): Boolean {
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk) }.getOrNull()
            ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    /** هل ثُبّت التطبيق من متجر Google Play؟ (عندها يتولى المتجر التحديث ولا نحدّث ذاتيًا). */
    fun isInstalledFromPlayStore(context: Context): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        return installer == PLAY_STORE_PACKAGE
    }

    /** يفتح صفحة التطبيق في المتجر (تطبيق المتجر إن وُجد وإلا المتصفح). */
    fun openStorePage(context: Context): Boolean {
        val id = context.packageName.removeSuffix(".debug")
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, market)) return true
        return openUrl(context, "https://play.google.com/store/apps/details?id=$id")
    }

    fun openUrl(context: Context, url: String): Boolean {
        if (!url.startsWith("https://")) return false
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
