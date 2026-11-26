package ro.leo.visual;
import android.security.keystore.*;
import java.math.BigInteger;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
/** Per-install identity. The private key never leaves AndroidKeyStore. */
final class CompanionTls {
    static final String ALIAS="keyshell-webui-v1";
    static synchronized KeyStore keys()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE,KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=KeyShell WebUI"))
                .setCertificateSerialNumber(new BigInteger(128,new SecureRandom()).add(BigInteger.ONE))
                .setCertificateNotBefore(new Date(0)).setCertificateNotAfter(new Date(4102444800000L)).build());generator.generateKeyPair();
        }return store;
    }
    static SSLServerSocket socket()throws Exception{
        KeyManagerFactory managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());managers.init(keys(),null);
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(managers.getKeyManagers(),null,new SecureRandom());
        SSLServerSocket socket=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket();socket.setEnabledProtocols(new String[]{"TLSv1.2"});
        socket.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256","TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"});return socket;
    }
    static String fingerprint()throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(keys().getCertificate(ALIAS).getEncoded());StringBuilder out=new StringBuilder();
        for(byte b:hash){if(out.length()>0)out.append(':');out.append(String.format(Locale.ROOT,"%02X",b&255));}return out.toString();
    }
}
