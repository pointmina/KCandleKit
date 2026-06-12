package com.hanto.kcandlekit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hanto.kcandlekit.data.UpbitApi
import com.hanto.kcandlekit.data.UpbitCandleRepository
import com.hanto.kcandlekit.data.UpbitTickerRepository

class ChartViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChartViewModel(
            candleRepo = UpbitCandleRepository(UpbitApi.service),
            tickerRepo = UpbitTickerRepository(UpbitApi.client),
        ) as T
}
