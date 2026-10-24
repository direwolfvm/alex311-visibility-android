package com.herbertindustries.alex311.domain

import com.herbertindustries.alex311.data.Request

object StatusChanges {
    fun snapshot(rows: List<Request>): Map<String, String> = rows.associate { it.service_request_id to it.status.orEmpty() }
    fun changed(previous: Map<String, String>, rows: List<Request>): List<Request> = rows.filter {
        previous.containsKey(it.service_request_id) && previous[it.service_request_id] != it.status.orEmpty()
    }
}
