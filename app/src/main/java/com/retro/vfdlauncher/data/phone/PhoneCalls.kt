package com.retro.vfdlauncher.data.phone

import android.app.Notification
import android.app.PendingIntent
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** What the call bar shows. */
enum class CallStage {
    /** Incoming, with ANSWER / HANG UP buttons from the notification. */
    RINGING,
    /** Incoming, but the notification has no buttons: OPEN PHONE to answer there. */
    RINGING_NO_BUTTONS,
    /** Dialling out. */
    CALLING,
    /** Connected. */
    ACTIVE,
}

data class CallInfo(
    val stage: CallStage,
    val name: String,
    val number: String?,
    val packageName: String,
    /** SystemClock.elapsedRealtime() when the call was answered (for IN CALL · 00:42). */
    val connectedAt: Long? = null,
    val answer: PendingIntent? = null,
    val hangUp: PendingIntent? = null,
)

/** One notification action, reduced to what the parser needs. */
data class ActionInfo(val title: String)

/** Pure reading of a call notification, unit-tested. */
object CallParser {
    private val answerWords = listOf("answer", "accept", "pick up", "beantwoord", "opnemen", "aannemen", "accepteren", "répondre", "annehmen", "接听")
    private val hangUpWords = listOf("hang up", "end call", "end", "decline", "reject", "dismiss", "ophangen", "weigeren", "beëindig", "afwijzen", "raccrocher", "auflegen", "ablehnen", "挂断", "拒绝")
    private val incomingWords = listOf("incoming", "inkomend", "ringing", "entrant", "eingehend", "来电")
    private val callingWords = listOf("calling", "dialing", "dialling", "outgoing", "bellen", "uitgaand", "appel en cours", "wählen")
    private val numberRegex = Regex("""^[+\d][\d\s().\-/]{5,}$""")
    private val phoneishPackage = Regex("""(?i)(bluetooth|btphone|bt\.phone|\.bt|dialer|incallui|telecom|phone|telefoon|hands?free|bt_)""")

    /** Index of the answer / hang-up action among [actions], or -1. */
    fun answerIndex(actions: List<ActionInfo>) = actions.indexOfFirst { a -> answerWords.any { it in a.title.lowercase(Locale.ROOT) } }
    fun hangUpIndex(actions: List<ActionInfo>) = actions.indexOfFirst { a -> hangUpWords.any { it in a.title.lowercase(Locale.ROOT) } }

    /** Whether a notification looks like a phone call (category CALL, or an ongoing one from a phone/Bluetooth app). */
    fun isCall(category: String?, packageName: String, ongoing: Boolean, actions: List<ActionInfo>): Boolean {
        if (category == Notification.CATEGORY_CALL) return true
        return ongoing && phoneishPackage.containsMatchIn(packageName) && (answerIndex(actions) >= 0 || hangUpIndex(actions) >= 0)
    }

    /**
     * Stage, display name and number from a call notification's title/text and its buttons.
     * Typical: title "Sarah" (or "+32 470 …"), text "Incoming call" / "Ongoing call" / "+32 470 …".
     */
    fun read(title: String?, text: String?, actions: List<ActionInfo>): Triple<CallStage, String, String?> {
        val all = listOfNotNull(title, text).joinToString(" ").lowercase(Locale.ROOT)
        val canAnswer = answerIndex(actions) >= 0
        val stage = when {
            canAnswer -> CallStage.RINGING
            callingWords.any { it in all } -> CallStage.CALLING
            incomingWords.any { it in all } -> CallStage.RINGING_NO_BUTTONS
            else -> CallStage.ACTIVE
        }
        val fields = listOfNotNull(title, text).map { it.trim() }.filter { it.isNotEmpty() }
        val number = fields.firstOrNull { numberRegex.matches(it) }
        val statusish = incomingWords + callingWords + listOf("ongoing", "call", "oproep", "gesprek", "anruf", "appel")
        val name = fields.firstOrNull { f -> f != number && statusish.none { it in f.lowercase(Locale.ROOT) } }
            ?: number ?: "UNKNOWN"
        return Triple(stage, name, number?.takeIf { it != name })
    }
}

/**
 * The current phone call, read from the call notification of ATOTO's Bluetooth phone app (or a
 * phone's dialer). Fed by the notification listener; a process-wide singleton like the next turn.
 */
object PhoneCalls {
    private val _call = MutableStateFlow<CallInfo?>(null)
    val call: StateFlow<CallInfo?> = _call.asStateFlow()

    private var key: String? = null

    /** The last call notification's fields, for the session inspector (tuning to ATOTO). */
    @Volatile
    var lastRaw: String? = null
        private set

    fun onPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        val actions = n.actions.orEmpty().map { ActionInfo(it.title?.toString().orEmpty()) }
        if (!CallParser.isCall(n.category, sbn.packageName, sbn.isOngoing, actions)) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        lastRaw = "  ${sbn.packageName} category=${n.category} ongoing=${sbn.isOngoing}\n" +
            "  title=$title\n  text=$text\n  actions=${actions.joinToString { "'${it.title}'" }}"
        val (stage, name, number) = CallParser.read(title, text, actions)
        val answer = CallParser.answerIndex(actions).takeIf { it >= 0 }?.let { n.actions[it].actionIntent }
        val hangUp = CallParser.hangUpIndex(actions).takeIf { it >= 0 }?.let { n.actions[it].actionIntent }
        val previous = _call.value
        val connectedAt = when {
            stage != CallStage.ACTIVE -> null
            previous?.connectedAt != null -> previous.connectedAt
            else -> SystemClock.elapsedRealtime()
        }
        key = sbn.key
        _call.value = CallInfo(stage, name, number, sbn.packageName, connectedAt, answer, hangUp)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.key == key) clear()
    }

    fun clear() {
        key = null
        _call.value = null
    }

    /** Press a notification button; false if there is none or it no longer works. */
    fun press(intent: PendingIntent?): Boolean =
        intent != null && runCatching { intent.send() }.isSuccess
}
