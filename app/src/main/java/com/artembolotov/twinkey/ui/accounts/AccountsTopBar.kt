package com.artembolotov.twinkey.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.artembolotov.twinkey.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsTopBar(
    visible: Boolean,
    editMode: Boolean,
    pageBackground: Color,
    scrollBehavior: TopAppBarScrollBehavior,
    onDoneClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        MaterialTheme(
            typography = MaterialTheme.typography.copy(
                headlineSmall = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp
                )
            )
        ) {
            MediumTopAppBar(
                title = { Text("TwinKey") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageBackground,
                    scrolledContainerColor = pageBackground
                ),
                actions = {
                    if (editMode) {
                        TextButton(onClick = onDoneClick) {
                            Text(
                                stringResource(R.string.accounts_done),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                }
            )
        }
    }
}
