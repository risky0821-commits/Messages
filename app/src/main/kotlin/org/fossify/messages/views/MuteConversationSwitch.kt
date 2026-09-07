package org.fossify.messages.views

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import com.google.android.material.materialswitch.MaterialSwitch
import org.fossify.commons.extensions.notificationManager
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.MUTED_NOTIFICATION_CHANNEL_PREFIX
import org.fossify.messages.helpers.THREAD_ID

class MuteConversationSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialSwitch(context, attrs) {

    private val threadId: Long
        get() = (context as? Activity)?.intent?.getLongExtra(THREAD_ID, 0L) ?: 0L

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val id = threadId
        if (id == 0L) return

        isChecked = context.config.isConversationMuted(id)
        setOnCheckedChangeListener { _, muted ->
            context.config.setConversationMuted(id, muted)
            context.notificationManager.cancel(id.hashCode())
            if (!muted) {
                context.notificationManager.deleteNotificationChannel(
                    MUTED_NOTIFICATION_CHANNEL_PREFIX + id
                )
            }
        }
    }
}
