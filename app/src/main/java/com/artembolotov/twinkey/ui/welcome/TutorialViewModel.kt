package com.artembolotov.twinkey.ui.welcome

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artembolotov.twinkey.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TutorialViewModel : ViewModel() {

    val messages = mutableStateListOf<ChatMessage>()
    var showButtons = mutableStateOf(false)
    var showMoreInfo = mutableStateOf(false)
    var depth = mutableStateOf(0)

    private var greetingStarted = false

    fun startGreetingIfNeeded() {
        if (greetingStarted) return
        greetingStarted = true

        viewModelScope.launch {
            messages.add(ChatMessage.Typing)
            delay(1_500)
            messages.removeAt(messages.lastIndex)
            messages.add(ChatMessage.Income(R.string.tutorial_greeting_1))

            delay(600)
            messages.add(ChatMessage.Typing)
            delay(3_000)
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
            delay(1_000)
            messages.add(ChatMessage.Typing)
            delay(2_000)
            messages.removeAt(messages.lastIndex)
            messages.add(ChatMessage.Income(R.string.tutorial_more_info_text))
            showButtons.value = true
            showMoreInfo.value = false
        }
    }
}
