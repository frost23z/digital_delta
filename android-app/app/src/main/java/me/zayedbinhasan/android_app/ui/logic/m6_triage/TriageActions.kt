package me.zayedbinhasan.android_app.ui.logic.m6_triage

import me.zayedbinhasan.android_app.data.local.repository.LocalRepository
import me.zayedbinhasan.android_app.ui.logic.core.appendMutation
import org.json.JSONObject

internal data class TriageImpact(
    val preemptedLowPriorityCount: Int,
    val reroutedHighPriorityCount: Int,
)

internal fun applyTriagePreemption(
    repository: LocalRepository,
    routeId: String,
    previousDurationMins: Long,
    recomputedDurationMins: Long,
    blocked: Boolean,
    reasonCode: String,
): TriageImpact {
    val deliveries = repository.allDeliveriesNow()
    if (deliveries.isEmpty()) {
        return TriageImpact(preemptedLowPriorityCount = 0, reroutedHighPriorityCount = 0)
    }

    val etaIncreaseRatio = if (previousDurationMins <= 0L) {
        0.0
    } else {
        (recomputedDurationMins - previousDurationMins).toDouble() / previousDurationMins.toDouble()
    }

    var preemptedLowPriorityCount = 0
    var reroutedHighPriorityCount = 0
    val now = System.currentTimeMillis()

    deliveries.forEach { delivery ->
        val priorityBand = normalizePriorityBand(delivery.priority)
        val slaMins = slaMinutesForPriority(priorityBand)
        val exceedsSla = recomputedDurationMins > slaMins
        val etaBreached = etaIncreaseRatio >= 0.30
        val shouldPreempt = blocked || exceedsSla || etaBreached

        if (!shouldPreempt) {
            return@forEach
        }

        val oldStatus = delivery.status
        val newStatus = when (priorityBand) {
            "P0", "P1" -> "REROUTED_PRIORITY"
            else -> "PREEMPTED_DROP_OFF"
        }

        if (oldStatus == newStatus) {
            return@forEach
        }

        repository.updateDeliveryStatus(delivery.task_id, newStatus, now)

        val operationType = if (newStatus == "REROUTED_PRIORITY") {
            reroutedHighPriorityCount += 1
            "TRIAGE_REROUTE_P0_P1"
        } else {
            preemptedLowPriorityCount += 1
            "TRIAGE_PREEMPT_P2_P3"
        }

        appendMutation(
            repository = repository,
            entityType = "delivery",
            entityId = delivery.task_id,
            operationType = operationType,
            changedFieldsJson = JSONObject().apply {
                put("route_id", routeId)
                put("priority", priorityBand)
                put("sla_mins", slaMins)
                put("previous_duration_mins", previousDurationMins)
                put("recomputed_duration_mins", recomputedDurationMins)
                put("eta_increase_ratio", etaIncreaseRatio)
                put("blocked", blocked)
                put("reason_code", reasonCode)
                put("old_status", oldStatus)
                put("new_status", newStatus)
                put("rationale", if (newStatus == "REROUTED_PRIORITY") {
                    "SLA breach risk: keep P0/P1 cargo active"
                } else {
                    "SLA breach risk: drop P2/P3 cargo at safe waypoint"
                })
            }.toString(),
        )
    }

    return TriageImpact(
        preemptedLowPriorityCount = preemptedLowPriorityCount,
        reroutedHighPriorityCount = reroutedHighPriorityCount,
    )
}

internal fun normalizePriorityBand(priorityRaw: String): String {
    val normalized = priorityRaw.uppercase()
    return when {
        normalized.startsWith("P0") -> "P0"
        normalized.startsWith("P1") -> "P1"
        normalized.startsWith("P2") -> "P2"
        normalized.startsWith("P3") -> "P3"
        else -> "P2"
    }
}

internal fun slaMinutesForPriority(priorityBand: String): Long {
    return when (priorityBand) {
        "P0" -> 60L
        "P1" -> 180L
        "P2" -> 360L
        "P3" -> 720L
        else -> 360L
    }
}
