package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetOperationResultTest {
    @Test fun cancelWithoutDataMatchesOnlyItsAcceptedConfigureOperation() {
        val cancel = WidgetOperationResult(WidgetOperationResult.CONFIGURE, 2, 123, null, false)
        assertTrue(cancel.matches(2, 123))
        assertFalse(cancel.matches(2, 124))
        assertFalse(cancel.matches(1, 123))
        assertFalse(cancel.copy(returnedId = 124).matches(2, 123))
        assertFalse(cancel.copy(source = WidgetOperationResult.BIND).matches(2, 123))
        assertFalse(cancel.copy(operationId = 0).matches(2, 0))
    }

    @Test fun bindSourceAlsoAcceptsLegacyRestoreBind() {
        val restore = WidgetOperationResult(WidgetOperationResult.BIND, 3, 55, 55, true)
        assertTrue(restore.matches(3, 55))
        assertFalse(restore.copy(source = WidgetOperationResult.CONFIGURE).matches(3, 55))
    }
}
