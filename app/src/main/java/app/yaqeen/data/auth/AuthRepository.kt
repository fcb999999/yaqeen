package app.yaqeen.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    /** Firebase غير مُعدّ في هذا البناء (لا يوجد google-services.json): التطبيق يعمل محليًا فقط. */
    data object Unavailable : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val uid: String, val name: String?, val email: String?, val photoUrl: String?) : AuthState
}

/** تسجيل الدخول بحساب Google عبر Credential Manager ثم Firebase Auth. لا أسرار داخل الكود. */
class AuthRepository(private val context: Context) {
    private val firebaseReady: Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    private val auth: FirebaseAuth? = if (firebaseReady) FirebaseAuth.getInstance() else null

    private val _state = MutableStateFlow<AuthState>(initial())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val isAvailable: Boolean get() = auth != null
    val currentUid: String? get() = (state.value as? AuthState.SignedIn)?.uid

    init {
        auth?.addAuthStateListener { _state.value = toState(it.currentUser) }
    }

    private fun initial(): AuthState = if (auth == null) AuthState.Unavailable else toState(auth.currentUser)

    private fun toState(u: com.google.firebase.auth.FirebaseUser?): AuthState =
        if (u == null) AuthState.SignedOut else AuthState.SignedIn(u.uid, u.displayName, u.email, u.photoUrl?.toString())

    /** معرّف عميل الويب ينشئه مكوّن google-services تلقائيًا (default_web_client_id) من ملف الإعداد. */
    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    suspend fun signInWithGoogle(activity: Activity): Result<Unit> = runCatching {
        val a = auth ?: error("خدمة الحساب غير مفعّلة في هذا البناء")
        val serverId = webClientId() ?: error("لم يُضبط معرّف عميل Google في ملف الإعداد")
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val cred = result.credential
        require(cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "نوع بيانات اعتماد غير متوقع"
        }
        val googleId = GoogleIdTokenCredential.createFrom(cred.data)
        a.signInWithCredential(GoogleAuthProvider.getCredential(googleId.idToken, null)).await()
        Unit
    }

    suspend fun signOut() {
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        _state.value = if (auth == null) AuthState.Unavailable else AuthState.SignedOut
    }
}
