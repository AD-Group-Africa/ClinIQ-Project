package com.cliniq.shared.ratelimit

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class CachedBodyRequestWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    val cachedBody: ByteArray = request.inputStream.readAllBytes()

    override fun getInputStream(): ServletInputStream {
        val bis = ByteArrayInputStream(cachedBody)
        return object : ServletInputStream() {
            override fun read(): Int = bis.read()
            override fun isFinished(): Boolean = bis.available() == 0
            override fun isReady(): Boolean = true
            override fun setReadListener(listener: ReadListener?) {}
        }
    }

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(ByteArrayInputStream(cachedBody), characterEncoding ?: "UTF-8"))
}
