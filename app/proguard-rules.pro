# Правила R8 для release-сборки.
# Базовый набор оптимизаций приходит из proguard-android-optimize.txt
# (подключается в app/build.gradle.kts).

# Читаемые стектрейсы в Play Console: номера строк сохраняем,
# оригинальные имена файлов прячем (mapping.txt заливается в Play).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ---
# Ядро библиотеки поставляет свои consumer-правила; здесь только
# сгенерированные компилятором сериализаторы @Serializable-классов приложения.
-keepclasseswithmembers class com.artembolotov.twinkey.**$$serializer {
    *** INSTANCE;
    kotlinx.serialization.KSerializer[] childSerializers();
}
-keepclassmembers @kotlinx.serialization.Serializable class com.artembolotov.twinkey.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
