package com.artembolotov.twinkey.ui.welcome

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artembolotov.twinkey.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The typing pauses only exist to make the chat feel friendly; they are not
 * meant to imitate the real pace of a person typing, so keep them short.
 */
private const val TYPING_SHORT_MS = 600L
private const val TYPING_LONG_MS = 1_000L
private const val PAUSE_BETWEEN_MESSAGES_MS = 300L
private const val PAUSE_BEFORE_REPLY_MS = 400L

class TutorialViewModel : ViewModel() {

    val messages = mutableStateListOf<ChatMessage>()
    var showButtons = mutableStateOf(false)
    var showMoreInfo = mutableStateOf(false)
    var depth = mutableStateOf(0)

    private var greetingJob: Job? = null

    fun reset() {
        greetingJob?.cancel()
        greetingJob = null
        messages.clear()
        showButtons.value = false
        showMoreInfo.value = false
        depth.value = 0
    }

    fun startGreeting() {
        greetingJob = viewModelScope.launch {
            messages.add(ChatMessage.Typing)
            delay(TYPING_SHORT_MS)
            messages.removeAt(messages.lastIndex)
            messages.add(ChatMessage.Income(R.string.tutorial_greeting_1))

            delay(PAUSE_BETWEEN_MESSAGES_MS)
            messages.add(ChatMessage.Typing)
            delay(TYPING_LONG_MS)
            messages.removeAt(messages.lastIndex)
            messages.add(ChatMessage.Income(R.string.tutorial_greeting_2))
            showButtons.value = true
        }
    }

    fun requestMoreInfo() {
        if (showMoreInfo.value) return
        depth.value = 1
        showButtons.value = false
        showMoreInfo.value = true
        messages.add(ChatMessage.Outcome(R.string.tutorial_more_about_2fa))

        viewModelScope.launch {
            delay(PAUSE_BEFORE_REPLY_MS)
            messages.add(ChatMessage.Typing)
            delay(TYPING_SHORT_MS)
            messages.removeAt(messages.lastIndex)
            messages.add(ChatMessage.Income(R.string.tutorial_more_info_text))
            showButtons.value = true
            showMoreInfo.value = false
        }
    }
}
