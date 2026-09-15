# Keep Room's generated implementations (found via reflection).
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# WorkManager instantiates workers via reflection.
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }

# OkHttp platform warnings are harmless; silence them.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
