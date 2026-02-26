package client;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import server.DiscoveryServer;

public class Client {
	
	private final static int MAX_FILE_LENGTH = 256;
	
	private final static int REQ_VISUALIZATION_FILE = 1;
	
	private final static byte RESPONSE_OK = 0;
	private final static byte RESPONSE_ERR = 1;
	private final static byte RESPONSE_SERVER_OFFLINE = 5;
	

	public static void main(String[] args) {
		// args: IP Discovery Server, porta Discovery Server
		
		// controllo argomenti
		if(args.length != 2) {
			System.err.println("[CLIENT] : Errore -> Numero di argomenti errati.");
			System.exit(1);
		}
			
		
		InetAddress dsAddr = null;
		try {
			dsAddr = InetAddress.getByName(args[0]);
		} catch (UnknownHostException e) {
			System.err.println("[CLIENT] : Errore -> Indirizzo IP del Discovery Server sconosciuto: " + e);
			System.exit(1);
		}		

		int dsPort = -1;
		try {
			dsPort = Integer.parseInt(args[1]);
			if(dsPort > 65535 || dsPort < 0)
				throw new NumberFormatException();
		} catch(NumberFormatException e) {
			System.err.println("[CLIENT] : Errore -> Porta del Discovery Server non valida: " + e);
			System.exit(1);
		}
		
		
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
			
			// timeout 30s
			//socket.setSoTimeout(30000);
		} catch (SocketException e) {
			System.err.println("[CLIENT] : Errore -> Creazione della socket non riuscita: " + e);
			System.exit(2);
		}
		
		// comunicazione con il Discovery Server
		
		// invia la richiesta di visualizzazione del file al Discovery Server
		try {
			socket.send(new DatagramPacket(new byte[]{REQ_VISUALIZATION_FILE}, 1, dsAddr, dsPort));
		} catch (IOException e) {
			System.err.println("[CLIENT_DS] : Errore -> Non è stato possibile comunicare con il discovery server: " + e);
			System.exit(3);
		}
		
		
		int rsPort = -1;
		InetAddress rsAddr = null;
		
		ByteArrayInputStream bin = null;
		DataInputStream din = null;
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		DataOutputStream dout = new DataOutputStream(bout);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		
		
		// Non conoscendo la lunghezza totale in byte dell'intera lista file e dato che UDP può essere frammentato, limitiamo la dimensione del buffer a 1472 byte
		// Essendo l'MTU del frame Ethernet 1500 byte, l'header IP 20 byte e l'header UDP 8 byte
		// 1500 - 20 - 8 = 1472
		// Questo serve per prevenire la frammentazione e l'eventuale perdita di un frammento (con la conseguente perdita dell'intero pacchetto)
		byte[] req = new byte[DiscoveryServer.MAX_PAYLOAD];
		byte[] res = new byte[MAX_FILE_LENGTH];
		
		DatagramPacket packetIn = new DatagramPacket(req, req.length);
		DatagramPacket packetOut = new DatagramPacket(res, res.length, dsAddr, dsPort);
		
