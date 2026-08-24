# Add project specific ProGuard rules here.
# R8 已开启：以下规则保证 Gson 反射序列化 / Retrofit 泛型解析在混淆后仍正常工作。

# ---------------- Gson ----------------
# Gson 按字段名反射填充数据，混淆改名后反序列化会得到全 null 数据。
# 保留泛型签名与注解（Retrofit/Gson 解析 List<Song> 等泛型类型需要）。
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# 参与 Gson 序列化的数据模型：
# data.model.Song —— 历史/收藏/队列/歌单全部经 JSON 落盘或网络传输
-keep class com.aesthetic.music.data.model.Song { <init>(); <fields>; }
# data.api.** —— Retrofit + Gson 解析的网络 DTO（PlaylistItem/UserInfo/HomeCategoryDto 等）
-keep class com.aesthetic.music.data.api.** { <init>(); <fields>; }
# data.settings 中直接经 Gson 落盘的持久化模型
-keep class com.aesthetic.music.data.settings.PlayerSession { <init>(); <fields>; }
-keep class com.aesthetic.music.data.settings.MyPlaylist { <init>(); <fields>; }
# AudioCacheService 的私有缓存索引条目（_index.json 反序列化）
-keepclassmembers class com.aesthetic.music.player.AudioCacheService$* { <fields>; }

# ---------------- Retrofit ----------------
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ---------------- 崩溃堆栈可读性 ----------------
# 混淆后保留源码行号，CrashHandler 上报的堆栈可用 mapping.txt 还原
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
