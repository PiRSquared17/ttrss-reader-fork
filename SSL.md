# Introduction #

If you want to use the reader with an encrypted connection and a self-signed certificate (CACert or anything the android-cert-store does not trust) you can now add your own keystore to the sdcard. You need the following tools:

  * [OpenSSL's command line client](http://www.openssl.org/)
  * [Java SE 6 (for keytool)](http://java.sun.com/javase/downloads/index.jsp)
  * [Bouncy Castle's provider jar](http://www.bouncycastle.org/latest_releases.html)

# Steps to create your own keystore #

  1. Fetch the public key certificate from your server
```
echo | openssl s_client -connect <yourserver>:443 2>&1 |  sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > cert.pem
```
(change "yourserver" to the location of you ttrss-server)

  1. Create a keystore in the _Bouncy Castle keystore format_
```
export CLASSPATH=bcprov-jdk16-145.jar
keytool \
      -import \
      -v \
      -trustcacerts \
      -alias 0 \
      -file cert.pem \
      -keystore store.bks \
      -storetype BKS \
      -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
      -providerpath /usr/share/java/bcprov.jar \
      -storepass <yourpassword>
```
(change "yourpassword" to the password you want to set for your keystore)

  1. Put the file to the following location
/sdcard/Android/data/org.ttrssreader/files/store.bks

  1. Start ttrss-reader and change the SSL-Preferences to suit your needs


Further information can be found at [crazybob.org - Android: Trusting SSL certificates](http://crazybob.org/2010/02/android-trusting-ssl-certificates.html)

# Troubleshooting #

  1. See Troubleshooting for general information about troubleshooting problems with the app.
  1. Check if the app has the appropriate access-rights for the keystore and parent folders. On Ext2/3 partitions it can in some cases happen that folders created from the PC aren't readable by apps on the phone.