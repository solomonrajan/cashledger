package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Event
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class NewEditEventActivityTest {

    @Test
    fun testEventCompanionConstants() {
        assertEquals("NewEditItemActivity::Mode", NewEditEventActivity.MODE)
        assertEquals("NewEditItemActivity::Id", NewEditEventActivity.ID)
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, NewEditEventActivity.Mode.NEW_ITEM)
        assertEquals(NewEditItemActivity.Mode.EDIT_ITEM, NewEditEventActivity.Mode.EDIT_ITEM)
    }

    @Test
    fun testEventContractColumnNames() {
        assertEquals("event_id", Contract.Event.ID)
        assertEquals("event_name", Contract.Event.NAME)
        assertEquals("event_icon", Contract.Event.ICON)
        assertEquals("event_start_date", Contract.Event.START_DATE)
        assertEquals("event_end_date", Contract.Event.END_DATE)
        assertEquals("event_note", Contract.Event.NOTE)
    }

    @Test
    fun testEventModelCreation() {
        val start = Date()
        val end = DateUtils.addMonths(Calendar.getInstance(), 1)
        val icon = ColorIcon("#4CAF50", "SE")
        val event = Event(202L, "Summer Expo", icon, start, end)
        assertEquals(202L, event.id)
        assertEquals("Summer Expo", event.name)
        assertEquals(icon, event.icon)
        assertEquals(start, event.startDate)
        assertEquals(end, event.endDate)
    }

    @Test
    fun testDateRangeValidationLogic() {
        val now = System.currentTimeMillis()
        val start = Date(now)
        val end = Date(now + 86400000L) // 1 day later
        assertTrue(start.time <= end.time)

        val invalidEnd = Date(now - 86400000L) // 1 day earlier
        assertFalse(start.time <= invalidEnd.time)
    }
}
