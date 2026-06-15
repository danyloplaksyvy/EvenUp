package com.dps.evenup

import androidx.lifecycle.ViewModel
import com.dps.evenup.domain.sharing.api.GetStartupMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    getStartupMessage: GetStartupMessageUseCase
) : ViewModel() {
    val startupMessage: String = getStartupMessage()
}
