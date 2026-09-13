package com.intent.screentime.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intent.screentime.core.time.DayWindow
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.TargetType
import com.intent.screentime.data.repository.AppCategoryRef
import com.intent.screentime.data.repository.UsageRepository
import com.intent.screentime.ui.apps.AppInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppDetailUiState(
    val loading: Boolean = true,
    val packageName: String = "",
    val days: List<Pair<Long, Long>> = emptyList(),
    val totalMs: Long = 0L,
    val averageMs: Long = 0L,
    val sessionCount: Int = 0,
    val todayMs: Long = 0L,
    val capMinutes: Int? = null,
    val category: AppCategoryRef? = null,
    val categories: List<CategoryEntity> = emptyList(),
) {
    val peakMs: Long get() = days.maxOfOrNull { it.second } ?: 0L
    val daysWithUse: Int get() = days.count { it.second > 0L }
}

/**
 * One app, over thirty days.
 *
 * The point of this screen is the trend, not the total: "four hours this month" is a
 * fact, but "half of it was last Tuesday" is a decision. A cap and a category live here
 * too, because this is where the user is actually thinking about that app.
 */
class AppDetailViewModel(
    private val packageName: String,
    private val repository: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val today: Long = DayWindow.todayEpochDay()
    private val fromDay: Long = today - (WINDOW_DAYS - 1)

    private val dataVersion = MutableStateFlow(0)

    val state: StateFlow<AppDetailUiState> = combine(
        repository.observeAppUsageFor(packageName, fromDay, today),
        repository.observeTargets(),
        dataVersion,
    ) { rows, targets, _ -> rows to targets }
        .map { (rows, targets) ->
            appInfo.preload(listOf(packageName))

            val byDay = rows.associate { it.dayEpochDay to it.totalMs }
            val days = (fromDay..today).map { day -> day to (byDay[day] ?: 0L) }
            val total = rows.sumOf { it.totalMs }
            val used = rows.count { it.totalMs > 0L }

            val cap = targets
                .firstOrNull {
                    it.type == TargetType.PER_APP_DAILY_CAP && it.scopePackage == packageName
                }
                ?.valueMinutes

            AppDetailUiState(
                loading = false,
                packageName = packageName,
                days = days,
                totalMs = total,
                averageMs = if (used == 0) 0L else total / used,
                sessionCount = rows.sumOf { it.sessionCount },
                todayMs = byDay[today] ?: 0L,
                capMinutes = cap,
                category = repository.categoryLookup()[packageName],
                categories = repository.allCategories(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState())

    fun setCap(minutes: Int?) {
        viewModelScope.launch {
            repository.setPerAppCap(packageName, minutes)
            dataVersion.value += 1
        }
    }

    fun setCategory(categoryId: String) {
        viewModelScope.launch {
            repository.setAppCategory(packageName, categoryId)
            dataVersion.value += 1
        }
    }

    private companion object {
        const val WINDOW_DAYS = 30L
    }
}
