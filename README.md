Introduction
------------

This is a testing project that demonstrates how to reproduce a jetty http client error.

Problem description
-------------------

When the jetty http client is used to connect many times at once to a server using an SSL connection,
some of the requests fail with one of two possible errors (*java.io.EOFException* or *java.lang.IllegalStateException*).
Which requests succeed and which ones fail seems to be random and probably also depends on the speed and load of
the machine it is run on. It looks like a faster machine generates more errors than a slower machine.

With SSL turned off everything works fine.

Using the apache http client instead of jetty http client works fine even if SSL is turned on.

The failures described above make use of the ProxyServlet (which uses jetty http client inside) unusable since
a browser application does exactly what we are testing - it sends multiple requests in parallel.

In this test, 100 requests is run in parallel for each tested client (jetty and apache). It appears to be enough
to reproduce at least one error per test run.

Steps to reproduce
------------------

1. **Server file structure**

    Unpack `misc/jetty-bug-report-server-files.tar` to the system root (use -P for preserving absolute paths):
    
    `tar -xPf misc/jetty-bug-report-server-files.tar`
    
    It will create following file structure:

        /opt
          /jetty-bug-report
            /certs
              ca_certs.pem
              cert.pem
              private_key.pem
            /docroot
              dummy.txt

2. **Virtual host**

    Create an apache virtual host using the configuration in `misc/jetty-bug-report.conf`.

3. **Build the testing project**

    Run `mvn package` to build this testing project.

4. **Run the test**

    Run `mvn exec:java` to generate a report (prints to the screen).


The problem seems to be time/speed related, so if you can't see any errors, try to run this test several times
or on a different (perhaps faster) machine.

An example of the erroneous output is also available in `misc/report-example.txt`.
