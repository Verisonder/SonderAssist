# Shrink but do not rename. A crash on a test build is read out of the app itself, and an
# obfuscated stack trace turns that back into guesswork. Costs a little APK size and gives
# up nothing that matters: the source is public anyway.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
