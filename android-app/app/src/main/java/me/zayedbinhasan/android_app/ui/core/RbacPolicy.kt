package me.zayedbinhasan.android_app.ui.core

internal enum class RbacCapability {
    ROUTE_MANAGE,
    DELIVERY_CREATE,
    DELIVERY_DELETE,
    CONFLICT_RESOLVE,
    SERVER_SYNC,
    POD_COUNTERSIGN,
}

internal data class RbacCapabilitySpec(
    val capability: RbacCapability,
    val title: String,
    val description: String,
    val allowedRoles: Set<String>,
)

private val managerRoles = setOf("SUPPLY_MANAGER", "CAMP_COMMANDER", "SYNC_ADMIN")
private val fieldAndManagerRoles = setOf("FIELD_VOLUNTEER") + managerRoles

private val rbacSpecs = listOf(
    RbacCapabilitySpec(
        capability = RbacCapability.ROUTE_MANAGE,
        title = "Route Planning and Recompute",
        description = "Create, delete, and recompute route plans",
        allowedRoles = managerRoles,
    ),
    RbacCapabilitySpec(
        capability = RbacCapability.DELIVERY_CREATE,
        title = "Delivery Queue Create",
        description = "Insert new offline delivery tasks",
        allowedRoles = fieldAndManagerRoles,
    ),
    RbacCapabilitySpec(
        capability = RbacCapability.DELIVERY_DELETE,
        title = "Delivery Queue Delete",
        description = "Delete delivery tasks from local queue",
        allowedRoles = managerRoles,
    ),
    RbacCapabilitySpec(
        capability = RbacCapability.CONFLICT_RESOLVE,
        title = "Conflict Resolution",
        description = "Accept local/remote/manual merge decisions",
        allowedRoles = managerRoles,
    ),
    RbacCapabilitySpec(
        capability = RbacCapability.SERVER_SYNC,
        title = "Server Sync Trigger",
        description = "Run manual sync with central server path",
        allowedRoles = managerRoles,
    ),
    RbacCapabilitySpec(
        capability = RbacCapability.POD_COUNTERSIGN,
        title = "PoD Countersign",
        description = "Approve pending PoD receipts",
        allowedRoles = fieldAndManagerRoles,
    ),
)

internal fun allRbacCapabilitySpecs(): List<RbacCapabilitySpec> = rbacSpecs

internal fun isRoleAllowed(role: String, capability: RbacCapability): Boolean {
    val normalizedRole = role.trim().uppercase()
    val spec = rbacSpecs.firstOrNull { it.capability == capability } ?: return false
    return normalizedRole in spec.allowedRoles
}

internal fun allowedRolesLabel(capability: RbacCapability): String {
    val spec = rbacSpecs.firstOrNull { it.capability == capability } ?: return "N/A"
    return spec.allowedRoles.joinToString(separator = ", ")
}
