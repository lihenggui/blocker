# Proguard rules for the `benchmark` build type.
#
# Obsfuscation must be disabled for the build variant that generates Baseline Profile, otherwise
# wrong symbols would be generated. The generated Baseline Profile will be properly applied when generated
# without obfuscation and your app is being obfuscated.
-dontobfuscate

# Will be fixed in okhttp5 https://github.com/square/okhttp/issues/6258
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE