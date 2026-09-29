package com.intent.screentime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.intent.screentime.data.goals.IntentScore
import com.intent.screentime.ui.theme.Spacing
import com.intent.screentime.ui.theme.dataColors

/**
 * One row of the score's own explanation: what this part was worth, in words.
 *
 * The wording is the caller's, because "your 4h cap" means something different on a day
 * card than on a month, but the label, the icon and the tone come from here so the five
 * rows can never be described two different ways.
 */
data class ScoreDetail(
    val kind: IntentScore.ContributionKind,
    val tone: IntentScore.ContributionTone,
    val text: String,
    val pointsText: String,
)

/**
 * The composite score, and the sentence that explains it.
 *
 * A single number is only motivating if the reader knows what moved it, so the ring is
 * always accompanied by the five rows it is made of: each one in points earned out of
 * points available, so the parts visibly add up to the whole. The same panel serves today,
 * a past day, and a whole window — on a window the rows are the means of the days' rows,
 * and the points still add up.
 *
 * [footnote] is where a window says which days carried it and which dragged, since one
 * average hides both.
 */
@Composable
fun IntentScorePanel(
    score: Int,
    verdict: String,
    explanation: String,
    contributions: List<IntentScore.Contribution>,
    details: List<ScoreDetail>,
    fileName: String,
    title: String = "Intent Score",
    subtitle: String? = null,
    footnote: String? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val data = dataColors
    var detailsShown by rememberSaveable { mutableStateOf(false) }

    CapturablePanel(
        title = title,
        subtitle = subtitle,
        fileName = fileName,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ProgressRing(
                progress = score / 100f,
                diameter = 96.dp,
                strokeWidth = 11.dp,
                color = when {
                    score >= 70 -> data.production
                    score >= 45 -> scheme.primary
                    else -> scheme.error
                },
            ) {
                Text(
                    text = score.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onSurface,
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = verdict,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        ScoreBreakdown(contributions)

        if (details.isNotEmpty()) {
            TextButton(
                onClick = { detailsShown = !detailsShown },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (detailsShown) Icons.Filled.Info else Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(Spacing.xs))
                Text(if (detailsShown) "Hide score details" else "See what moved this score")
            }
            if (detailsShown) {
                ScoreDetails(details)
            }
        }
    }
}

/** The five rows the score is made of, each in its own points rather than a percentage. */
@Composable
private fun ScoreBreakdown(contributions: List<IntentScore.Contribution>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        contributions.forEach { contribution ->
            ScoreRow(contribution)
        }
    }
}

@Composable
private fun ScoreRow(contribution: IntentScore.Contribution) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (contribution.available > 0) {
        contribution.earned.toFloat() / contribution.available
    } else {
        0f
    }
    val tint = contributionToneColor(contribution.tone)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = contribution.kind.detailLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${contribution.earned} / ${contribution.available} pts",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(scheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint),
            )
        }
    }
}

@Composable
private fun ScoreDetails(details: List<ScoreDetail>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        details.forEach { detail ->
            val tint = contributionToneColor(detail.tone)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = when (detail.tone) {
                        IntentScore.ContributionTone.POSITIVE -> Icons.Filled.CheckCircle
                        IntentScore.ContributionTone.NEGATIVE -> Icons.Filled.ErrorOutline
                        IntentScore.ContributionTone.NEUTRAL -> Icons.Filled.Info
                    },
                    contentDescription = when (detail.tone) {
                        IntentScore.ContributionTone.POSITIVE -> "Helped"
                        IntentScore.ContributionTone.NEGATIVE -> "Not fully earned"
                        IntentScore.ContributionTone.NEUTRAL -> "Neutral"
                    },
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = detail.kind.detailLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        color = tint,
                    )
                    Text(
                        text = detail.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = detail.pointsText,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
        }
    }
}

@Composable
private fun contributionToneColor(tone: IntentScore.ContributionTone) = when (tone) {
    IntentScore.ContributionTone.POSITIVE -> dataColors.positive
    IntentScore.ContributionTone.NEGATIVE -> dataColors.negative
    IntentScore.ContributionTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** What each row of the score is called, wherever it is shown. */
fun IntentScore.ContributionKind.detailLabel(): String = when (this) {
    IntentScore.ContributionKind.PRODUCTION -> "Producing"
    IntentScore.ContributionKind.CAP -> "Under your cap"
    IntentScore.ContributionKind.BEDTIME -> "Bedtime"
    IntentScore.ContributionKind.FOCUS -> "Focus"
    IntentScore.ContributionKind.STREAK -> "Streak"
}

private fun IntentScore.Contribution.detailLabel(): String = kind.detailLabel()

/** "12 points earned" reads honestly for the row that earned them. */
fun IntentScore.Contribution.pointsText(): String {
    val missing = available - earned
    return when (tone) {
        IntentScore.ContributionTone.POSITIVE -> "$earned points earned"
        IntentScore.ContributionTone.NEGATIVE -> "$missing points not earned"
        IntentScore.ContributionTone.NEUTRAL -> "$earned points held neutral"
    }
}
