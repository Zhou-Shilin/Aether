# Project-specific ProGuard rules can be added here when needed.

# Flexmark's BitFieldSet reflects enum constant names at runtime.
# Keep its enum members stable in release builds.
-keepclassmembers enum com.vladsch.flexmark.** {
    *;
}
