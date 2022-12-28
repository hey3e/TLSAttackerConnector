# TLS-Attacker Connector

This tool provides a connection between TLS-Attacker and StateLearner.

## Build

```
mvn package
```

## Example

Start OpenSSL

```
openssl s_server -key server.key -cert server.crt -CAfile cacert.pem -accept 4433 -HTTP
```

Start TLS-Attacker Connector

```
java -jar ./target/TLSAttackerConnector2.0.jar --timeout 500 -l 6666 -tP 4433
```

Run StateLearner

In socket directory of statelearner:
```
java -jar ../../target/stateLearner-0.0.1-SNAPSHOT.jar socket.properties
```
