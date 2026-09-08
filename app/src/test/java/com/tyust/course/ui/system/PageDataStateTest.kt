package com.tyust.course.ui.system

import org.junit.Assert.*
import org.junit.Test

class PageDataStateTest {
    @Test fun revisitingPageRetainsResultButAccountChangesDetachOldCallbacks() {
        val model = PageDataViewModel()
        val first = model.forAccount("a").state("grades") { listOf(1) }
        assertSame(first, model.forAccount("a").state("grades") { emptyList<Int>() })
        val second = model.forAccount("b").state("grades") { emptyList<Int>() }
        first.value = listOf(999)
        assertTrue(second.value.isEmpty())
        assertNotSame(first, model.forAccount("a").state("grades") { emptyList<Int>() })
    }
}
