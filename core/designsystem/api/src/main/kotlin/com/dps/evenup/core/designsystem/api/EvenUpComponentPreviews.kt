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
                    EvenUpParticipantChip(name = "Sam", colorIndex = 1, selected = true)
                    EvenUpParticipantChip(name = "Max", colorIndex = 2)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EvenUpReceiptAndSettlementPreview() {
    EvenUpTheme {
        Column(
            modifier = Modifier.padding(EvenUpTheme.spacing.space16),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            EvenUpReceiptItemsCard(
                title = "Bella Roma",
                subtitle = "Tue, May 14",
                items = listOf(
                    {
                        EvenUpReceiptItemRow(
                            itemName = "Margherita Pizza",
                            quantityLabel = "1x",
                            unitPriceLabel = "EUR 14.00 each",
                            totalLabel = "EUR 14.00",
                            state = EvenUpReceiptItemState.Assigned,
                            assignees = listOf(EvenUpReceiptAssignee(name = "You")),
                        )
                    },
                    {
                        EvenUpReceiptItemRow(
                            itemName = "Truffle Pasta",
                            quantityLabel = "2x",
                            unitPriceLabel = "EUR 22.00 each",
                            totalLabel = "EUR 44.00",
                            state = EvenUpReceiptItemState.Shared,
                            assignees = listOf(
                                EvenUpReceiptAssignee(name = "Anna", colorIndex = 1, detail = "Anna"),
                                EvenUpReceiptAssignee(name = "Ben", colorIndex = 2, detail = "Ben"),
                            ),
                        )
                    },
                    {
                        EvenUpReceiptItemRow(
                            itemName = "Tiramisu",
                            totalLabel = "EUR 9.50",
                            state = EvenUpReceiptItemState.Unassigned,
                        )
                    },
                ),
            )
            EvenUpSummaryCard(
                title = "Bella Roma",
                subtitle = "Total",
                totalLabel = "EUR 112.50",
                payerLabel = "Paid by",
                payerName = "You",
            ) {
                EvenUpSettlementRow(
                    participantName = "Anna",
                    relationLabel = "owes you",
                    amountLabel = "EUR 31.90",
                    colorIndex = 1,
                )
            }
            EvenUpExpandableDetailsCard(
                title = "Calculation details",
                leadingLabel = "Tax + tip",
                expanded = true,
                onExpandedChange = {},
            ) {
                EvenUpSettlementRow(
                    participantName = "Anna",
                    relationLabel = "total share",
                    amountLabel = "EUR 31.90",
                    colorIndex = 1,
                )
            }
        }
    }
}
