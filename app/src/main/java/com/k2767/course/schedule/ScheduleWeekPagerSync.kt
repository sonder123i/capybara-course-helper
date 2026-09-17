package com.k2767.course.schedule

/** Calendar changes request a page; settled user swipes only report the page for restoration. */
internal class ScheduleWeekPagerSync(initialWeek: Int, initialRequestKey: String?) {
    private var requestKey = initialRequestKey
    private var requestedWeek = initialWeek.coerceIn(1, ScheduleMaxWeeks)
    private var pendingWeek: Int? = requestedWeek
    private var lastSettledWeek: Int? = null
    private var lastReportedWeek: Int? = null

    fun requestPage(week: Int, key: String?): Int? {
        val requested = week.coerceIn(1, ScheduleMaxWeeks)
        // Callers without a calendar key can still request a week directly.
        val directRequest = key == null && requested != requestedWeek && requested != lastReportedWeek
        if (key != requestKey || directRequest) pendingWeek = requested
        requestKey = key
        requestedWeek = requested
        return pendingWeek?.minus(1)
    }

    fun settledWeek(page: Int): Int? {
        val week = (page + 1).coerceIn(1, ScheduleMaxWeeks)
        if (pendingWeek != null) {
            if (week == pendingWeek) {
                pendingWeek = null
                lastSettledWeek = week
            }
            return null
        }
        if (week == lastSettledWeek) return null
        lastSettledWeek = week
        lastReportedWeek = week
        return week
    }
}
