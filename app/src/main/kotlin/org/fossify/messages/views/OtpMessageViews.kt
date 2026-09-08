package org.fossify.messages.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.views.MyTextView
import org.fossify.messages.R

class OtpAwareMessageTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MyTextView(context, attrs) {

    override fun setText(text: CharSequence?, type: TextView.BufferType?) {
        super.setText(text, type)
        val code = OtpCodeDetector.detect(text?.toString().orEmpty())
        rootView.findViewById<OtpCopyTextView?>(R.id.thread_message_otp_copy)?.setOtpCode(code)
    }
}

class OtpCopyTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MyTextView(context, attrs) {

    private var otpCode: String? = null

    init {
        setTextColor(context.getProperPrimaryColor())
        setOnClickListener {
            val code = otpCode ?: return@setOnClickListener
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.otp_code_label), code))
            Toast.makeText(context, R.string.otp_code_copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun setOtpCode(code: String?) {
        otpCode = code
        if (code == null) {
            visibility = View.GONE
        } else {
            text = context.getString(R.string.copy_otp_code, code)
            setTextColor(context.getProperPrimaryColor())
            visibility = View.VISIBLE
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val body = (parent as? View)?.findViewById<View>(R.id.thread_message_body)
        val params = body?.layoutParams as? RelativeLayout.LayoutParams
        val isSentMessage = params?.getRule(RelativeLayout.ALIGN_PARENT_END) != 0
        visibility = if (otpCode != null && !isSentMessage) View.VISIBLE else View.GONE
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

private object OtpCodeDetector {
    private val keywords = listOf(
        "otp",
        "one-time",
        "one time",
        "verification",
        "verify",
        "passcode",
        "security code",
        "authentication code",
        "رمز",
        "كود",
        "التحقق",
        "تحقق",
        "التأكيد",
        "تأكيد",
        "رمز الدخول",
        "لمرة واحدة",
    )

    private val codeRegex = Regex("(?<![0-9٠-٩])[0-9٠-٩]{4,8}(?![0-9٠-٩])")

    fun detect(body: String): String? {
        if (body.isBlank()) return null
        val normalizedBody = body.lowercase()
        if (keywords.none { normalizedBody.contains(it) }) return null

        return codeRegex.findAll(body)
            .map { it.value }
            .firstOrNull { candidate -> !candidate.isLikelyYear() }
    }

    private fun String.isLikelyYear(): Boolean {
        if (length != 4) return false
        val westernDigits = map {
            when (it) {
                '٠' -> '0'
                '١' -> '1'
                '٢' -> '2'
                '٣' -> '3'
                '٤' -> '4'
                '٥' -> '5'
                '٦' -> '6'
                '٧' -> '7'
                '٨' -> '8'
                '٩' -> '9'
                else -> it
            }
        }.joinToString("")
        val value = westernDigits.toIntOrNull() ?: return false
        return value in 1900..2099
    }
}
