package com.rayatickets.manager

import org.json.JSONArray
import org.json.JSONObject

data class Ticket(
    val id: Long = 0,
    val serial: String = "",
    val bank: String = "",
    val siteName: String = "",
    val atmId: String = "",
    val atmModel: String = "",
    val status: String = "",
    val subStatus: String = "",
    val statusId: Int = 0,
    val type: String = "",
    val subType: String = "",
    val typeId: Int = 0,
    val assignee: String = "",
    val displayAssignee: String = "",
    val leader: String = "",
    val displayLeader: String = "",
    val startTime: String = "",
    val createdAt: String = "",
    val description: String = "",
    val isClosed: Boolean = false
) {
    val isPending: Boolean
        get() = statusId == 1 || status.equals("pending", ignoreCase = true) || !isClosed

    val isResolved: Boolean
        get() = statusId == 4 || status.equals("resolved", ignoreCase = true) ||
                statusId == 20 || status.equals("solved by phone", ignoreCase = true) || isClosed

    val fullStatusText: String
        get() = if (subStatus.isNotBlank()) "$status · $subStatus" else status
}

object TicketParser {
    val standardCallTypes = listOf("Call", "PM", "Customer Request", "Installation")

    private val titleRegex = """^(Eng\.?|\u0645\.?|\u0627\u0644\u0645\u0647\u0646\u062F\u0633)\s*""".toRegex(RegexOption.IGNORE_CASE)
    private val spacesRegex = """\s+""".toRegex()

    fun cleanAssigneeName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return "Unassigned"
        val withoutTitle = rawName.replace(titleRegex, "").trim()
        val tokens = withoutTitle.split(spacesRegex).filter { it.isNotBlank() }
        return when {
            tokens.isEmpty() -> "Unassigned"
            tokens.size == 1 -> tokens[0].trim()
            else -> "${tokens[0].trim()} ${tokens[1].trim()}"
        }
    }

    fun parseJson(jsonString: String): List<Ticket> {
        val list = mutableListOf<Ticket>()
        try {
            val root = JSONObject(jsonString)
            val dataArray = when {
                root.has("data") -> root.optJSONArray("data")
                root.has("tickets") -> root.optJSONArray("tickets")
                else -> null
            } ?: JSONArray()

            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                val id = obj.optLong("id", 0L)
                val serial = obj.optString("serial", obj.optString("ticket", id.toString()))

                val machineObj = obj.optJSONObject("machine")
                val bankObj = machineObj?.optJSONObject("bank") ?: obj.optJSONObject("bank")
                val bank: String = bankObj?.optString("abberv", bankObj.optString("name", obj.optString("bank", "")))
                    ?: obj.optString("bank", "")

                val siteName: String = machineObj?.optString("site_name", obj.optString("site_name", ""))
                    ?: obj.optString("site_name", "")
                val atmId: String = machineObj?.optString("atm_id", obj.optString("atm_id", ""))
                    ?: obj.optString("atm_id", "")
                val atmModel: String = machineObj?.optString("atm_model", "") ?: ""

                val statusObj = obj.optJSONObject("status")
                val statusName: String = statusObj?.optString("name", obj.optString("status", ""))
                    ?: obj.optString("status", "")
                val subStatusObj = obj.optJSONObject("subStatus")
                val subStatusName: String = subStatusObj?.optString("name", obj.optString("sub_status", ""))
                    ?: obj.optString("sub_status", "")

                val fallbackStatusId = statusObj?.optInt("id") ?: 0
                val statusId = if (obj.has("status_id")) obj.optInt("status_id") else fallbackStatusId

                val typeObj = obj.optJSONObject("type")
                val typeName: String = typeObj?.optString("name", obj.optString("type", ""))
                    ?: obj.optString("type", "")
                val subTypeObj = obj.optJSONObject("subType")
                val subTypeName: String = subTypeObj?.optString("name", "") ?: ""

                val fallbackTypeId = typeObj?.optInt("id") ?: 0
                val typeId = if (obj.has("type_id")) obj.optInt("type_id") else fallbackTypeId

                val userObj = obj.optJSONObject("assignee") ?: obj.optJSONObject("user")
                val rawAssignee: String = userObj?.optString("full_name", userObj.optString("name", obj.optString("assignee", "")))
                    ?: obj.optString("assignee", "")
                val displayAssignee = cleanAssigneeName(rawAssignee)

                val leaderObj = obj.optJSONObject("leader") ?: userObj?.optJSONObject("leader")
                val rawLeader: String = leaderObj?.optString("full_name", leaderObj.optString("name", obj.optString("leader", "")))
                    ?: obj.optString("leader", "")
                val displayLeader = cleanAssigneeName(rawLeader)

                val startTime = obj.optString("start_time", "")
                val createdAt = obj.optString("created_at", "")
                val description = obj.optString("description", "")
                val isClosed = obj.optInt("is_closed", if (statusId == 4 || statusId == 20) 1 else 0) == 1

                list.add(
                    Ticket(
                        id = id,
                        serial = serial,
                        bank = bank,
                        siteName = siteName,
                        atmId = atmId,
                        atmModel = atmModel,
                        status = statusName,
                        subStatus = subStatusName,
                        statusId = statusId,
                        type = typeName,
                        subType = subTypeName,
                        typeId = typeId,
                        assignee = rawAssignee,
                        displayAssignee = displayAssignee,
                        leader = rawLeader,
                        displayLeader = displayLeader,
                        startTime = startTime,
                        createdAt = createdAt,
                        description = description,
                        isClosed = isClosed
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
