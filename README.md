# Simple-Name-System

## Italiano
Sviluppo di un semplice Sistema di Nomi.

### Componenti
- Il DiscoveryServer funge da Naming Server.
- Il RowSwapServer è il server che offre l'effettivo servizio richiesto dal client.
- Il Client interpella il DiscoveryServer, il quale risponde con il numero di porta su cui potrà richiedere il servizio al RowSwapServer.

### Istruzioni
- Compilazione dalla cartella src:
  - $ javac -cp . server/DiscoveryServer.java server/RowSwapServer.java client/Client.java

- Esecuzione dalla cartella src:
  - $ java -cp . server/DiscoveryServer requestPortClient registerPortRowSwapServer
  - $ java -cp . server/RowSwapServer IPAddressDiscoveryServer portDiscoveryServer localPortRowSwapServer fileName
  - $ java -cp . client/Client IPAddressDiscoveryServer portDiscoveryServer

---

## English
Development of a simple Name System.

### Components
- DiscoveryServer acts as the Naming Server.
- RowSwapServer is the server providing the actual service requested by the client.
- The Client contacts the DiscoveryServer, which responds with the port number to request the service from the RowSwapServer.

### Instructions
- Compile from the src folder:
  - $ javac -cp . server/DiscoveryServer.java server/RowSwapServer.java client/Client.java

- Run from the src folder:
  - $ java -cp . server/DiscoveryServer requestPortClient registerPortRowSwapServer
  - $ java -cp . server/RowSwapServer IPAddressDiscoveryServer portDiscoveryServer localPortRowSwapServer fileName
  - $ java -cp . client/Client IPAddressDiscoveryServer portDiscoveryServer
