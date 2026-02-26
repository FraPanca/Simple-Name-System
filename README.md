Progetto di un semplice Sistema di Nomi.

==== COMPONENTI ====
 - il DiscoveryServer funge da Naming Server.
 - il RowSwapServer è il server che offre l'effetivo servizio richiesto dal client.
 - il Client interpella perciò il DiscoveryServer, il quale risponde con il numero di porta su cui potrà richiedere il servizio al RowSwapServer.


==== ISTRUZIONI ====
Compilazione dalla cartella src:
	javac -cp . server/DiscoveryServer.java server/RowSwapServer.java client/Client.java

Esecuzione dalla cartella src:
	java -cp . server/DiscoveryServer requestPortClient registerPortRowSwapServer
	java -cp . server/RowSwapServer IPAddressDiscoveryServer portDiscoveryServer localPortRowSwapServer fileName
	java -cp . client/Client IPAddressDiscoveryServer portDiscoveryServer
