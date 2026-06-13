# PeopleHub R8 / ProGuard rules.
# Most libraries (Room, Hilt, Coil, WorkManager, Glance) ship their own consumer rules; the rules
# below cover kotlinx.serialization and a few reflection-sensitive entry points.

# Keep line numbers for readable crash reports, hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# ---------------------------------------------------------------------------
# kotlinx.serialization
# Generated serializers are looked up reflectively for @Serializable classes (data-io DTOs and the
# type-safe navigation routes), so keep them and their Companion / serializer() accessors.
# ---------------------------------------------------------------------------
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.peoplehub.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.peoplehub.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.peoplehub.**$$serializer { *; }

# ---------------------------------------------------------------------------
# Room
# Entities and the generated database implementation are kept by Room's own rules; this is a guard
# for the DAO/entity reflection used during migrations.
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class * { @androidx.room.* <methods>; }

# ---------------------------------------------------------------------------
# Hilt / Dagger
# Hilt ships consumer rules; keep generated components defensively.
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }

# ---------------------------------------------------------------------------
# WorkManager — CoroutineWorker subclasses are constructed via the Hilt worker factory.
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker { *; }

# ---------------------------------------------------------------------------
# Domain & data models — kept whole so serialization and Room mapping stay stable.
# ---------------------------------------------------------------------------
-keep class com.peoplehub.core.domain.model.** { *; }
-keep class com.peoplehub.core.dataio.dto.** { *; }

# Kotlin metadata / coroutines (standard safe keeps)
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
