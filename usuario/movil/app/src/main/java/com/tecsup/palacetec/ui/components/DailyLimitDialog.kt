package com.tecsup.palacetec.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Sin límite diario el backend no acepta apuestas. */
@Composable
fun DailyLimitDialog(
    saving: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    val valid = AMOUNT.matches(amount)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Límite diario de juego") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Elige cuánto puedes apostar como máximo por día. " +
                        "Reducirlo es inmediato; aumentarlo demora 24 horas.",
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.replace(',', '.') },
                    label = { Text("Monto en créditos") },
                    singleLine = true,
                    enabled = !saving,
                    isError = error != null || (amount.isNotEmpty() && !valid),
                    supportingText = {
                        val message = error
                            ?: "Usa un número con hasta dos decimales.".takeIf { amount.isNotEmpty() && !valid }
                        if (message != null) Text(message)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(amount) }, enabled = valid && !saving) {
                Text(if (saving) "Guardando…" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        },
    )
}

private val AMOUNT = Regex("[0-9]{1,10}(\\.[0-9]{1,2})?")
