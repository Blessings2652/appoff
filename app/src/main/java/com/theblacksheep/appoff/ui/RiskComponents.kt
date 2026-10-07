package com.theblacksheep.appoff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblacksheep.appoff.core.AppRecommendation
import com.theblacksheep.appoff.core.PackageRisk
import com.theblacksheep.appoff.core.RecommendedAction
import com.theblacksheep.appoff.core.RiskLevel

fun riskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.SAFE -> Color(0xFF00E676)
    RiskLevel.CAUTION -> Color(0xFFFFD600)
    RiskLevel.ADVANCED -> Color(0xFFFF9100)
    RiskLevel.PROTECTED -> Color(0xFFFF1744)
}

@Composable
fun RiskBadge(risk: PackageRisk, modifier: Modifier = Modifier) {
    val c = riskColor(risk.level)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = c.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text("${risk.level.label} \u00B7 ${risk.score}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c)
        }
    }
}

@Composable
private fun SignalLines(risk: PackageRisk, max: Int = 8) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        risk.signals.take(max).forEach { s ->
            val pts = if (s.points > 0) "+${s.points}" else "${s.points}"
            Row {
                Text(
                    pts,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (s.points > 0) riskColor(RiskLevel.ADVANCED) else riskColor(RiskLevel.SAFE),
                    modifier = Modifier.width(34.dp)
                )
                Text(s.text, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }
        }
        if (risk.dependents.isNotEmpty()) {
            val names = risk.dependents.take(3).joinToString(", ")
            val more = if (risk.dependents.size > 3) " +${risk.dependents.size - 3} more" else ""
            Text(
                "Used by: $names$more",
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Full card used in the app details dialog. */
@Composable
fun RiskDetailsCard(risk: PackageRisk) {
    val c = riskColor(risk.level)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = c.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${risk.level.label}  \u00B7  risk ${risk.score}/100",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = c
                )
            }
            Text(
                risk.level.summary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            SignalLines(risk)
        }
    }
}

/** Compact line under a recommended app: badge, suggested action, reason, tap for the evidence. */
@Composable
fun RecommendationMeta(rec: AppRecommendation) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = 16.dp, end = 16.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RiskBadge(rec.risk)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (rec.action == RecommendedAction.DISABLE) "DISABLE" else "UNINSTALL",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = rec.reason,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = if (expanded) 4 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (expanded) {
            SignalLines(rec.risk)
        }
    }
}
