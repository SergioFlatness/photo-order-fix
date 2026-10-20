package io.github.sergioflatness.photoorderfix

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PRIVACY_URL = "https://sergioflatness.github.io/photo-order-fix/privacy.html"
const val TERMS_URL = "https://sergioflatness.github.io/photo-order-fix/terms.html"
private const val ISSUES_URL = "https://github.com/SergioFlatness/photo-order-fix/issues"
private const val SOURCE_URL = "https://github.com/SergioFlatness/photo-order-fix"

/** The round "?" button in the header that opens [InfoScreen]. */
@Composable
fun InfoButton(onClick: () -> Unit) {
    val label = stringResource(R.string.info_title)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
    }
}

/** App info, frequently asked questions, and how to get help. */
@Composable
fun InfoScreen(version: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    Text(stringResource(R.string.info_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

    Section(stringResource(R.string.faq_title)) {
        val faqs = listOf(
            R.string.faq_why_q to R.string.faq_why_a,
            R.string.faq_how_q to R.string.faq_how_a,
            R.string.faq_quality_q to R.string.faq_quality_a,
            R.string.faq_originals_q to R.string.faq_originals_a,
            R.string.faq_trash_q to R.string.faq_trash_a,
            R.string.faq_space_q to R.string.faq_space_a,
            R.string.faq_cloud_q to R.string.faq_cloud_a,
            R.string.faq_still_wrong_q to R.string.faq_still_wrong_a,
            R.string.faq_crop_q to R.string.faq_crop_a,
            R.string.faq_privacy_q to R.string.faq_privacy_a,
        )
        faqs.forEachIndexed { i, (q, a) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Faq(stringResource(q), stringResource(a))
        }
    }

    Section(stringResource(R.string.support_title)) {
        Text(stringResource(R.string.support_body), style = MaterialTheme.typography.bodyMedium)
        val email = stringResource(R.string.support_email)
        if (email.isNotBlank()) {
            val subject = stringResource(R.string.support_email_subject, version ?: "?")
            val body = stringResource(R.string.support_email_body, deviceInfo(version))
            GradientButton(stringResource(R.string.action_email), onClick = { sendEmail(context, email, subject, body) })
        }
        val kakao = stringResource(R.string.kakao_openchat_url)
        if (kakao.isNotBlank()) {
            OutlinedButton(shape = outlinedShape, modifier = Modifier.fillMaxWidth(), onClick = { open(context, kakao) }) {
                Text(stringResource(R.string.action_kakao))
            }
        }
        OutlinedButton(shape = outlinedShape, modifier = Modifier.fillMaxWidth(), onClick = { open(context, ISSUES_URL) }) {
            Text(stringResource(R.string.action_report))
        }
    }

    Section(stringResource(R.string.about_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.about_version), Modifier.weight(1f))
            Text(version ?: "?", fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.about_developer), Modifier.weight(1f))
            Text(stringResource(R.string.developer_name), fontWeight = FontWeight.Bold)
        }
        LinkRow(stringResource(R.string.about_privacy)) { open(context, PRIVACY_URL) }
        LinkRow(stringResource(R.string.about_terms)) { open(context, TERMS_URL) }
        LinkRow(stringResource(R.string.about_source)) { open(context, SOURCE_URL) }
        Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    OutlinedButton(shape = outlinedShape, modifier = Modifier.fillMaxWidth(), onClick = onBack) {
        Text(stringResource(R.string.action_back))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = cardColors(), elevation = cardElevation(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@Composable
private fun Faq(question: String, answer: String) {
    var open by rememberSaveable(question) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { open = !open }
            .animateContentSize()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(if (open) "−" else "+", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A card that shows only its title until tapped, for text most people can skip. */
@Composable
fun FoldCard(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(colors = cardColors(), elevation = cardElevation()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .animateContentSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(if (open) "−" else "+", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (open) content()
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
    }
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

/** "‹ Back" at the top of a screen, for people who don't use the system back gesture. */
@Composable
fun BackLink(onBack: () -> Unit) {
    Text(
        "‹  " + stringResource(R.string.action_back),
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onBack)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}
