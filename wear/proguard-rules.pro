# Wear OS companion app proguard rules

# Keep Media3 session classes
-keep class androidx.media3.session.** { *; }

# Keep Wear Tiles
-keep class androidx.wear.tiles.** { *; }

# Keep Complications
-keep class androidx.wear.watchface.complications.** { *; }
