package com.caskfive.pingcheck.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun NumericTextField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var textValue by remember { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        if (textValue.toIntOrNull() != value) {
            textValue = value.toString()
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            if (newText.isEmpty()) {
                textValue = newText
            } else {
                newText.toIntOrNull()?.let {
                    textValue = newText
                    onValueChange(it)
                }
            }
        },
        modifier = modifier.onFocusChanged { focusState ->
            if (!focusState.isFocused && textValue.toIntOrNull() == null) {
                textValue = value.toString()
            }
        },
        label = label,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
fun NumericTextField(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var textValue by remember { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        if (textValue.toFloatOrNull() != value) {
            textValue = value.toString()
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            if (newText.isEmpty() || newText == "." || newText.endsWith(".")) {
                textValue = newText
                newText.toFloatOrNull()?.let { onValueChange(it) }
            } else {
                newText.toFloatOrNull()?.let {
                    textValue = newText
                    onValueChange(it)
                }
            }
        },
        modifier = modifier.onFocusChanged { focusState ->
            if (!focusState.isFocused && textValue.toFloatOrNull() == null) {
                textValue = value.toString()
            }
        },
        label = label,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
