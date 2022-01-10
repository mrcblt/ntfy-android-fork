package io.heckel.ntfy.util

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.net.Uri
import android.view.Window
import io.heckel.ntfy.data.Notification
import io.heckel.ntfy.data.PROGRESS_NONE
import io.heckel.ntfy.data.Subscription
import java.security.SecureRandom
import java.text.DateFormat
import java.text.StringCharacterIterator
import java.util.*

fun topicUrl(baseUrl: String, topic: String) = "${baseUrl}/${topic}"
fun topicUrlUp(baseUrl: String, topic: String) = "${baseUrl}/${topic}?up=1" // UnifiedPush
fun topicUrlJson(baseUrl: String, topic: String, since: String) = "${topicUrl(baseUrl, topic)}/json?since=$since"
fun topicUrlJsonPoll(baseUrl: String, topic: String, since: String) = "${topicUrl(baseUrl, topic)}/json?poll=1&since=$since"
fun topicShortUrl(baseUrl: String, topic: String) =
    topicUrl(baseUrl, topic)
        .replace("http://", "")
        .replace("https://", "")

fun formatDateShort(timestampSecs: Long): String {
    val date = Date(timestampSecs*1000)
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
}

fun toPriority(priority: Int?): Int {
    if (priority != null && (1..5).contains(priority)) return priority
    else return 3
}

fun toPriorityString(priority: Int): String {
    return when (priority) {
        1 -> "min"
        2 -> "low"
        3 -> "default"
        4 -> "high"
        5 -> "max"
        else -> "default"
    }
}

fun joinTags(tags: List<String>?): String {
    return tags?.joinToString(",") ?: ""
}

fun joinTagsMap(tags: List<String>?): String {
    return tags?.mapIndexed { i, tag -> "${i+1}=${tag}" }?.joinToString(",") ?: ""
}

fun splitTags(tags: String?): List<String> {
    return if (tags == null || tags == "") {
        emptyList()
    } else {
        tags.split(",")
    }
}

fun toEmojis(tags: List<String>): List<String> {
    return tags.mapNotNull { tag -> toEmoji(tag) }
}

fun toEmoji(tag: String): String? {
    return EmojiManager.getForAlias(tag)?.unicode
}

fun unmatchedTags(tags: List<String>): List<String> {
    return tags.filter { tag -> toEmoji(tag) == null }
}

/**
 * Prepend tags/emojis to message, but only if there is a non-empty title.
 * Otherwise the tags will be prepended to the title.
 */
fun formatMessage(notification: Notification): String {
    return if (notification.title != "") {
        notification.message
    } else {
        val emojis = toEmojis(splitTags(notification.tags))
        if (emojis.isEmpty()) {
            notification.message
        } else {
            emojis.joinToString("") + " " + notification.message
        }
    }
}

/**
 * See above; prepend emojis to title if the title is non-empty.
 * Otherwise, they are prepended to the message.
 */
fun formatTitle(subscription: Subscription, notification: Notification): String {
    return if (notification.title != "") {
        formatTitle(notification)
    } else {
        topicShortUrl(subscription.baseUrl, subscription.topic)
    }
}

fun formatTitle(notification: Notification): String {
    val emojis = toEmojis(splitTags(notification.tags))
    return if (emojis.isEmpty()) {
        notification.title
    } else {
        emojis.joinToString("") + " " + notification.title
    }
}

// FIXME duplicate code
fun formatAttachmentInfo(notification: Notification, fileExists: Boolean): String {
    if (notification.attachment == null) return ""
    val att = notification.attachment
    val infos = mutableListOf<String>()
    if (att.name != null) infos.add(att.name)
    if (att.size != null) infos.add(formatBytes(att.size))
    //if (att.expires != null && att.expires != 0L) infos.add(formatDateShort(att.expires))
    if (att.progress in 0..99) infos.add("${att.progress}%")
    if (!fileExists) {
        if (att.progress == PROGRESS_NONE) infos.add("not downloaded")
        else infos.add("deleted")
    }
    if (infos.size == 0) return ""
    if (att.progress < 100) return "Downloading ${infos.joinToString(", ")}"
    return "\uD83D\uDCC4 " + infos.joinToString(", ")
}

// Checks in the most horrible way if a content URI exists; I couldn't find a better way
fun fileExists(context: Context, uri: String): Boolean {
    val resolver = context.applicationContext.contentResolver
    return try {
        val fileIS = resolver.openInputStream(Uri.parse(uri))
        fileIS?.close()
        true
    } catch (_: Exception) {
        false
    }
}

// Status bar color fading to match action bar, see https://stackoverflow.com/q/51150077/1440785
fun fadeStatusBarColor(window: Window, fromColor: Int, toColor: Int) {
    val statusBarColorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
    statusBarColorAnimation.addUpdateListener { animator ->
        val color = animator.animatedValue as Int
        window.statusBarColor = color
    }
    statusBarColorAnimation.start()
}

// Generates a (cryptographically secure) random string of a certain length
fun randomString(len: Int): String {
    val random = SecureRandom()
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray()
    return (1..len).map { chars[random.nextInt(chars.size)] }.joinToString("")
}

// Allows letting multiple variables at once, see https://stackoverflow.com/a/35522422/1440785
inline fun <T1: Any, T2: Any, R: Any> safeLet(p1: T1?, p2: T2?, block: (T1, T2)->R?): R? {
    return if (p1 != null && p2 != null) block(p1, p2) else null
}

fun formatBytes(bytes: Long): String {
    val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(bytes)
    if (absB < 1024) {
        return "$bytes B"
    }
    var value = absB
    val ci = StringCharacterIterator("KMGTPE")
    var i = 40
    while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
        value = value shr 10
        ci.next()
        i -= 10
    }
    value *= java.lang.Long.signum(bytes).toLong()
    return java.lang.String.format("%.1f %ciB", value / 1024.0, ci.current())
}

fun supportedImage(mimeType: String?): Boolean {
    return listOf("image/jpeg", "image/png").contains(mimeType)
}
