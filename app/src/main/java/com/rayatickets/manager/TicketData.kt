package com.rayatickets.manager

import org.json.JSONArray
import org.json.JSONObject

data class Ticket(
    val id: Long = 0,
    val ticketNumber: String = "",
    val bank: String = "",
    val siteName: String = "",
    val machineSerial: String = "",
    val status: String = "",
    val statusId: Int = 0,
    val type: String = "",
    val typeId: Int = 0,
    val assignee: String = "",
    val displayAssignee: String = "",
    val startTime: String = "",
    val updatedAt: String = "",
    val comment: String = "",
    val isClosed: Boolean = false
) {
    val isPending: Boolean
        get() = statusId == 1 || status.equals("pending", ignoreCase = true) || !isClosed

    val isResolved: Boolean
        get() = statusId == 4 || status.equals("resolved", ignoreCase = true) || 
                statusId == 20 || status.equals("solved by phone", ignoreCase = true) || isClosed
}

object TicketParser {
    val standardCallTypes = listOf("call", "Call Back", "Customer Request", "PM", "Installation")

    private val titleRegex = """^(Eng\.?|م\.?|المهندس)\s*""".toRegex(RegexOption.IGNORE_CASE)
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
                val ticketNum = obj.optString("ticket", obj.optString("ticket_number", id.toString()))

                val bankObj = obj.optJSONObject("bank")
                val bank = bankObj?.optString("name") ?: obj.optString("bank", "")

                val machineObj = obj.optJSONObject("machine")
                val siteName = machineObj?.optString("name") 
                    ?: obj.optString("site_name", obj.optString("site", ""))
                val machineSerial = machineObj?.optString("serial_number")
                    ?: machineObj?.optString("terminal_id")
                    ?: obj.optString("machine", obj.optString("terminal_id", ""))

                val statusObj = obj.optJSONObject("status")
                val statusName = statusObj?.optString("name") ?: obj.optString("status", "")
                val fallbackStatusId = statusObj?.optInt("id") ?: 0
                val statusId = if (obj.has("status_id")) obj.optInt("status_id") else fallbackStatusId

                val typeObj = obj.optJSONObject("type")
                val typeName = typeObj?.optString("name") ?: obj.optString("type", "")
                val fallbackTypeId = typeObj?.optInt("id") ?: 0
                val typeId = if (obj.has("type_id")) obj.optInt("type_id") else fallbackTypeId

                val userObj = obj.optJSONObject("user") ?: obj.optJSONObject("assignee")
                val rawAssignee = userObj?.optString("name") ?: obj.optString("assignee", "")
                val displayAssignee = cleanAssigneeName(rawAssignee)

                val startTime = obj.optString("start_time", obj.optString("created_at", ""))
                val updatedAt = obj.optString("updated_at", "")
                val comment = obj.optString("comment", obj.optString("description", ""))
                val isClosed = obj.optInt("is_closed", if (statusId == 4 || statusId == 20) 1 else 0) == 1

                list.add(
                    Ticket(
                        id = id,
                        ticketNumber = ticketNum,
                        bank = bank,
                        siteName = siteName,
                        machineSerial = machineSerial,
                        status = statusName,
                        statusId = statusId,
                        type = typeName,
                        typeId = typeId,
                        assignee = rawAssignee,
                        displayAssignee = displayAssignee,
                        startTime = startTime,
                        updatedAt = updatedAt,
                        comment = comment,
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