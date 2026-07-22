package com.dps.evenup.domain.expenseinput.api

interface PrepareAiExpenseUseCase {
    fun prepare(command: PrepareAiExpenseCommand): PrepareAiExpenseResult
}
