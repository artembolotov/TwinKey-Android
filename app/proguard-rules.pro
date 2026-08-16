# Правила R8 для release-сборки.
# Базовый набор оптимизаций приходит из proguard-android-optimize.txt
# (подключается в app/build.gradle.kts).

# Читаемые стектрейсы в Play Console: номера строк сохраняем,
# оригинальные имена файлов прячем (mapping.txt заливается в Play).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- ML Kit / Firebase Components ---
# ML Kit берёт имена своих ComponentRegistrar из meta-data в AndroidManifest
# и создаёт их рефлексией: Class.forName(name).getDeclaredConstructor().newInstance().
# Consumer-правила библиотеки сохраняют сами классы — в mapping.txt их имена
# не переименованы, — но в full mode R8 (по умолчанию с AGP 8) `-keep class`
# больше не подразумевает конструктор по умолчанию. Из кода регистраторы никто
# не создаёт, поэтому R8 их конструкторы вырезает: в маппинге сборки 152 у
# BarcodeRegistrar, VisionCommonRegistrar и CommonComponentRegistrar нет
# собственного `<init>()`, хотя у сохранённых классов он там всегда есть.
#
# Дальше ComponentRuntime ловит InvalidRegistrarException, пишет в лог и молча
# пропускает регистратор — приложение стартует нормально, но BarcodeScannerFactory
# оказывается незарегистрированным. MlKitContext.get() возвращает null, и первый
# же BarcodeScanning.getClient() падает NPE — на экране сканера, при нажатии «+».
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

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
