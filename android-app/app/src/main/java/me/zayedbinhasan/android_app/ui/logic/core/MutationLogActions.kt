package me.zayedbinhasan.android_app.ui.logic.core

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import org.json.JSONObject
import java.util.UUID

internal fun appendMutation(
    repository: LocalRepository,
    entityType: String,
    entityId: String,
    operationType: String,
    changedFieldsJson: String = "{}",
    actorId: String = "ui_user",
) {
    val deviceId = "android_client"
    val nextCounter = repository.localDeviceMutationCount(deviceId) + 1
    val vectorClockJson = "{\"$deviceId\":$nextCounter}"

    repository.insertMutation(
        mutationId = "mut-${UUID.randomUUID().toString().take(12)}",
        entityType = entityType,
        entityId = entityId,
        operationType = operationType,
        changedFieldsJson = changedFieldsJson,
        vectorClockJson = vectorClockJson,
        actorId = actorId,
        deviceId = deviceId,
        mutationTimestamp = System.currentTimeMillis(),
        synced = false,
    )
}

internal fun changedFieldsJson(fields: Map<String, String>): String {
    if (fields.isEmpty()) {
        return "{}"
    }
    return JSONObject(fields).toString()
}
