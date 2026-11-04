package app.yaqeen.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * تنفيذ Firestore. هيكل البيانات:  users/{uid}/{collection}/{docId}
 * ويُضاف حقل uid داخل كل وثيقة. قواعد الأمان (firestore.rules) تضمن أن المستخدم لا يصل إلا إلى مساره،
 * وأن الكتابة لا تقبل إلا updatedAt أحدث أو مساويًا (Last-Writer-Wins على الخادم).
 */
class FirestoreRemoteStore(private val db: FirebaseFirestore) : RemoteStore {

    private fun col(uid: String, collection: String) =
        db.collection("users").document(uid).collection(collection)

    override suspend fun push(uid: String, collection: String, docId: String, data: Map<String, Any?>): PushOutcome =
        suspendCancellableCoroutine { cont ->
            col(uid, collection).document(docId).set(data)
                .addOnSuccessListener { if (cont.isActive) cont.resume(PushOutcome.OK) }
                .addOnFailureListener { e ->
                    val code = (e as? FirebaseFirestoreException)?.code
                    val outcome = if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) PushOutcome.STALE_REJECTED else PushOutcome.FAILED
                    if (cont.isActive) cont.resume(outcome)
                }
        }

    override suspend fun fetchSince(uid: String, collection: String, sinceUpdatedAt: Long): List<RemoteDoc> =
        suspendCancellableCoroutine { cont ->
            col(uid, collection)
                .whereGreaterThan("updatedAt", sinceUpdatedAt)
                .orderBy("updatedAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener { snap ->
                    if (cont.isActive) cont.resume(snap.documents.mapNotNull { d -> d.data?.let { RemoteDoc(d.id, it) } })
                }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

    override fun listen(
        uid: String,
        collection: String,
        sinceUpdatedAt: Long,
        onDocs: (List<RemoteDoc>) -> Unit,
        onError: (Throwable) -> Unit,
    ): Closeable {
        val reg: ListenerRegistration = col(uid, collection)
            .whereGreaterThan("updatedAt", sinceUpdatedAt)
            .addSnapshotListener { snap, err ->
                if (err != null) { onError(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val docs = snap.documentChanges
                    .filter { it.type != com.google.firebase.firestore.DocumentChange.Type.REMOVED }
                    .mapNotNull { c -> c.document.data.let { d -> RemoteDoc(c.document.id, d) } }
                if (docs.isNotEmpty()) onDocs(docs)
            }
        return Closeable { reg.remove() }
    }

    override suspend fun writeProfile(uid: String, profile: Map<String, Any?>): Unit =
        suspendCancellableCoroutine { cont ->
            db.collection("users").document(uid).set(profile + ("uid" to uid), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
}
