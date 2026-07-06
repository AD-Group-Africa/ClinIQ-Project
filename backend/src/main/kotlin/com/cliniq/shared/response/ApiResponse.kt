package com.cliniq.shared.response

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?         = null,
    val error: ApiError? = null,
    val meta: PageMeta?  = null
) {
    companion object {
        fun <T> success(data: T, meta: PageMeta? = null) = ApiResponse(true, data, meta = meta)
        fun <T> error(code: String, message: String) = ApiResponse<T>(false, error = ApiError(code, message))
    }
}

data class ApiError(
    val code: String,
    val message: String?,
    val details: Any? = null,
    val timestamp: Instant = Instant.now()
)

data class PageMeta(val page: Int, val size: Int, val total: Long, val totalPages: Int)
