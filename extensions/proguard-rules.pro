-dontobfuscate
-dontoptimize
-keepattributes *
-keep class app.dualvot.** {
  *;
}
-keep class com.google.** {
  *;
}
# Proguard can strip away kotlin intrinsics methods that are used by extension Kotlin code. Unclear why.
-keep class kotlin.jvm.internal.Intrinsics {
    public static *;
}
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn javax.lang.model.element.Modifier

# Compatible platform classes are provided by the base bundle's extension DEX at runtime.
# The add-on references them via compileOnly, so R8 sees them as missing during minify.
-dontwarn app.morphe.extension.**
