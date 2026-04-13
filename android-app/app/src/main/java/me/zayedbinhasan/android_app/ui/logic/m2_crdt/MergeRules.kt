package me.zayedbinhasan.android_app.ui.logic.m2_crdt

internal fun mergeStrategyForField(fieldName: String): String {
    return when (fieldName) {
        "quantity" -> "ADDITIVE"
        "assigned_driver_id" -> "MANUAL_OWNERSHIP"
        else -> "LWW"
    }
}
