# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
# BouncyCastle: keep only what the sealed box uses (reflection-free), silence the rest
-keep class org.bouncycastle.crypto.engines.XSalsa20Engine { *; }
-keep class org.bouncycastle.crypto.macs.Poly1305 { *; }
-keep class org.bouncycastle.math.ec.rfc7748.X25519 { *; }
-keep class org.bouncycastle.crypto.digests.Blake2bDigest { *; }
-dontwarn org.bouncycastle.**
# Tink (security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
