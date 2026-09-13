# Keep Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# WorkManager instantiates workers reflectively.
-keep class * extends androidx.work.ListenableWorker { *; }
