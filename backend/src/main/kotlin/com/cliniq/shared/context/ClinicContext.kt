package com.cliniq.shared.context

import java.util.UUID

object ClinicContext {
    private val id = ThreadLocal<UUID?>()
    fun set(v: UUID)       { id.set(v) }
    fun get(): UUID        = id.get() ?: error("No clinic context - send X-Clinic-ID header")
    fun getOrNull(): UUID? = id.get()
    fun clear()            { id.remove() }
}
