package dev.aliarslan.musvedde.domain

enum class ErrorLevel { ERROR, WARNING, INFO }

data class ErrorEntry(
    val id: Long,
    val timestamp: Long,
    val level: ErrorLevel,
    val source: String,
    val message: String,
    val stackTrace: String?,
)
