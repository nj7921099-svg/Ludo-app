package com.example.ui.components.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.game.ludo.model.LudoColor
import com.example.game.ludo.model.LudoGameMode
import com.example.game.ludo.model.LudoTeamId
import com.example.game.ludo.stats.model.LudoStatisticsData
import com.example.game.ludo.stats.model.MatchHistoryRecord
import com.example.game.ludo.stats.model.PlayerMatchContribution
import com.example.game.ludo.stats.model.PlayerStatistics
import com.example.ui.components.ludo.LudoThemeColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonDarkCard
import com.example.ui.theme.NeonDarkCardElevated
import com.example.ui.theme.NeonGreenBright
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean, premium, dark-themed Statistics & Match History dialog.
 * Features tabs for:
 * 1. Player Statistics (P1..P4 slot identities)
 * 2. Match History (Newest-first list with expandable player contributions)
 */
@Composable
fun StatisticsDialog(
    statisticsData: LudoStatisticsData,
    onClearAllStatistics: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val tabs = listOf("Player Stats", "Match History")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .height(660.dp)
                .testTag("statistics_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeonDarkCard),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPurple)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF261942)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = NeonPurpleBright,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "STATISTICS & HISTORY",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Authoritative Lifetime Metrics",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_stats_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Segmented Tabs: Player Stats vs Match History
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF0A0716),
                    contentColor = TextPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = NeonPurpleBright,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = if (selectedTab == index) NeonPurpleBright else TextSecondary
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = if (index == 0) Icons.Default.Person else Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (selectedTab == index) NeonPurpleBright else TextSecondary
                                )
                            },
                            modifier = Modifier.testTag("stats_tab_$index")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> PlayerStatisticsTab(
                            playerStatsMap = statisticsData.playerStats,
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> MatchHistoryTab(
                            matchHistory = statisticsData.matchHistory,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action: Clear All Statistics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${statisticsData.matchHistory.size} matches on record",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    )

                    OutlinedButton(
                        onClick = { showClearConfirmation = true },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                        modifier = Modifier.testTag("clear_stats_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear Stats",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog before wiping statistics
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = {
                Text(
                    text = "Clear Statistics?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all saved statistics and match history.",
                    color = TextSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearAllStatistics()
                    },
                    modifier = Modifier.testTag("confirm_clear_stats_button")
                ) {
                    Text(
                        text = "Clear All",
                        color = NeonRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmation = false },
                    modifier = Modifier.testTag("cancel_clear_stats_button")
                ) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary
                    )
                }
            },
            containerColor = NeonDarkCardElevated,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

/**
 * Tab 1: Displays P1..P4 slot statistics cards with win rates and detailed gameplay metrics.
 */
@Composable
fun PlayerStatisticsTab(
    playerStatsMap: Map<Int, PlayerStatistics>,
    modifier: Modifier = Modifier
) {
    val totalGamesPlayed = playerStatsMap.values.sumOf { it.gamesPlayed }

    if (totalGamesPlayed == 0) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("player_stats_empty_view"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F1735)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = NeonPurpleBright,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No matches played yet. Start a game to record stats!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        return
    }

    val slots = listOf(
        Triple(1, "P1 (Red)", LudoColor.RED),
        Triple(2, "P2 (Green)", LudoColor.GREEN),
        Triple(3, "P3 (Yellow)", LudoColor.YELLOW),
        Triple(4, "P4 (Blue)", LudoColor.BLUE)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("player_stats_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(slots) { (playerId, label, color) ->
            val stats = playerStatsMap[playerId] ?: PlayerStatistics(playerId = playerId)
            PlayerSlotCard(
                label = label,
                color = color,
                stats = stats
            )
        }
    }
}

@Composable
fun PlayerSlotCard(
    label: String,
    color: LudoColor,
    stats: PlayerStatistics,
    modifier: Modifier = Modifier
) {
    val themeColor = LudoThemeColors.getPrimaryColor(color)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_card_${stats.playerId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0A1E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Player Color Dot + Name + Win Rate Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(themeColor)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    )
                }

                // Win Rate Pill
                val winRate = stats.winRatePercent
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1533))
                        .border(1.dp, NeonPurpleBright.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Win Rate: ${"%.1f".format(Locale.US, winRate)}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (winRate >= 50f) NeonGreenBright else Color(0xFFE2E8F0)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Games Summary: Played / Won + Linear Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Games Played: ${stats.gamesPlayed}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = "Won: ${stats.gamesWon}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (stats.gamesWon > 0) NeonGreenBright else TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val progress = if (stats.gamesPlayed > 0) (stats.gamesWon.toFloat() / stats.gamesPlayed).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = themeColor,
                trackColor = Color(0xFF221A38),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Detailed Metric Grid (2 columns x 3 rows)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "Tokens Finished",
                    value = stats.tokensFinished.toString(),
                    highlightColor = NeonGreenBright
                )
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "Tokens Captured",
                    value = stats.tokensCaptured.toString(),
                    highlightColor = NeonAmber
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "Times Captured",
                    value = stats.timesCaptured.toString(),
                    highlightColor = NeonRed
                )
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "Sixes Rolled",
                    value = stats.sixesRolled.toString(),
                    highlightColor = NeonCyan
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "Extra Turns",
                    value = stats.extraTurnsGranted.toString(),
                    highlightColor = NeonPurpleBright
                )
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "Total Moves",
                    value = stats.totalMoves.toString(),
                    highlightColor = TextPrimary
                )
            }

            // Team-Up Subsection (if player has participated in Team-Up matches)
            if (stats.teamUpMatchesPlayed > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF140F2B))
                        .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = NeonPurpleBright,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Team-Up 2v2",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = NeonPurpleBright
                                )
                            )
                        }
                        Text(
                            text = "Matches: ${stats.teamUpMatchesPlayed} | Won: ${stats.teamUpMatchesWon}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatMetricBox(
    title: String,
    value: String,
    highlightColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141026))
            .border(1.dp, Color(0xFF261D45), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = highlightColor
                )
            )
        }
    }
}

