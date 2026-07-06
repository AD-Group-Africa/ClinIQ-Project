package com.cliniq.shared.exceptions

import com.cliniq.shared.response.ApiError
import com.cliniq.shared.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = err(HttpStatus.NOT_FOUND, "NOT_FOUND", e.message)

    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException) = err(HttpStatus.CONFLICT, "CONFLICT", e.message)

    @ExceptionHandler(ForbiddenException::class, AccessDeniedException::class)
    fun forbidden(e: Exception) = err(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied")

    @ExceptionHandler(BadRequestException::class)
    fun badRequest(e: BadRequestException) = err(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.message)

    @ExceptionHandler(UnauthorizedException::class, BadCredentialsException::class)
    fun unauthorized(e: Exception) = err(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.message ?: "Unauthorized")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val fields = e.bindingResult.allErrors.associate {
            (if (it is FieldError) it.field else it.objectName) to (it.defaultMessage ?: "Invalid")
        }
        return ResponseEntity.badRequest().body(ApiResponse(false, error = ApiError("VALIDATION_ERROR", "Validation failed", fields)))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParam(e: MissingServletRequestParameterException) =
        err(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Missing parameter: ${e.parameterName}")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(e: MethodArgumentTypeMismatchException) =
        err(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid value for: ${e.name}")

    @ExceptionHandler(Exception::class)
    fun generic(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled: {}", e.message, e)
        return err(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred")
    }

    private fun err(s: HttpStatus, code: String, msg: String?) =
        ResponseEntity.status(s).body(ApiResponse<Nothing>(false, error = ApiError(code, msg)))
}
