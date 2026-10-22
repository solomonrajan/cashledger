package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewEditPersonActivityTest {

    @Test
    fun testPersonCompanionConstants() {
        assertEquals("NewEditItemActivity::Mode", NewEditPersonActivity.MODE)
        assertEquals("NewEditItemActivity::Id", NewEditPersonActivity.ID)
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, NewEditPersonActivity.Mode.NEW_ITEM)
        assertEquals(NewEditItemActivity.Mode.EDIT_ITEM, NewEditPersonActivity.Mode.EDIT_ITEM)
    }

    @Test
    fun testPersonContractColumnNames() {
        assertEquals("person_id", Contract.Person.ID)
        assertEquals("person_name", Contract.Person.NAME)
        assertEquals("person_icon", Contract.Person.ICON)
        assertEquals("person_note", Contract.Person.NOTE)
    }

    @Test
    fun testPersonModelCreation() {
        val icon = ColorIcon("#FF5722", "JD")
        val person = Person(101L, "John Doe", icon)
        assertEquals(101L, person.id)
        assertEquals("John Doe", person.name)
        assertEquals(icon, person.icon)
    }

    @Test
    fun testColorIconInitialGeneration() {
        assertEquals("A", IconLoader.getColorIconString("Alice"))
        assertEquals("AS", IconLoader.getColorIconString("Alice Smith"))
        assertEquals("BD", IconLoader.getColorIconString("Bob   Dylan"))
        assertEquals("?", IconLoader.getColorIconString(""))
        assertEquals("?", IconLoader.getColorIconString(null))
    }
}
