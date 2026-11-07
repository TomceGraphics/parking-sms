# Parking SMS ProGuard / R8 rules (used by release builds only; debug has minify disabled).
# Room, DataStore, Glance and Compose ship their own consumer rules.
-keep class app.parkingsms.data.** { *; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
