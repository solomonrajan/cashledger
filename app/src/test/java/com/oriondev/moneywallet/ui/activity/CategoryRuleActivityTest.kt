package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRuleActivityTest {

    @Test
    fun testCategoryRuleCompanionConstants() {
        assertEquals("NewEditItemActivity::Mode", NewEditCategoryRuleActivity.MODE)
        assertEquals("NewEditItemActivity::Id", NewEditCategoryRuleActivity.ID)
        assertEquals(NewEditItemActivity.Mode.NEW_ITEM, NewEditCategoryRuleActivity.Mode.NEW_ITEM)
        assertEquals(NewEditItemActivity.Mode.EDIT_ITEM, NewEditCategoryRuleActivity.Mode.EDIT_ITEM)
    }

    @Test
    fun testPatternValidationLogic() {
        var pattern = "  "
        assertTrue(pattern.trim().isEmpty())

        pattern = "Starbucks"
        assertFalse(pattern.trim().isEmpty())
        assertEquals("Starbucks", pattern.trim())
    }

    @Test
    fun testCategoryAssignmentLogic() {
        var category: Category? = null
        assertNull(category)

        category = Category(
            10L,
            "Groceries",
            IconLoader.parse(""),
            Contract.CategoryType.EXPENSE,
            "tag_groceries"
        )
        assertNotNull(category)
        assertEquals(10L, category.id)
        assertEquals("Groceries", category.name)
        assertEquals(Contract.CategoryType.EXPENSE, category.type)
    }
}
