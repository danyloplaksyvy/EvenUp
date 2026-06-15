package com.dps.evenup.core.designsystem.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable

@Preview(showBackground = true)
@Composable
private fun EvenUpButtonsPreview() {
    EvenUpTheme {
        Column(
            modifier = Modifier.padding(EvenUpTheme.spacing.space16),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            EvenUpPrimaryButton(text = "Continue", onClick = {})
            EvenUpSecondaryButton(text = "Enter manually", onClick = {})
            EvenUpTextButton(text = "Skip for now", onClick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EvenUpInputsAndStatesPreview() {
    EvenUpTheme {
        Column(
            modifier = Modifier.padding(EvenUpTheme.spacing.space16),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            EvenUpTextField(value = "Cafe Luna", onValueChange = {}, label = "Merchant")
            EvenUpMoneyField(value = "42.18", onValueChange = {}, label = "Total")
            EvenUpValidationMessage(message = "Assign every item before continuing.")
            EvenUpCard {
                Row(horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
                    EvenUpParticipantChip(name = "Ari", selected = true)
                    EvenUpParticipantChip(name = "Sam", colorIndex = 1)
                }
            }
        }
    }
}
