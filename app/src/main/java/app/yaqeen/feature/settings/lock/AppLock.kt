package app.yaqeen.feature.settings.lock

import android.os.SystemClock
import app.yaqeen.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * تجزئة رمز القفل: PBKDF2-HMAC-SHA256 بملح عشوائي. لا يُحفظ الرمز نفسه أبدًا، ويبقى على هذا الجهاز
 * فقط (LocalPrefs، لا يُزامَن).
 */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    fun isValidPin(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Base64.getEncoder().encodeToString(bytes)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: String, expected: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())
}

/** إعداد القفل كما هو محفوظ. [delaySeconds]: مدة البقاء خارج التطبيق قبل أن يُقفل (0 = فورًا). */
data class LockConfig(
    val enabled: Boolean = false,
    val biometric: Boolean = false,
    val delaySeconds: Int = 0,
    val pinLength: Int = 0,
)

enum class LockPhase {
    /** لم يُقرأ الإعداد بعد (لحظة بدء التطبيق): تُعرض شاشة فارغة كي لا يظهر المحتوى قبل القفل. */
    Unknown,
    Locked,
    Unlocked,
}

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class LockedOut(val untilMs: Long) : PinResult
}

/** حالة قفل التطبيق (واحدة للعملية كلها). */
object AppLock {
    /** مهلة دنيا حتى في «فورًا»: كي لا يُقفل عند فتح نافذة مشاركة أو منتقي ملفات والرجوع منها سريعًا. */
    const val MIN_GRACE_MS = 3_000L
    const val MAX_ATTEMPTS = 5
    const val LOCKOUT_MS = 30_000L
    val DELAY_OPTIONS = listOf(0, 60, 300)

    private const val K_ENABLED = "lock.enabled"
    private const val K_HASH = "lock.hash"
    private const val K_SALT = "lock.salt"
    private const val K_LEN = "lock.len"
    private const val K_BIO = "lock.biometric"
    private const val K_DELAY = "lock.delay_s"
    private const val K_FAILS = "lock.fails"
    private const val K_UNTIL = "lock.lockout_until"

    private val _phase = MutableStateFlow(LockPhase.Unknown)
    val phase: StateFlow<LockPhase> = _phase.asStateFlow()

    private val _config = MutableStateFlow<LockConfig?>(null)
    val config: StateFlow<LockConfig?> = _config.asStateFlow()

    private var stoppedAt = 0L

    /** يقرأ الإعداد عند بدء العملية ويُقفل إن كان القفل مفعّلًا. */
    fun init(c: AppContainer) {
        if (_config.value != null) return
        c.appScope.launch {
            val cfg = load(c)
            _config.value = cfg
            if (_phase.value == LockPhase.Unknown) _phase.value = if (cfg.enabled) LockPhase.Locked else LockPhase.Unlocked
        }
    }

    /** هل يجب القفل بعد غياب [awayMs]؟ (خالصة، للاختبار) */
    fun shouldLock(cfg: LockConfig, awayMs: Long): Boolean =
        cfg.enabled && awayMs >= maxOf(MIN_GRACE_MS, cfg.delaySeconds * 1000L)

    fun onActivityStop() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onActivityStart() {
        val cfg = _config.value ?: return
        if (_phase.value == LockPhase.Unlocked && stoppedAt > 0 && shouldLock(cfg, SystemClock.elapsedRealtime() - stoppedAt)) {
            _phase.value = LockPhase.Locked
        }
        stoppedAt = 0
    }

    fun unlock() {
        _phase.value = LockPhase.Unlocked
        stoppedAt = 0
    }

    private suspend fun load(c: AppContainer): LockConfig {
        val p = c.localPrefs
        val enabled = p.getBool(K_ENABLED) && p.get(K_HASH) != null && p.get(K_SALT) != null
        return LockConfig(
            enabled = enabled,
            biometric = enabled && p.getBool(K_BIO),
            delaySeconds = p.get(K_DELAY)?.toIntOrNull()?.takeIf { it in DELAY_OPTIONS } ?: 0,
            pinLength = p.get(K_LEN)?.toIntOrNull() ?: 0,
        )
    }

    private suspend fun reload(c: AppContainer) {
        _config.value = load(c)
    }

    /** يفعّل القفل برمز جديد (أو يغيّر الرمز). */
    suspend fun setPin(c: AppContainer, pin: String) {
        require(PinHasher.isValidPin(pin))
        val salt = PinHasher.newSalt()
        val p = c.localPrefs
        p.put(K_SALT, salt)
        p.put(K_HASH, PinHasher.hash(pin, salt))
        p.put(K_LEN, pin.length.toString())
        p.put(K_FAILS, null)
        p.put(K_UNTIL, null)
        p.putBool(K_ENABLED, true)
        reload(c)
        _phase.value = LockPhase.Unlocked
    }

    suspend fun disable(c: AppContainer) {
        val p = c.localPrefs
        for (k in listOf(K_HASH, K_SALT, K_LEN, K_FAILS, K_UNTIL, K_BIO)) p.put(k, null)
        p.putBool(K_ENABLED, false)
        reload(c)
        _phase.value = LockPhase.Unlocked
    }

    suspend fun setBiometric(c: AppContainer, on: Boolean) {
        c.localPrefs.putBool(K_BIO, on)
        reload(c)
    }

    suspend fun setDelay(c: AppContainer, seconds: Int) {
        c.localPrefs.put(K_DELAY, seconds.coerceIn(0, 3600).toString())
        reload(c)
    }

    /** وقت انتهاء الإيقاف المؤقت بعد المحاولات الخاطئة (0 = لا إيقاف). */
    suspend fun lockoutUntil(c: AppContainer): Long {
        val until = c.localPrefs.get(K_UNTIL)?.toLongOrNull() ?: 0L
        return if (until > System.currentTimeMillis()) until else 0L
    }

    /** يتحقق من الرمز مع حدّ للمحاولات: بعد [MAX_ATTEMPTS] أخطاء متتالية إيقاف [LOCKOUT_MS]. */
    suspend fun checkPin(c: AppContainer, pin: String): PinResult {
        val p = c.localPrefs
        val now = System.currentTimeMillis()
        lockoutUntil(c).takeIf { it > now }?.let { return PinResult.LockedOut(it) }
        val salt = p.get(K_SALT) ?: return PinResult.Wrong(0)
        val hash = p.get(K_HASH) ?: return PinResult.Wrong(0)
        if (PinHasher.verify(pin, salt, hash)) {
            p.put(K_FAILS, null)
            p.put(K_UNTIL, null)
            return PinResult.Ok
        }
        val fails = (p.get(K_FAILS)?.toIntOrNull() ?: 0) + 1
        return if (fails >= MAX_ATTEMPTS) {
            p.put(K_FAILS, null)
            val until = now + LOCKOUT_MS
            p.put(K_UNTIL, until.toString())
            PinResult.LockedOut(until)
        } else {
            p.put(K_FAILS, fails.toString())
            PinResult.Wrong(MAX_ATTEMPTS - fails)
        }
    }
}
