package dev.aliarslan.musvedde.core

interface ErrorLogger {
    fun error(source: String, message: String, throwable: Throwable? = null)
    fun warning(source: String, message: String, throwable: Throwable? = null)
    fun info(source: String, message: String)
}