		try {
			socket.receive(packetIn);
			
			// algoritmo file visual
			System.out.println("========== LISTA FILE ==========");
			
			bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
			din = new DataInputStream(bin);
			
			int totalFrag = din.readInt();
			String[] msgFrag = new String[totalFrag];
			
			String fragment = null;
			int index = -1;
			int colon = -1;
			for(int i=0; i<totalFrag; i++) {
				// nel caso un pacchetto venga perso, il timeout scatta a 10s
				socket.receive(packetIn);
								
				fragment = new String(packetIn.getData(), 0, packetIn.getLength());
				
				colon = fragment.indexOf(':');
				if (colon == -1) {
					System.err.println("[CLIENT_DS] : Errore -> Frammento malformato");
					System.exit(5);
				}
				
				index = Integer.parseInt(fragment.substring(0, colon));
				msgFrag[index] = fragment.substring(colon + 1);
			}
			
			// stampa della lista ordinata
			for(int i=0; i<totalFrag; i++) System.out.println(msgFrag[i]);
			
			
			// richiesta del file da console
			System.out.print("\nInserisci il nome del file: ");
			String fileName = null;
			try {
				fileName = in.readLine();
				if (fileName == null || fileName.isBlank())
					throw new IOException(" nome inserito vuoto");
			} catch (IOException e) {
				System.err.println("[CLIENT_DS] : Errore -> Input non valido: " + e);
				System.exit(6);
			}
			
			// comunicazione al Discovery Server del nome del file scelto
			dout.writeUTF(fileName);
			dout.flush();
			
			res = bout.toByteArray();
			packetOut.setData(res);
			socket.send(packetOut);
			
			bout.reset();
			
			System.out.println("[CLIENT_DS] : Ricerca Row Swap Server...");
						
			// ricezione endpoint del Row Swap Server che effettuerà lo scambio delle righe
			socket.receive(packetIn);
			bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
			din = new DataInputStream(bin);
			
			String[] rsEndpoint = din.readUTF().split(":");
			try {
				if(rsEndpoint[0].equals("0") && rsEndpoint[1].equals("0"))
					throw new IllegalArgumentException();
				
				rsAddr = InetAddress.getByName(rsEndpoint[0]);
				rsPort = Integer.parseInt(rsEndpoint[1]);
			} catch(UnknownHostException e) {
				System.err.println("[CLIENT_DS] : Errore -> Indirizzo Row Swap Server passato sconosciuto: " + e);
				System.exit(7);
			} catch(NumberFormatException e) {
				System.err.println("[CLIENT_DS] : Errore -> Porta Row Swap Server passata non valida: " + e);
				System.exit(7);
			} catch(IllegalArgumentException e) {
				System.err.println("[CLIENT_DS] : Errore -> Il nome del file non è stato trovato: il file passato non è presente nella lista.");
				System.exit(7);
			}			
			
		} catch (IOException e) {
			System.err.println("[CLIENT_DS] : Errore -> Socket non valida: " + e);
			System.exit(4);
		}	
		
		System.out.println("[CLIENT] : Row Swap Server trovato.");
		
		
		// comunicazione con il Row Swap Server
		
		req = new byte[1];
		res = new byte[21];
		// dato che il formato della richiesta consiste in linea1:linea2, vi sono 18 byte suddivisi tra i due numeri
		// 2 byte di intestazione UTF, 1 byte per ":" -> 21 - 2 - 1 = 18 byte di contenuto
		
		packetIn = new DatagramPacket(req, req.length);
		packetOut = new DatagramPacket(res, res.length, rsAddr, rsPort);
		
		try {			
			// richiesta degli indici da console
			int index1 = -1;
			int index2 = -1;
			
			try {
				System.out.print("\nInserisci il primo indice: ");
				index1 = Integer.parseInt(in.readLine());
				System.out.print("Inserisci il secondo indice: ");
				index2 = Integer.parseInt(in.readLine());
			} catch (IOException | NumberFormatException e) {
				System.err.println("[CLIENT_RS] : Errore -> Input non valido: " + e);
				System.exit(8);
			}
			
			// comunicazione al Row Swap Server degli indici scelti
			dout.writeUTF(index1 + ":" + index2);
			dout.flush();
			
			res = bout.toByteArray();
			packetOut.setData(res);
			socket.send(packetOut);
			
			bout.reset();
				
			// ricezione risultato scambio Row Swap Server
			packetIn = new DatagramPacket(new byte[4], 4);
			socket.receive(packetIn);
			
			bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
			din = new DataInputStream(bin);
			
			byte flag = din.readByte();
			if(flag == RESPONSE_OK) System.out.println("[CLIENT_RS] : Operazione eseguita con successo.");
			else if(flag == RESPONSE_SERVER_OFFLINE) System.out.println("[CLIENT_RS] : L'perazione non è andata a buon fine perché il Row Swap Server è offline.");
			else if(flag == RESPONSE_ERR) System.out.println("[CLIENT_RS] : L'operazione non è andata a buon fine.");					
			
		} catch (IOException e) {
			System.err.println("[CLIENT_RS] : Errore -> Socket non valida: " + e);
			System.exit(4);
		}	
	}
}
