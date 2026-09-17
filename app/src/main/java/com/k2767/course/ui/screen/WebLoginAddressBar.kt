package com.k2767.course.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.k2767.course.ui.system.GlassTextField
import com.k2767.course.ui.system.SystemIconButton

@Composable
fun WebLoginAddressBar(currentUrl: String, onNavigate: (String) -> Unit, onSearchSchool: () -> Unit) {
    var address by rememberSaveable { mutableStateOf(currentUrl) }
    val focus = LocalFocusManager.current
    LaunchedEffect(currentUrl) { address = currentUrl }
    fun navigate() {
        onNavigate(address)
        focus.clearFocus()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassTextField(
            value = address,
            onValueChange = { address = it },
            placeholder = "网址或搜索关键词",
            modifier = Modifier.weight(1f).semantics { contentDescription = "网址或搜索关键词" },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { navigate() }),
            trailing = { SystemIconButton(Icons.AutoMirrored.Filled.ArrowForward, "前往", ::navigate) }
        )
        SystemIconButton(Icons.Default.Search, "搜索学校", {
            focus.clearFocus()
            onSearchSchool()
        })
    }
}
