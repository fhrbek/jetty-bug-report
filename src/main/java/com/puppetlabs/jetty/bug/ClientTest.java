package com.puppetlabs.jetty.bug;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.client.HttpAsyncClient;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.KeyStore;
import java.util.UUID;

/**
 * This is the main testing class. It tries to send an HTTP request multiple times, once with the
 * jetty client, and then with the apache client. When the machine is fast enough, many of the
 * jetty client requests fail while none of the apache client requests fails.
 */
public class ClientTest {

    private static final String HOST = "localhost";
    private static final int PORT = 8002;
    private static final String PATH = "/dummy.txt";
    private static final String URL = "https://" + HOST + ":" + PORT + PATH;
    private static final int ITERATIONS = 100;

    public static void main(String... args) throws Exception {

        //Read certificates and keys from resources
        Reader caCertReader = new InputStreamReader(ClientTest.class.getResourceAsStream("/ca_cert.pem"));
        Reader privateKeyReader = new InputStreamReader(ClientTest.class.getResourceAsStream("/private_key.pem"));
        Reader certReader = new InputStreamReader(ClientTest.class.getResourceAsStream("/cert.pem"));
        String keyPassword = UUID.randomUUID().toString();

        KeyStore trustStore = CertificateUtil.associateCertsFromReader(CertificateUtil.createKeyStore(), "CA Certificate", caCertReader);
        KeyStore keyStore = CertificateUtil.associatePrivateKeyFromReader(CertificateUtil.createKeyStore(), "Private Key",
                privateKeyReader, keyPassword, certReader);

        final HttpClient jettyClient = createJettyClient(keyStore, keyPassword, trustStore);
        final CloseableHttpAsyncClient apacheClient = createApacheClient(keyStore, keyPassword, trustStore);

        BatchManager bm = new BatchManager();

        //Execute a simple GET request multiple times with the jetty client. Expect some failures.
        System.out.println("\nTesting jetty client...");
        bm.runTask(new BatchManager.Task() {

            @Override
            public void run(BatchManager m, int iteration) {
                sendJettyRequest(m, jettyClient, "Jetty #" + iteration, URL);
            }
        }, ITERATIONS);

        jettyClient.stop();

        //Execute a simple GET request multiple times with the apache client. No failures are expected.
        System.out.println("\nTesting apache client...");
        bm.runTask(new BatchManager.Task() {

            @Override
            public void run(BatchManager m, int iteration) {
                sendApacheRequest(m, apacheClient, "Apache #" + iteration, URL);
            }
        }, ITERATIONS);

        apacheClient.close();

        System.out.println("Done.");
    }

    private static HttpClient createJettyClient(KeyStore keyStore, String keyPassword, KeyStore trustStore) throws Exception {
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStore(keyStore);
        sslContextFactory.setKeyStorePassword(keyPassword);
        sslContextFactory.setTrustStore(trustStore);
        HttpClient client = new HttpClient(sslContextFactory);
        client.start();

        return client;
    }

    private static void sendJettyRequest(final BatchManager m, HttpClient client, final String id, String url) {
        client.newRequest(url).send(new Response.Listener.Adapter() {
            public void onComplete(Result result) {
                try {
                    synchronized (m) {
                        System.out.println("Status of " + id + ": " + result.getResponse().getStatus());
                        if (result.isFailed()) {
                            System.out.print("  The failure of " + id + " was: ");
                            result.getFailure().printStackTrace();
                        }
                    }
                } finally {
                    m.taskDone();
                }
            }
        });
    }

    private static CloseableHttpAsyncClient createApacheClient(KeyStore keyStore, String keyPassword, KeyStore trustStore) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        CloseableHttpAsyncClient apacheClient = HttpAsyncClients.custom().setSSLContext(context).build();
        apacheClient.start();

        return apacheClient;
    }

    private static void sendApacheRequest(final BatchManager m, HttpAsyncClient client, final String id, String url) {
        client.execute(new HttpGet(url), new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse httpResponse) {
                try {
                    synchronized (m) {
                        System.out.println("Status of " + id + ": " + httpResponse.getStatusLine().getStatusCode());
                    }
                } finally {
                    m.taskDone();
                }
            }

            @Override
            public void failed(Exception e) {
                try {
                    synchronized (m) {
                        System.out.println("  The failure " + id + " was: ");
                        e.printStackTrace();
                    }
                } finally {
                    m.taskDone();
                }
            }

            @Override
            public void cancelled() {
                try {
                    synchronized (m) {
                        System.out.println("  The request " + id + " was cancelled!");
                    }
                } finally {
                    m.taskDone();
                }
            }
        });
    }
}
