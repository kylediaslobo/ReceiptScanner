package com.kyled.receiptscanner.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.kyled.receiptscanner.AppContainer
import com.kyled.receiptscanner.ReceiptApp

/** Lets `viewModel { MyViewModel(container()) }` reach the app's singletons. */
fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as ReceiptApp).container
