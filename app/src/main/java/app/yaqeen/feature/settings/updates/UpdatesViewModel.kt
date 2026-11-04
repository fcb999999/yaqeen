package app.yaqeen.feature.settings.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.BuildConfig
import app.yaqeen.data.prefs.LocalPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal sealed interface CheckUi {
    data object Checking : CheckUi
    data class Done(val result: UpdateCheckResult, val checkedAtMs: Long) : CheckUi
}

internal sealed interface InstallUi {
    data object Idle : InstallUi
    data class Downloading(val done: Long, val total: Long) : InstallUi
    data object Verifying : InstallUi

    /** الملف جاهز وموثَّق لكن نحتاج إذن "تثبيت التطبيقات غير المعروفة". */
    data class NeedsPermission(val file: File, val checksumVerified: Boolean) : InstallUi

    /** فُتح مثبّت النظام. */
    data class Launched(val file: File, val checksumVerified: Boolean) : InstallUi
    data class Failed(val message: String) : InstallUi
}

/**
 * منطق شاشة التحديثات: فحص GitHub، تنزيل APK، التحقق من المجموع والتوقيع، ثم تشغيل المثبّت.
 * لا يُشغَّل المثبّت أبدًا قبل نجاح التحقق.
 */
internal class UpdatesViewModel(private val c: AppContainer) : ViewModel() {
    private val appContext = c.context
    val repo: String = BuildConfig.GITHUB_REPO
    val configured: Boolean = UpdateChecker.isValidRepo(repo)
    val fromPlayStore: Boolean = UpdateInstaller.isInstalledFromPlayStore(appContext)

    private val http = UrlHttp()
    private val checker = UpdateChecker(http, repo, BuildConfig.VERSION_NAME)
    private val downloader = ApkDownloader()

    private val _check = MutableStateFlow<CheckUi>(CheckUi.Checking)
    val check: StateFlow<CheckUi> = _check.asStateFlow()
    private val _install = MutableStateFlow<InstallUi>(InstallUi.Idle)
    val install: StateFlow<InstallUi> = _install.asStateFlow()

    private var checkJob: Job? = null
    private var installJob: Job? = null

    init {
        // ملفات تحديث قديمة (بعد تحديث ناجح أو محاولة سابقة) لا حاجة لها.
        runCatching { UpdateInstaller.downloadsDir(appContext).listFiles()?.forEach { it.delete() } }
        if (!fromPlayStore) check()
    }

    fun check() {
        if (checkJob?.isActive == true) return
        _check.value = CheckUi.Checking
        checkJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { checker.check() }
            val now = System.currentTimeMillis()
            if (result !is UpdateCheckResult.Disabled && result !is UpdateCheckResult.Offline) {
                runCatching { c.localPrefs.put(LocalPrefs.Keys.LAST_UPDATE_CHECK, now.toString()) }
            }
            _check.value = CheckUi.Done(result, now)
        }
    }

    fun startUpdate(release: ReleaseInfo) {
        if (installJob?.isActive == true) return
        val apk = release.apk
        if (apk == null) {
            _install.value = InstallUi.Failed("لا يوجد ملف APK مرفق بهذا الإصدار.")
            return
        }
        installJob = viewModelScope.launch {
            val dir = UpdateInstaller.downloadsDir(appContext)
            val dest = File(dir, "yaqeen-" + release.tag.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".apk")
            try {
                dir.listFiles()?.forEach { it.delete() }
                _install.value = InstallUi.Downloading(0, apk.size)
                val expected = withContext(Dispatchers.IO) { checker.resolveSha256(release) }
                if (expected == null && release.checksumAssetUrl != null) {
                    _install.value = InstallUi.Failed("تعذّر جلب مجموع التحقق المنشور مع الإصدار. أعد المحاولة لاحقًا.")
                    return@launch
                }
                val file = downloader.download(apk.url, dest, apk.size) { done, total ->
                    _install.value = InstallUi.Downloading(done, total)
                }
                _install.value = InstallUi.Verifying
                val verdict = withContext(Dispatchers.IO) { ApkVerifier.verify(appContext, file.file, expected, file.sha256) }
                when (verdict) {
                    is VerifyResult.Failed -> {
                        dest.delete()
                        _install.value = InstallUi.Failed(verdict.reason)
                    }
                    is VerifyResult.Ok -> proceedToInstall(dest, verdict.checksumVerified)
                }
            } catch (e: CancellationException) {
                dest.delete()
                File(dest.path + ".part").delete()
                _install.value = InstallUi.Idle
                throw e
            } catch (e: Exception) {
                dest.delete()
                _install.value = InstallUi.Failed(e.message?.takeIf { it.isNotBlank() } ?: "فشل تنزيل التحديث.")
            }
        }
    }

    /** بعد منح إذن التثبيت (أو لإعادة فتح المثبّت). */
    fun retryInstall() {
        val s = _install.value
        val (file, verified) = when (s) {
            is InstallUi.NeedsPermission -> s.file to s.checksumVerified
            is InstallUi.Launched -> s.file to s.checksumVerified
            else -> return
        }
        if (!file.isFile) {
            _install.value = InstallUi.Failed("لم يعد ملف التحديث موجودًا. نزّله من جديد.")
            return
        }
        proceedToInstall(file, verified)
    }

    private fun proceedToInstall(file: File, checksumVerified: Boolean) {
        if (!UpdateInstaller.canInstallPackages(appContext)) {
            _install.value = InstallUi.NeedsPermission(file, checksumVerified)
            return
        }
        _install.value = if (UpdateInstaller.launchInstall(appContext, file)) {
            InstallUi.Launched(file, checksumVerified)
        } else {
            InstallUi.Failed("تعذّر تشغيل مثبّت النظام.")
        }
    }

    fun cancelDownload() {
        installJob?.cancel()
    }

    fun dismissError() {
        if (_install.value is InstallUi.Failed) _install.value = InstallUi.Idle
    }
}
