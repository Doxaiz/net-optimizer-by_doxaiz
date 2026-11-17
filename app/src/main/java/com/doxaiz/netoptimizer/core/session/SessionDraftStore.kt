package com.doxaiz.netoptimizer.core.session

import android.content.Context

/** SharedPreferences apply() queues ordered atomic writes; the draft never leaves this app's storage. */
class SessionDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("doxaiz_session_draft", Context.MODE_PRIVATE)

    fun load(): Result<SessionDraft?> = runCatching {
        val encoded = prefs.getString("draft", null) ?: return@runCatching null
        SessionDraftCodec.decode(encoded) ?: error("Invalid session draft")
    }

    fun save(draft: SessionDraft) {
        prefs.edit().putString("draft", SessionDraftCodec.encode(draft)).apply()
    }

    fun clear() {
        prefs.edit().remove("draft").apply()
    }
}
