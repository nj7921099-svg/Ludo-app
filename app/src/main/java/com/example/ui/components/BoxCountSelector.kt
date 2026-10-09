package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ludo.model.LudoGameMode
import com.example.ui.BotOpponentMode
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.TextPrimary

@Composable
fun BoxCountSelector(
    selectedCount: Int,
    isGameStarted: Boolean,
    onCountSelected: (Int) -> Unit,
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier,
    selectedGameMode: LudoGameMode = LudoGameMode.INDIVIDUAL,
    onGameModeSelected: (LudoGameMode) -> Unit = {},
    botOpponentMode: BotOpponentMode = BotOpponentMode.ALL_HUMAN,
    botPlayerIds: Set<Int> = emptySet(),
    onOpponentModeSelected: (BotOpponentMode) -> Unit = {},
    onTogglePlayerControl: (Int) -> Unit = {}
) {
    val choices = listOf(2, 3, 4, 5, 6)
    val isTeamUp = selectedGameMode == LudoGameMode.TEAM_UP

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("box_count_selector_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0A18)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPurple)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // "GAME MODE" Title
            Text(
                text = "GAME MODE",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.2.sp,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Game Mode Segmented Selector: [Individual] [Team-Up 2v2]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("game_mode_selector_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Individual Button
                val isIndivSelected = selectedGameMode == LudoGameMode.INDIVIDUAL
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isIndivSelected) 2.dp else 1.dp,
                            color = if (isIndivSelected) Color(0xFFE9D5FF) else NeonPurple.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onGameModeSelected(LudoGameMode.INDIVIDUAL) }
                        .testTag("mode_button_individual"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isIndivSelected) Color(0xFF261845) else Color(0xFF110E1E)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Individual",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isIndivSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isIndivSelected) Color.White else Color(0xFFB3A8CA)
                            )
                        )
                    }
                }

                // Team-Up 2v2 Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isTeamUp) 2.dp else 1.dp,
                            color = if (isTeamUp) Color(0xFFE9D5FF) else NeonPurple.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onGameModeSelected(LudoGameMode.TEAM_UP) }
                        .testTag("mode_button_team_up"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isTeamUp) Color(0xFF261845) else Color(0xFF110E1E)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Team-Up 2v2",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isTeamUp) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isTeamUp) Color.White else Color(0xFFB3A8CA)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "SELECT PLAYERS" Title
            Text(
                text = "SELECT PLAYERS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.2.sp,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Row of player number buttons: [2] [3] [4] [5] [6]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                choices.forEach { count ->
                    // In TEAM_UP mode: exactly 4 players is supported; 2 and 3 are disabled.
                    // In INDIVIDUAL mode: 2, 3, and 4 players are supported.
                    val isSupported = if (isTeamUp) {
                        count == 4
                    } else {
                        count in 2..4
                    }
                    val isSelected = selectedCount == count
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = tween(150),
                        label = "player_btn_scale"
                    )

                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFFE9D5FF) else NeonPurple.copy(alpha = if (isSupported) 0.7f else 0.25f),
                        animationSpec = tween(200),
                        label = "player_btn_border"
                    )

                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF261845) else Color(0xFF110E1E),
                        animationSpec = tween(200),
                        label = "player_btn_bg"
                    )

                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor.copy(alpha = if (isSupported) 1f else 0.45f))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.5.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = isSupported) {
                                onCountSelected(count)
                            }
                            .testTag("box_count_button_$count"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = if (isSelected) Color(0xFFFFFFFF) else if (isSupported) Color(0xFFE2E8F0) else Color(0xFF6B5C82)
                                )
                            )
                            if (!isSupported) {
                                Text(
                                    text = if (isTeamUp && count in 2..3) "4P ONLY" else "P10",
                                    fontSize = if (isTeamUp && count in 2..3) 7.sp else 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7A6894)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isTeamUp) {
                    "Team-Up is a 2v2 battle: Red & Yellow vs Green & Blue (4 players required)."
                } else {
                    "Standard Ludo board supports 2, 3, or 4 players."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = Color(0xFF8E84A8)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // "OPPONENTS" Title
            Text(
                text = "OPPONENTS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.2.sp,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Opponent Mode Segmented Selector: [All Human] [vs Bots]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bot_mode_selector_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isAllHuman = botOpponentMode == BotOpponentMode.ALL_HUMAN
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isAllHuman) 2.dp else 1.dp,
                            color = if (isAllHuman) Color(0xFFE9D5FF) else NeonPurple.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onOpponentModeSelected(BotOpponentMode.ALL_HUMAN) }
                        .testTag("bot_mode_all_human"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isAllHuman) Color(0xFF261845) else Color(0xFF110E1E)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👥 All Human",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isAllHuman) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isAllHuman) Color.White else Color(0xFFB3A8CA)
                            )
                        )
                    }
                }

                val isVsBots = botOpponentMode == BotOpponentMode.VS_BOTS
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isVsBots) 2.dp else 1.dp,
                            color = if (isVsBots) Color(0xFFE9D5FF) else NeonPurple.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onOpponentModeSelected(BotOpponentMode.VS_BOTS) }
                        .testTag("bot_mode_vs_bots"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isVsBots) Color(0xFF261845) else Color(0xFF110E1E)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🤖 vs Bots",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isVsBots) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isVsBots) Color.White else Color(0xFFB3A8CA)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Player slot controls (clickable to toggle between Human and Bot)
            val effectiveCount = if (isTeamUp) 4 else selectedCount
            if (isTeamUp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Team 1
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TEAM 1 (Red + Yellow)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE9D5FF)
                        )
                        listOf(1, 3).forEach { pid ->
                            PlayerControlChip(
                                playerId = pid,
                                playerColor = if (pid == 1) Color(0xFFEF4444) else Color(0xFFFBBF24),
                                isBot = botPlayerIds.contains(pid),
                                onToggle = { onTogglePlayerControl(pid) }
                            )
                        }
                    }

                    // Team 2
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TEAM 2 (Green + Blue)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE9D5FF)
                        )
                        listOf(2, 4).forEach { pid ->
                            PlayerControlChip(
                                playerId = pid,
                                playerColor = if (pid == 2) Color(0xFF10B981) else Color(0xFF3B82F6),
                                isBot = botPlayerIds.contains(pid),
                                onToggle = { onTogglePlayerControl(pid) }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..effectiveCount).forEach { pid ->
                        val pColor = when (pid) {
                            1 -> Color(0xFFEF4444)
                            2 -> if (effectiveCount == 2) Color(0xFFFBBF24) else Color(0xFF10B981)
                            3 -> Color(0xFFFBBF24)
                            else -> Color(0xFF3B82F6)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            PlayerControlChip(
                                playerId = pid,
                                playerColor = pColor,
                                isBot = botPlayerIds.contains(pid),
                                onToggle = { onTogglePlayerControl(pid) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tap any player slot to toggle between Human and Bot.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = Color(0xFF8E84A8)
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Pill "START GAME" button
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = 1.5.dp,
                        color = NeonPurple,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        onStartGame()
                    }
                    .testTag("start_game_button"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF140F26)
            ) {
                Text(
                    text = if (isGameStarted) "RESTART GAME" else "START GAME",
                    modifier = Modifier.padding(horizontal = 42.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun PlayerControlChip(
    playerId: Int,
    playerColor: Color,
    isBot: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = if (isBot) Color(0xFF8B5CF6) else Color(0xFF4B3A6A),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onToggle() }
            .testTag("player_control_toggle_$playerId"),
        shape = RoundedCornerShape(12.dp),
        color = if (isBot) Color(0xFF23163E) else Color(0xFF120E22)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(playerColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "P$playerId: ${if (isBot) "Bot 🤖" else "Human 👤"}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isBot) FontWeight.Bold else FontWeight.Medium,
                    color = if (isBot) Color(0xFFD8B4FE) else Color(0xFFE2E8F0)
                )
            )
        }
    }
}
