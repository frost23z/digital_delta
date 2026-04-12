package me.zayedbinhasan.android_app.ui.logic.m1_auth

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import java.util.UUID

internal fun insertDemoUser(repository: LocalRepository) {
    val now = System.currentTimeMillis()
    val userId = "user-${UUID.randomUUID().toString().take(8)}"
    repository.upsertUser(
        userId = userId,
        displayName = "Field Volunteer",
        role = "FIELD_VOLUNTEER",
        publicKey = "pk_demo_$userId",
        active = true,
        createdAt = now,
        updatedAt = now,
    )
    appendMutation(repository, entityType = "user", entityId = userId, operationType = "UPSERT")
}

internal fun deleteUser(repository: LocalRepository, userId: String) {
    repository.deleteUserById(userId)
    appendMutation(repository, entityType = "user", entityId = userId, operationType = "DELETE")
}
