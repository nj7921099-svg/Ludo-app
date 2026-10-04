package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.BoxState
import com.example.ui.theme.NeonDarkCard
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.TextMuted

@Composable
fun GameArena(
    boxes: List<BoxState>,
    activeBoxCount: Int,
    onBoxTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val boxMap = boxes.associateBy { it.boxId }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("game_arena_container"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0A18)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPurple)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            when (activeBoxCount) {
                2 -> ArenaTwoPlayers(boxMap = boxMap, onBoxTap = onBoxTap)
                3 -> ArenaThreePlayers(boxMap = boxMap, onBoxTap = onBoxTap)
                4 -> ArenaFourPlayers(boxMap = boxMap, onBoxTap = onBoxTap)
                5 -> ArenaFivePlayers(boxMap = boxMap, onBoxTap = onBoxTap)
                6 -> ArenaSixPlayers(boxMap = boxMap, onBoxTap = onBoxTap)
                else -> ArenaFourPlayers(boxMap = boxMap, onBoxTap = onBoxTap)
            }

            // Decorative star in bottom right corner (as in reference image)
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NeonPurple.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 4.dp)
                    .size(22.dp)
            )
        }
    }
}

@Composable
private fun ArrowText(symbol: String) {
    Text(
        text = symbol,
        color = Color(0xFF4A3C6B),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ArenaTwoPlayers(
    boxMap: Map<Int, BoxState>,
    onBoxTap: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[1]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(1) }) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArrowText("▶")
                Spacer(modifier = Modifier.height(16.dp))
                ArrowText("◀")
            }
            boxMap[2]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(2) }) }
        }
    }
}

@Composable
private fun ArenaThreePlayers(
    boxMap: Map<Int, BoxState>,
    onBoxTap: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[1]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(1) }) }
            ArrowText("▶")
            boxMap[2]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(2) }) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.width(60.dp))
            ArrowText("▲")
            Spacer(modifier = Modifier.weight(1f))
            ArrowText("▼")
            Spacer(modifier = Modifier.width(60.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            boxMap[3]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(3) }) }
        }
    }
}

@Composable
private fun ArenaFourPlayers(
    boxMap: Map<Int, BoxState>,
    onBoxTap: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Row: R1 (left) -> R2 (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[1]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(1) }) }
            ArrowText("▶")
            boxMap[2]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(2) }) }
        }

        // Vertical Arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(145.dp), contentAlignment = Alignment.Center) {
                ArrowText("▲")
            }
            Box(modifier = Modifier.width(145.dp), contentAlignment = Alignment.Center) {
                ArrowText("▼")
            }
        }

        // Bottom Row: R4 (left) <- R3 (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[4]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(4) }) }
            ArrowText("◀")
            boxMap[3]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(3) }) }
        }
    }
}

/**
 * Exact match to the user's reference image for 5 players!
 * Top row: R1, R2
 * Middle row: R3, R4
 * Bottom row: R5
 */
@Composable
private fun ArenaFivePlayers(
    boxMap: Map<Int, BoxState>,
    onBoxTap: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: R1 (left) and R2 (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[1]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(1) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ArrowText("▶")
                ArrowText("▶")
            }
            boxMap[2]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(2) }) }
        }

        // Vertical arrows below row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(145.dp), contentAlignment = Alignment.Center) {
                ArrowText("▲")
            }
            Box(modifier = Modifier.width(145.dp), contentAlignment = Alignment.Center) {
                ArrowText("▼")
            }
        }

        // Row 2: R3 (left) and R4 (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[3]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(3) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ArrowText("▶")
                ArrowText("◀")
            }
            boxMap[4]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(4) }) }
        }

        // Vertical arrow between Row 2 and R5
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ArrowText("▲")
        }

        // Row 3: R5 centered below
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArrowText("◀")
            Spacer(modifier = Modifier.width(10.dp))
            boxMap[5]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(5) }) }
            Spacer(modifier = Modifier.width(26.dp))
        }
    }
}

@Composable
private fun ArenaSixPlayers(
    boxMap: Map<Int, BoxState>,
    onBoxTap: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: R1 (left) -> R2 (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[1]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(1) }) }
            ArrowText("▶")
            boxMap[2]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(2) }) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(145.dp), contentAlignment = Alignment.Center) { ArrowText("▲") }
            Box(modifier = Modifier.width(145.dp), contentAlignment = Alignment.Center) { ArrowText("▼") }
        }

        // Row 2: R6 (left) and R3 (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[6]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(6) }) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ArrowText("▲")
                ArrowText("▼")
            }
            boxMap[3]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(3) }) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.width(145.dp), contentAlignment = Alignment.Center) { ArrowText("▲") }
            Box(modifier = Modifier.width(145.dp), contentAlignment = Alignment.Center) { ArrowText("▼") }
        }

        // Row 3: R5 (left) <- R4 (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            boxMap[5]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(5) }) }
            ArrowText("◀")
            boxMap[4]?.let { BoxCard(box = it, onBoxTap = { onBoxTap(4) }) }
        }
    }
}
