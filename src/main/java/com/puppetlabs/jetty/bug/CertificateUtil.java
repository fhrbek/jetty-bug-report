package com.puppetlabs.jetty.bug;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.Reader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * This class contains basic key store and certificate utilities.
 */
public class CertificateUtil {
    public static KeyStore createKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null);
        return ks;
    }

    public static List<Object> pemToObjects(Reader reader)
            throws IOException {
        PEMParser parser = new PEMParser(reader);
        List<Object> results = new ArrayList<Object>();
        for (Object o = parser.readObject(); o != null; o = parser.readObject())
            results.add(o);
        return results;
    }

    public static List<X509Certificate> pemToCerts(Reader reader)
            throws CertificateException, IOException {
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
        List<Object> pemObjects = pemToObjects(reader);
        List<X509Certificate> results = new ArrayList<X509Certificate>(pemObjects.size());
        for (Object o : pemObjects)
            results.add(converter.getCertificate((X509CertificateHolder) o));
        return results;
    }

    public static KeyStore associateCertsFromReader(KeyStore keystore, String prefix, Reader pem)
            throws CertificateException, KeyStoreException, IOException {
        List<X509Certificate> certs = pemToCerts(pem);
        ListIterator<X509Certificate> iter = certs.listIterator();
        for (int i = 0; iter.hasNext(); i++)
            associateCert(keystore, prefix + "-" + i, iter.next());
        return keystore;
    }

    public static KeyStore associateCert(KeyStore keystore, String alias, X509Certificate cert)
            throws KeyStoreException {
        keystore.setCertificateEntry(alias, cert);
        return keystore;
    }

    public static KeyStore associatePrivateKeyFromReader(KeyStore keystore, String alias, Reader pemPrivateKey,
                                                         String password, Reader pemCert)
            throws CertificateException, KeyStoreException, IOException {
        PrivateKey privateKey = pemToPrivateKey(pemPrivateKey);
        List<X509Certificate> certs = pemToCerts(pemCert);

        if (certs.size() > 1)
            throw new IllegalArgumentException("The PEM stream contains more than one certificate");

        X509Certificate firstCert = certs.get(0);
        return associatePrivateKey(keystore, alias, privateKey, password, firstCert);
    }

    public static PrivateKey pemToPrivateKey(Reader reader)
            throws IOException {
        List<PrivateKey> privateKeys = pemToPrivateKeys(reader);
        if (privateKeys.size() != 1)
            throw new IllegalArgumentException("The PEM stream must contain exactly one private key");
        return privateKeys.get(0);
    }

    public static List<PrivateKey> pemToPrivateKeys(Reader reader)
            throws IOException, PEMException {
        List<Object> objects = pemToObjects(reader);
        List<PrivateKey> results = new ArrayList<PrivateKey>(objects.size());
        for (Object o : objects)
            results.add(objectToPrivateKey(o));
        return results;
    }

    public static PrivateKey objectToPrivateKey(Object obj)
            throws PEMException {
        // Certain PEMs will hand back a keypair with a nil public key
        if (obj instanceof PrivateKeyInfo)
            return new JcaPEMKeyConverter().getPrivateKey((PrivateKeyInfo) obj);
        else if (obj instanceof PEMKeyPair)
            return new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) obj).getPrivate();
        else
            throw new IllegalArgumentException("Expected a KeyPair or PrivateKey, got " + obj);
    }

    public static KeyStore associatePrivateKey(KeyStore keystore, String alias, PrivateKey privateKey,
                                               String password, X509Certificate cert)
            throws KeyStoreException {
        keystore.setKeyEntry(alias, privateKey, password.toCharArray(), new Certificate[]{cert});
        return keystore;
    }
}
