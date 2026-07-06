-dontobfuscate

-keep class eu.siacs.conversations.**
-keep class im.conversations.**

-keep class org.whispersystems.**

-keep class com.kyleduo.switchbutton.Configuration

-keep class com.soundcloud.android.crop.**

-keep class com.google.android.gms.**

-keep class org.openintents.openpgp.*
-keep class org.webrtc.** { *; }

-keep class net.fellbaum.jemoji.** { *; }
-keeppackagenames net.fellbaum.jemoji.**
-keepdirectories jemoji

-dontwarn javax.mail.internet.MimeMessage
-dontwarn javax.mail.internet.MimeBodyPart
-dontwarn javax.mail.internet.SharedInputStream
-dontwarn javax.activation.DataContentHandler
-dontwarn org.bouncycastle.mail.**
-dontwarn org.bouncycastle.x509.util.LDAPStoreHelper
-dontwarn org.bouncycastle.jce.provider.X509LDAPCertStoreSpi
-dontwarn org.bouncycastle.cert.dane.**
-dontwarn rocks.xmpp.addr.**
-dontwarn com.google.firebase.analytics.connector.AnalyticsConnector
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient
-dontwarn com.google.android.gms.ads.identifier.AdvertisingIdClient$Info
-dontwarn java.lang.**
-dontwarn com.google.auto.service.AutoService
-dontwarn javax.lang.**
-dontwarn org.webrtc.Dav1dDecoder


-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.jetbrains.annotations.**

-keepclassmembers class eu.siacs.conversations.http.services.** {
  !transient <fields>;
}

# Needed for proper GSON deserialization
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*


# With R8 full mode, it sees no subtypes of Retrofit interfaces since they are created with a Proxy
# and replaces all potential values with null. Explicitly keeping the interfaces prevents this.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Guava doesn’s ship a complete file - base.pro

# Throwables uses internal APIs for lazy stack trace resolution
-dontnote sun.misc.SharedSecrets
-keep class sun.misc.SharedSecrets {
  *** getJavaLangAccess(...);
}
-dontnote sun.misc.JavaLangAccess
-keep class sun.misc.JavaLangAccess {
  *** getStackTraceElement(...);
  *** getStackTraceDepth(...);
}

# FinalizableReferenceQueue calls this reflectively
# Proguard is intelligent enough to spot the use of reflection onto this, so we
# only need to keep the names, and allow it to be stripped out if
# FinalizableReferenceQueue is unused.
#
# (Under Android, we end up using Finalzier non-reflectively, so we could
# likely rework all this configuration.)
-keepclassmembernames class com.google.common.base.internal.Finalizer {
  *** startFinalizer(...);
}
# However, it cannot "spot" that this method needs to be kept IF the class is.
-keepclassmembers class com.google.common.base.internal.Finalizer {
  *** startFinalizer(...);
}

-keepclassmembernames class com.google.common.base.FinalizableReference {
  void finalizeReferent();
}
-keepclassmembers class com.google.common.base.FinalizableReference {
  void finalizeReferent();
}

# Guava - cache.pro

# Striped64 uses this
-dontwarn sun.misc.Unsafe

# Striped64 appears to make some assumptions about object layout that
# really might not be safe. This should be investigated.
-keepclassmembers class com.google.common.cache.Striped64 {
  *** base;
  *** busy;
}
-keepclassmembers class com.google.common.cache.Striped64$Cell {
  <fields>;
}


# random shit from https://github.com/google/guava/issues/8345

-keep class com.google.common.cache.** { *; }
-keepclassmembers class com.google.common.cache.LocalCache$Strength {
public static **[] values();
public static ** valueOf(java.lang.String);
}


# Guava collect pro

# The nested FieldSettersHolder class looks these up.
#
# We use -keepclassmembernames because we want for ImmutableMultimap and its
# fields to be stripped if it's unused: -keepclassmembernames says that, *if*
# you're keeping the fields, you need to leave their names untouched. (Anyone
# who is using ImmutableMultimap will certainly be using its fields. So we
# don't need to worry that an ImmutableMultimap user will have the fields
# optimized away.)
#
# This configuration is untested....

-keepclassmembernames class com.google.common.collect.ImmutableMultimap {
  *** map;
  *** size;
}
# similarly:
-keepclassmembernames class com.google.common.collect.ConcurrentHashMultiset {
  *** countMap;
}
# similarly:
-keepclassmembernames class com.google.common.collect.ImmutableSetMultimap {
  *** emptySet;
}
# similarly:
-keepclassmembernames class com.google.common.collect.AbstractSortedMultiset {
  *** comparator;
}
# similarly:
-keepclassmembernames class com.google.common.collect.TreeMultiset {
  *** range;
  *** rootReference;
  *** header;
}

# concurrent

# Futures.getChecked, in both of its variants, is incompatible with proguard.

# Used by AtomicReferenceFieldUpdater, sun.misc.Unsafe, and VarHandle.
# We could be more precise about which classes these are defined in, but that feels error-prone.
-keepclassmembers class com.google.common.util.concurrent.AbstractFuture** {
  *** waitersField;
  *** valueField;
  *** listenersField;
  *** thread;
  *** next;
}
-keepclassmembers class com.google.common.util.concurrent.AbstractFutureState** {
  *** waitersField;
  *** valueField;
  *** listenersField;
  *** thread;
  *** next;
}
-keepclassmembers class com.google.common.util.concurrent.AtomicDouble {
  *** value;
}
-keepclassmembers class com.google.common.util.concurrent.AggregateFutureState {
  *** remainingField;
  *** seenExceptionsField;
}

# Since Unsafe is using the field offsets of these inner classes, we don't want
# to have class merging or similar tricks applied to these classes and their
# fields. It's safe to allow obfuscation, since the by-name references are
# already preserved in the -keep statement above.
-keep,allowshrinking,allowobfuscation class com.google.common.util.concurrent.AbstractFuture** {
  <fields>;
}
-keep,allowshrinking,allowobfuscation class com.google.common.util.concurrent.AbstractFutureState** {
  <fields>;
}

# AbstractFuture uses this
-dontwarn sun.misc.Unsafe

# MoreExecutors references AppEngine
-dontnote com.google.appengine.api.ThreadManager
-keep class com.google.appengine.api.ThreadManager {
  static *** currentRequestThreadFactory(...);
}
-dontnote com.google.apphosting.api.ApiProxy
-keep class com.google.apphosting.api.ApiProxy {
  static *** getCurrentEnvironment (...);
}

# guava - hash.pro

# LittleEndianByteArray uses this
-dontwarn sun.misc.Unsafe

# Striped64 appears to make some assumptions about object layout that
# really might not be safe. This should be investigated.
-keepclassmembers class com.google.common.hash.Striped64 {
  *** base;
  *** busy;
}
-keepclassmembers class com.google.common.hash.Striped64$Cell {
  <fields>;
}

# UnsignedBytes uses this
-dontwarn sun.misc.Unsafe
