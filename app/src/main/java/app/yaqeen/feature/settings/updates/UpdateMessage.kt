package app.yaqeen.feature.settings.updates

/**
 * رسالة FCM (data only) المرسلة إلى موضوع "app_updates" من سكربت الإصدار:
 * {type:"update", version:"1.2.3", url:"https://github.com/…/releases/tag/v1.2.3"}
 */
data class UpdateMessage(val version: String, val url: String?) {
    companion object {
        const val TYPE_UPDATE = "update"

        /** يحلّل بيانات الرسالة. يعيد null لأي نوع آخر أو إصدار غير صالح. الرابط يُقبل فقط إن كان https. */
        fun parse(data: Map<String, String>): UpdateMessage? {
            if (data["type"] != TYPE_UPDATE) return null
            val version = data["version"]?.trim().orEmpty()
            if (SemVer.parse(version) == null) return null
            val url = data["url"]?.trim()?.takeIf { it.startsWith("https://") }
            return UpdateMessage(version.removePrefix("v").removePrefix("V"), url)
        }
    }
}
