package io.github.sergioflatness.photoorderfix

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PRIVACY_URL = "https://sergioflatness.github.io/photo-order-fix/privacy.html"
const val TERMS_URL = "https://sergioflatness.github.io/photo-order-fix/terms.html"

/** Frequently asked questions, how to reach the developer, and the version. */
@Composable
fun HelpScreen(version: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Page {
        TopBar(stringResource(R.string.help_title), onBack)
        Headline(stringResource(R.string.faq_title))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                R.string.faq_why_q to R.string.faq_why_a,
                R.string.faq_originals_q to R.string.faq_originals_a,
                R.string.faq_trash_q to R.string.faq_trash_a,
                R.string.faq_space_q to R.string.faq_space_a,
                R.string.faq_cloud_q to R.string.faq_cloud_a,
                R.string.faq_gallery_q to R.string.faq_gallery_a,
                R.string.faq_repair_q to R.string.faq_repair_a,
            ).forEachIndexed { i, (q, a) ->
                ExpandCard(stringResource(q), initiallyOpen = i == 0) {
                    Text(stringResource(a), fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.support_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                val kakao = stringResource(R.string.kakao_openchat_url)
                if (kakao.isNotBlank()) TonalButton(stringResource(R.string.action_kakao), onClick = { open(context, kakao) })
                val email = stringResource(R.string.support_email)
                if (email.isNotBlank()) {
                    val subject = stringResource(R.string.support_email_subject, version ?: "?")
                    val body = stringResource(R.string.support_email_body, deviceInfo(version))
                    TonalButton(stringResource(R.string.action_email), onClick = { sendEmail(context, email, subject, body) })
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "v${version ?: "?"} · ${stringResource(R.string.developer_name)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row {
                SmallLink(stringResource(R.string.about_privacy)) { open(context, PRIVACY_URL) }
                SmallLink(stringResource(R.string.about_terms)) { open(context, TERMS_URL) }
            }
        }
    }
}

@Composable
private fun SmallLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** What a bug report needs, filled in so the user does not have to look it up. */
private fun deviceInfo(version: String?): String =
    "App ${version ?: "?"} / ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

// The app has no internet permission; links open in the user's browser or mail app instead.
fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun sendEmail(context: Context, to: String, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(intent) }
}
