# R8 rules for the release build.
#
# This file is deliberately near-empty. The app has no reflection of its own, no
# serialization library and no JNI: the stores hand-roll their JSON rather than
# using Codable-style reflection, so there is nothing R8 can rename out from
# under us. Compose, AndroidX and the Kotlin stdlib all ship their own consumer
# rules, which is why `assembleRelease` shrinks 12 MB to about 1.3 MB with no
# help from here.
#
# If something works in debug and breaks in release, this is where the keep rule
# goes — and the mapping file to decode the stack trace is written to
# app/build/outputs/mapping/release/mapping.txt.

# Keep line numbers so a release crash report points at a real line, and hide
# the original file name (which the line table would otherwise leak).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