/**
 * Tab 2: Displays completed match history records (newest-first) with expandable player breakdowns.
 */
@Composable
fun MatchHistoryTab(
    matchHistory: List<MatchHistoryRecord>,
    modifier: Modifier = Modifier
) {
    if (matchHistory.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("match_history_empty_view"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F1735)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = NeonPurpleBright,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No completed matches recorded yet.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        return
    }

    // Expandable item state tracker
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("match_history_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(matchHistory, key = { it.matchId }) { record ->
            val isExpanded = expandedStates[record.matchId] ?: false
            MatchHistoryCard(
                record = record,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedStates[record.matchId] = !isExpanded
                }
            )
        }
    }
}

@Composable
fun MatchHistoryCard(
    record: MatchHistoryRecord,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTeamUp = record.gameMode == LudoGameMode.TEAM_UP
    val formattedDuration = formatDuration(record.durationSeconds)
    val formattedDate = remember(record.timestamp) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(record.timestamp))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200))
            .testTag("match_card_${record.matchId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0A21)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2254))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Date/Time + Game Mode Badge + Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Game Mode Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTeamUp) Color(0xFF281442) else Color(0xFF14243B))
                            .border(1.dp, if (isTeamUp) NeonPurpleBright else NeonCyan, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isTeamUp) "TEAM-UP 2v2" else "${record.playerCount}P INDIVIDUAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                color = if (isTeamUp) NeonPurpleBright else NeonCyan
                            )
                        )
                    }
                }

                Text(
                    text = "⏱ $formattedDuration",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = Color(0xFFE2E8F0)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Winner Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Winner",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isTeamUp) {
                        val teamName = when (record.winningTeamId) {
                            LudoTeamId.TEAM_1 -> "Team 1 (P1 + P3)"
                            LudoTeamId.TEAM_2 -> "Team 2 (P2 + P4)"
                            null -> "Team"
                        }
                        Text(
                            text = "Winning Team: $teamName",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color(0xFFFFE57F)
                            )
                        )
                    } else {
                        val winP = record.winningPlayerId ?: 1
                        Text(
                            text = "Winner: P$winP",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color(0xFFFFE57F)
                            )
                        )
                    }
                }

                // Expand / Collapse Action
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleExpand() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Hide" else "Details",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = NeonPurpleBright
                        )
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = NeonPurpleBright,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expandable Player Contribution Breakdown
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090616))
                        .border(1.dp, Color(0xFF221740), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PLAYER CONTRIBUTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.8.sp,
                            color = TextSecondary
                        )
                    )

                    record.players.forEach { contribution ->
                        PlayerContributionRow(contribution = contribution)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerContributionRow(
    contribution: PlayerMatchContribution,
    modifier: Modifier = Modifier
) {
    val themeColor = LudoThemeColors.getPrimaryColor(contribution.color)
    val rankText = when (contribution.finishRank) {
        1 -> "🥇 1st"
        2 -> "🥈 2nd"
        3 -> "🥉 3rd"
        4 -> "4th"
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF120E26))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(themeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Player ${contribution.playerId}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                )
                if (contribution.isWinner) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "👑 Winner",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = Color(0xFFFFD700)
                        )
                    )
                }
            }

            if (rankText != null) {
                Text(
                    text = rankText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFFFFE57F)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Metrics chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Finished: ${contribution.tokensFinished}/4",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = if (contribution.tokensFinished > 0) NeonGreenBright else TextSecondary
                )
            )
            Text(
                text = "Caps: ${contribution.tokensCaptured}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = NeonAmber
                )
            )
            Text(
                text = "Lost: ${contribution.timesCaptured}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = if (contribution.timesCaptured > 0) NeonRed else TextSecondary
                )
            )
            Text(
                text = "6s: ${contribution.sixesRolled}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = NeonCyan
                )
            )
            Text(
                text = "Moves: ${contribution.movesCount}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            )
        }
    }
}

/**
 * Clean compact duration formatter: mm:ss, or 00:ss for under a minute.
 */
fun formatDuration(durationSeconds: Long): String {
    val safeSeconds = durationSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60
    val secs = safeSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, secs)
}
