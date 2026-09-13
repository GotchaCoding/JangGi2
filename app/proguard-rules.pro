# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# JNI 네이티브 메서드가 있는 클래스는 이름/시그니처가 하드코딩된 JNI 명명 규칙
# (Java_com_example_janggi2_..._nativeXxx)으로 네이티브 코드와 연결되므로, 축소·
# 난독화되면 UnsatisfiedLinkError로 크래시합니다 (FairyStockfishEngine 대상).
-keepclasseswithmembernames class * {
    native <methods>;
}

# OpenCV(com.quickbirdstudios:opencv) - 이 AAR은 공식 배포판과 달리 자체 consumer
# proguard 규칙이 없고, 네이티브 라이브러리가 org.opencv.* 클래스/메서드를 JNI로
# 직접 참조합니다. 축소·이름변경 시 이미지 인식 기능이 릴리즈에서만 크래시합니다.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**