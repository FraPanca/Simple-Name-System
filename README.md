# Simple-Name-System

## Italiano

Sistema di naming e service discovery su UDP: un client individua dinamicamente il server che gestisce un determinato file e vi si connette per eseguire un'operazione di scambio righe.

**Stack:** Java · Socket UDP (`DatagramSocket`) · Programmazione multithread

### Descrizione

Progetto accademico che replica, in scala ridotta, il funzionamento di un servizio di naming distribuito (in stile DNS/service discovery): un `DiscoveryServer` centrale tiene traccia di quali `RowSwapServer` sono attivi e quali file gestiscono, mentre i `Client` interrogano il `DiscoveryServer` per ottenere l'indirizzo del server responsabile del file richiesto, per poi comunicare direttamente con esso.

Il protocollo è interamente basato su UDP, incluse la gestione manuale della frammentazione dei pacchetti e la sincronizzazione tramite un semplice schema di registrazione/de-registrazione.

### Come si esegue

Compilazione dalla cartella principale del repository:
```
javac -cp . server/DiscoveryServer.java server/RowSwapServer.java client/Client.java
```

Esecuzione (in tre terminali separati, nell'ordine indicato):
```
java -cp . server.DiscoveryServer <porta_richieste_client> <porta_registrazione_row_swap_server>
java -cp . server.RowSwapServer <IP_discovery_server> <porta_discovery_server> <porta_locale_row_swap_server> <nome_file>
java -cp . client.Client <IP_discovery_server> <porta_discovery_server>
```

I file su cui operare vanno posizionati in `server/resources/` (nel repository sono già presenti due file di esempio, `f1.txt` e `f2.txt`).

### Funzionalità principali

- Servizio di naming/discovery basato su UDP: il client non conosce a priori l'indirizzo del server che possiede un file, ma lo scopre tramite il `DiscoveryServer`
- Registrazione e de-registrazione dinamica dei `RowSwapServer` presso il `DiscoveryServer`, con un protocollo a comandi (apertura/chiusura del servizio)
- Gestione concorrente delle richieste tramite thread dedicati (uno per la comunicazione con i `RowSwapServer`, uno per i `Client`) e un `ReentrantLock` per proteggere la tabella condivisa dei server registrati
- Frammentazione manuale dei pacchetti UDP per l'invio della lista file quando supera l'MTU disponibile (payload limitato a 1400 byte per evitare frammentazione a livello IP), con riassemblaggio ordinato lato client tramite numerazione dei frammenti
- Operazione applicativa di scambio di due righe in un file di testo, eseguita in modo sicuro tramite file temporaneo e sostituzione atomica (`Files.move` con `REPLACE_EXISTING`)
- Terminazione controllata del `RowSwapServer` da riga di comando (comando `Termina`), con conseguente de-registrazione dal `DiscoveryServer`

### Struttura del progetto

```
Simple-Name-System/
├── client/
│   └── Client.java              # Interroga il Discovery Server e il Row Swap Server
├── server/
│   ├── DiscoveryServer.java     # Naming server: tabella dei Row Swap Server registrati
│   ├── RowSwapServer.java       # Server che espone l'operazione di scambio righe
│   └── resources/                # File di testo su cui operare (es. f1.txt, f2.txt)
```

### Note

Progetto didattico: il protocollo di trasporto usato (UDP) non garantisce ordine o consegna dei pacchetti, e la gestione degli errori di rete è volutamente semplificata. Non pensato per un uso in produzione (nessuna autenticazione o cifratura del traffico).

### Licenza

MIT

---

## English

A UDP-based naming and service discovery system: a client dynamically locates the server responsible for a given file and connects to it to perform a line-swap operation.

**Stack:** Java · UDP sockets (`DatagramSocket`) · Multithreaded programming

### Description

Academic project that replicates, on a small scale, how a distributed naming service works (DNS-like/service discovery): a central `DiscoveryServer` keeps track of which `RowSwapServer` instances are online and which file each one manages, while `Client`s query the `DiscoveryServer` to obtain the address of the server responsible for the requested file, then communicate with it directly.

The whole protocol runs over UDP, including manual packet-fragmentation handling and a simple registration/de-registration scheme.

### How to run

Compile from the repository root:
```
javac -cp . server/DiscoveryServer.java server/RowSwapServer.java client/Client.java
```

Run (in three separate terminals, in this order):
```
java -cp . server.DiscoveryServer <clientRequestPort> <rowSwapRegistrationPort>
java -cp . server.RowSwapServer <discoveryServerIP> <discoveryServerPort> <rowSwapLocalPort> <fileName>
java -cp . client.Client <discoveryServerIP> <discoveryServerPort>
```

Files to operate on must be placed in `server/resources/` (two sample files, `f1.txt` and `f2.txt`, are already included).

### Key features

- UDP-based naming/discovery service: the client doesn't know in advance which server owns a given file — it discovers it through the `DiscoveryServer`
- Dynamic registration and de-registration of `RowSwapServer` instances with the `DiscoveryServer`, using a simple command-based protocol (service opening/closing)
- Concurrent request handling via dedicated threads (one for `RowSwapServer` communication, one for `Client`s) and a `ReentrantLock` protecting the shared table of registered servers
- Manual UDP packet fragmentation for sending the file list when it exceeds the available MTU (payload capped at 1400 bytes to avoid IP-level fragmentation), with ordered client-side reassembly via fragment numbering
- Application-level operation that swaps two lines in a text file, performed safely through a temporary file and an atomic replace (`Files.move` with `REPLACE_EXISTING`)
- Controlled shutdown of the `RowSwapServer` via a command-line command (`Termina`), which triggers de-registration from the `DiscoveryServer`

### Project structure

```
Simple-Name-System/
├── client/
│   └── Client.java              # Queries the Discovery Server and the Row Swap Server
├── server/
│   ├── DiscoveryServer.java     # Naming server: table of registered Row Swap Servers
│   ├── RowSwapServer.java       # Server exposing the line-swap operation
│   └── resources/                # Text files to operate on (e.g. f1.txt, f2.txt)
```

### Notes

Educational project: the transport protocol used (UDP) does not guarantee packet ordering or delivery, and network error handling is intentionally simplified. Not intended for production use (no authentication or traffic encryption).

### License

MIT
