-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Ktor only uses this to detect an attached IntelliJ debugger.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
