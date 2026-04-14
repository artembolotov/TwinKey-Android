package com.artembolotov.twinkey.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.artembolotov.twinkey.R
import kotlinx.coroutines.delay

/**
 * Порт TutorialScreen.swift.
 *
 * Чат-интерфейс онбординга. Сообщения появляются по очереди с задержкой.
 * Кнопки "Начать" и "Подробнее о 2FA".
 */
@Composable
fun TutorialScreen(
    onGetStarted: () -> Unit
) {
    // Порт: @State private var messages = [Message]()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var showButtons by remember { mutableStateOf(false) }
    var showMoreInfo by remember { mutableStateOf(false) }
    var depth by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    // Прокрутка вниз при новом сообщении — порт onChange(of: messages)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Порт greeting() async
    LaunchedEffect(Unit) {
        messages.add(ChatMessage.Typing)
        delay(1_500)
        messages.removeAt(messages.lastIndex)
        messages.add(ChatMessage.Income(R.string.tutorial_greeting_1))

        delay(600)
        messages.add(ChatMessage.Typing)
        delay(3_000)
        messages.removeAt(messages.lastIndex)
        messages.add(ChatMessage.Income(R.string.tutorial_greeting_2))
        showButtons = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                when (msg) {
                    is ChatMessage.Typing -> TypingBubble()
                    is ChatMessage.Income -> MessageBubble(
                        text = stringResource(msg.textRes),
                        isOutcome = false
                    )
                    is ChatMessage.Outcome -> MessageBubble(
                        text = stringResource(msg.textRes),
                        isOutcome = true
                    )
                }
            }
        }

        AnimatedVisibility(visible = showButtons, enter = fadeIn()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Порт Button("TutorialScreen.Commands.GetStarted")
                Button(onClick = {
                    showButtons = false
                    messages.add(ChatMessage.Outcome(R.string.tutorial_get_started))
                    onGetStarted()
                }) {
                    Text(stringResource(R.string.tutorial_get_started))
                }

                // Порт Button("TutorialScreen.Commands.More")
                if (depth < 1 && !showMoreInfo) {
                    TextButton(onClick = {
                        depth = 1
                        showButtons = false
                        showMoreInfo = true
                        messages.add(ChatMessage.Outcome(R.string.tutorial_more_about_2fa))
                    }) {
                        Text(stringResource(R.string.tutorial_more_about_2fa))
                    }
                }
            }
        }
    }

    // Порт moreAbout2FA() async
    if (showMoreInfo) {
        LaunchedEffect(Unit) {
            delay(1_000)
            messages.add(ChatMessage.Typing)
            delay(2_000)
            messages.removeAt(messages.lastIndex)
            messages.add(ChatMessage.Income(R.string.tutorial_more_info_text))
            showButtons = true
            showMoreInfo = false
        }
    }
}

// Порт Message enum из TutorialScreen.swift
sealed class ChatMessage {
    object Typing : ChatMessage()
    data class Income(val textRes: Int) : ChatMessage()
    data class Outcome(val textRes: Int) : ChatMessage()
}

@Composable
private fun MessageBubble(text: String, isOutcome: Boolean) {
    val bgColor = if (isOutcome) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isOutcome) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutcome) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bgColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = text, color = textColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TypingBubble() {
    // Порт IsTypingView — упрощённо
    var dots by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dots = when (dots.length) { 3 -> ""; else -> dots + "." }
        }
    }
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.tutorial_typing) + dots,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
