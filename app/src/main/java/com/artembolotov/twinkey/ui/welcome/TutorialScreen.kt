package com.artembolotov.twinkey.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artembolotov.twinkey.R
import kotlinx.coroutines.delay

@Composable
fun TutorialScreen(
    sessionId: Int = 0,
    onGetStarted: () -> Unit,
    vm: TutorialViewModel = viewModel()
) {
    val messages = vm.messages
    val showButtons by vm.showButtons
    val depth by vm.depth
    val listState = rememberLazyListState()

    LaunchedEffect(sessionId) {
        vm.reset()
        vm.startGreeting()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

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
                Button(onClick = {
                    vm.showButtons.value = false
                    messages.add(ChatMessage.Outcome(R.string.tutorial_get_started))
                    onGetStarted()
                }) {
                    Text(stringResource(R.string.tutorial_get_started))
                }

                if (depth < 1) {
                    TextButton(onClick = { vm.requestMoreInfo() }) {
                        Text(stringResource(R.string.tutorial_more_about_2fa))
                    }
                }
            }
        }
    }
}

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
