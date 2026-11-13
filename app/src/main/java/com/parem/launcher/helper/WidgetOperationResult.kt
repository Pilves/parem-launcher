package com.parem.launcher.helper

/** Identifies the operation a result was accepted for while delivery was deferred. */
data class WidgetOperationResult(
    val source: Int,
    val operationStage: Int,
    val operationId: Int,
    val returnedId: Int?,
    val success: Boolean,
) {
    fun matches(stage: Int, id: Int): Boolean = id > 0 && operationStage == stage && operationId == id &&
        (returnedId == null || returnedId == id) &&
        when (source) {
            BIND -> stage == BIND || stage == RESTORE_BIND
            CONFIGURE -> stage == CONFIGURE
            else -> false
        }

    companion object {
        const val BIND = 1
        const val CONFIGURE = 2
        const val RESTORE_BIND = 3
    }
}
