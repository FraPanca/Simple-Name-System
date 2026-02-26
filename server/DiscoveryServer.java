package server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.locks.ReentrantLock;


public class DiscoveryServer {
	
	private final static int REQ_VISUALIZATION_FILE = 1;
	
	private final static byte RESPONSE_ERR = 1;
	private final static byte RESPONSE_SERVER_OPENING = 3;
	private final static byte RESPONSE_SERVER_CLOSING = 4;
	
	
	private final static int MAX_RS_SERVER = 256;
	private final static int MAX_FILE_LENGTH = 256;
	public final static int MAX_PAYLOAD = 1400;
	// Non conoscendo la lunghezza totale in byte dell'intera lista file e dato che UDP può essere frammentato
	// dovremmo limitare la dimensione del buffer a 1472 byte ->
	// Essendo: l'MTU del frame Ethernet 1500 byte, l'header IP 20 byte e l'header UDP 8 byte
	// Allora: 1500 - 20 - 8 = 1472
	// Questo serve per prevenire la frammentazione e l'eventuale perdita di un frammento (con la conseguente perdita dell'intero pacchetto)
	// Inoltre dato che nell'UDP non c'è la numerazione "integrata", all'inizio di ogni pacchetto inviamo un header con relativo numero
	// Per questo assegniamo alla costante un valore di 1400 anziché 1472
	
	
	private static int clientPort = -1;
	private static int rsPort = -1;
	
	private static int counter = 0;
	
	// 0 -> IP, 1 -> porta, 2 -> file name
	private static String[][] tableRs = new String[MAX_RS_SERVER][3];
	
	// dato che la tabella è condivisa tra i due thread, è necessario usare il lock per evitare sezioni critiche
	private static final ReentrantLock tableLock = new ReentrantLock();
	
	
	public static void main(String[] args) {
		// args: porta Client, porta registrazione Row Swap Server
				
		// controllo argomenti
		if(args.length != 2) {
			System.err.println("[SERVER] : Errore -> Numero di argomenti errati.");
			System.exit(1);
		}
		
		
		try {
			clientPort = Integer.parseInt(args[0]);
			if(clientPort > 65535 || clientPort < 0)
				throw new NumberFormatException();
		} catch(NumberFormatException e) {
			System.err.println("[SERVER] : Errore -> Porta del Client non valida: " + e);
			System.exit(1);
		}
		
		try {
			rsPort = Integer.parseInt(args[1]);
			if(rsPort > 65535 || rsPort < 0)
				throw new NumberFormatException();
		} catch(NumberFormatException e) {
			System.err.println("[SERVER] : Errore -> Porta del Row Swap Server non valida: " + e);
			System.exit(1);
		}
		
		
		// inizializzo tableRs con degli IP "N" per indicare che quelle celle non sono utilizzate
		for(int i=0; i<MAX_RS_SERVER; i++) tableRs[i][0] = "N";
		
		System.out.println("[SERVER] : Il Discovery Server si sta avviando...");
		
		
		DatagramSocket socketClient = null;
		DatagramSocket socketRS = null;
		try {
			socketClient = new DatagramSocket(clientPort);
			socketRS = new DatagramSocket(rsPort);
			
			// 2 thread: 1 che comunica con i Row Swap Server, 1 che comunica con i Client
			
			// Thread che comunica con il Discovery Server
			DSServer_ThreadRS threadDs = new DSServer_ThreadRS(socketRS);	
			threadDs.start();
					
			// Thread che comunica con il Client
			DSServer_ThreadClient threadClient = new DSServer_ThreadClient(socketClient);	
			threadClient.start();		
			
			
			threadDs.join();
			threadClient.join();
		} catch (SocketException e) {
			System.err.println("[SERVER] : Errore -> Socket non valida: " + e);
			System.exit(2);
		} catch (InterruptedException e) {
			System.err.println("[SERVER] : Errore -> Thread terminato improvvisamente: " + e);
			System.exit(3);
		} finally {
			if(socketClient != null) socketClient.close();
			if(socketRS != null) socketRS.close();
		}		
	}
	
	
	
	private static class DSServer_ThreadRS extends Thread {
		
		private DatagramSocket socket;
		
		public DSServer_ThreadRS(DatagramSocket socket) {
			this.socket = socket;
		}
		
		
		public void run() {			
			
			byte[] req = new byte[MAX_FILE_LENGTH];
			byte[] res = new byte[1];
			
			ByteArrayInputStream bin = null;
			DataInputStream din = null;
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			DataOutputStream dout = new DataOutputStream(bout);
			
			DatagramPacket packetIn = new DatagramPacket(req, req.length);
			DatagramPacket packetOut = null;
			
			
			while(true) {
				try {
					socket.receive(packetIn);
					
					packetOut = new DatagramPacket(res, res.length, packetIn.getAddress(), packetIn.getPort());
					
					System.out.println("[SERVER DS_RS] : Pacchetto ricevuto dal Row Swap Server " + packetIn.getAddress().getHostAddress() + " - " + packetIn.getPort());
					
					bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
					din = new DataInputStream(bin);
										
					byte flag = -1;
					byte cmd = din.readByte();
					if(cmd == RESPONSE_SERVER_OPENING) { // il Row Swap Server chiede di registrarsi
						socket.receive(packetIn);
						
						bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
						din = new DataInputStream(bin);
						
						// [0] -> fileName - [1] -> porta
						String[] infoRs = din.readUTF().split(":");
						
						bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
						din = new DataInputStream(bin);
						
						
						flag = RESPONSE_SERVER_OPENING;
						
						if(counter < MAX_RS_SERVER) {
							String addr = packetIn.getAddress().getHostAddress();
							int port = Integer.parseInt(infoRs[1]);
							
							// controllo sull'endpoint
							tableLock.lock();
							try {
								for(int i=0; i<MAX_RS_SERVER; i++) {
									if(!tableRs[i][0].equals("N")) { // skip se la posizione è vuota		
										if(tableRs[i][0].equals(addr) && tableRs[i][1].equals(port + ""))
											throw new IllegalArgumentException();
									}
								}
								
								for(int i=0; i<MAX_RS_SERVER; i++) {
									if(!tableRs[i][0].equals("N")) { // skip se la posizione è vuota	
										if(tableRs[i][2].equals(infoRs[0]))
											throw new IllegalArgumentException();
									}
								}	
								
								int index = -1;
								for (int i = 0; i < MAX_RS_SERVER; i++) {
								    if (tableRs[i][0].equals("N")) {
								        index = i;
								        break;
								    }
								}
								if (index != -1) {
								    tableRs[index][0] = addr;
								    tableRs[index][1] = port + "";
								    tableRs[index][2] = infoRs[0];
								    
								    counter++;
									
									System.out.println("[SERVER DS_RS] : Registrazione del file " + infoRs[0] + " avvenuta con successo.");
								}
								
							} catch(IllegalArgumentException e) {
								System.err.println("[SERVER DS_RS] : Errore -> Endpoint/Nome del file già registrato: " + addr + " - " + port);
								flag = RESPONSE_ERR;
							} finally {
								tableLock.unlock();
							}
							
						} else {
							System.err.println("[SERVER DS_RS] : Errore -> Non è possibile registrare nuovi Row Server attualmente.");
							flag = RESPONSE_ERR;
						}	
					} else if(cmd == RESPONSE_SERVER_CLOSING) {
						flag = RESPONSE_SERVER_CLOSING;
						
						socket.receive(packetIn);
						
						bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
						din = new DataInputStream(bin);
											
						int port = din.readInt();
						
						boolean found = false;
						
						tableLock.lock();
						try {
							
							for(int i=0; i<MAX_RS_SERVER && !found; i++) {
								if(!tableRs[i][0].equals("N")) { // skip se la posizione è vuota	
									if(tableRs[i][0].equals(packetIn.getAddress().getHostAddress()) && tableRs[i][1].equals(port + "")) {
										tableRs[i][0] = "N";
										tableRs[i][1] = null;
										tableRs[i][2] = null;
										
										counter--;
										found = true;
									}
								}
							}
						} finally {
							tableLock.unlock();
						}
						
						if(!found) {
							flag = RESPONSE_ERR;
							System.err.println("[SERVER DS_RS] : Errore -> Non è stato possibile effetturare la de-registrazione del Row Swap Server " + packetIn.getAddress().getHostAddress() + " - " + packetIn.getPort());
						} else {
							System.out.println("[SERVER DS_RS] : De-registrazione del Row Swap Server " + packetIn.getAddress().getHostAddress() + " - " + packetIn.getPort() + " avvenuta con successo.");
						}
						
					} else {
						flag = RESPONSE_ERR;						
						System.err.println("[SERVER DS_RS] : Errore -> Comando errato del Row Swap Server " + packetIn.getAddress().getHostAddress() + " - " + packetIn.getPort());			
					}
					
					dout.writeByte(flag);
					dout.flush();
					
					res = bout.toByteArray();
					packetOut.setData(res);
					socket.send(packetOut);
										
					bout.reset();
				} catch (IOException e) {
					System.err.println("[SERVER DS_RS] : Errore -> Socket non valida: " + e);
					System.exit(4);
				}
			}
		}
	}


	private static class DSServer_ThreadClient extends Thread {
		
		private DatagramSocket socket;
		
		public DSServer_ThreadClient(DatagramSocket socket) {
			this.socket = socket;
		}
		
		
		public void run() {

			byte[] req = new byte[MAX_FILE_LENGTH];
			byte[] res = new byte[MAX_PAYLOAD];
			
			ByteArrayInputStream bin = null;
			DataInputStream din = null;
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			DataOutputStream dout = new DataOutputStream(bout);
			
			DatagramPacket packetIn = new DatagramPacket(req, req.length);
			DatagramPacket packetOut = null;
			
			
			while(true) {
				try {
					socket.receive(packetIn);
					
					packetOut = new DatagramPacket(res, res.length, packetIn.getAddress(), packetIn.getPort());
					
					if(packetIn.getLength() == 1) { // caso il cui il Client richieda la visualizzazione della lista file
						if(packetIn.getData()[0] == REQ_VISUALIZATION_FILE) {
							
							String listFile = "";
							
							tableLock.lock();
							try {
								for(int i=0; i<MAX_RS_SERVER; i++) {
									if(!tableRs[i][0].equals("N")) { // skip se la posizione è vuota	
										listFile += "\t" + tableRs[i][2] + "\n";
									}
								}
							} finally {
								tableLock.unlock();
							}
							
							
							byte[] data = listFile.getBytes();
							int totalFrag = (int) Math.ceil((double) data.length / MAX_PAYLOAD);
							
							// invio al Client dei frammenti totali che saranno inviati
							dout.writeInt(totalFrag);
							dout.flush();
							
							res = bout.toByteArray();
							packetOut.setData(res);
							socket.send(packetOut);
							
							bout.reset();
							
							// frammentazione e numerazione						
							for(int i=0; i<totalFrag; i++) {
								int start = i * MAX_PAYLOAD;
								int end = Math.min(start + MAX_PAYLOAD, data.length);
								
								res = new byte[MAX_PAYLOAD];
								
								// numerazione del frammento esplicita
								byte[] header = (i + ":").getBytes();
								System.arraycopy(header, 0, res, 0, header.length);
								System.arraycopy(data, start, res, header.length, end - start);
								
								packetOut.setData(res, 0, header.length + (end - start));
								socket.send(packetOut);
							}
							
						} else System.err.println("[SERVER DS_CLIENT] : Errore -> Ricevuto pacchetto di lunghezza 1 non valido.");
					} else { // caso il cui il Client abbia inviato il nome del file
						bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
						din = new DataInputStream(bin);
						
						String fileName = din.readUTF();
						System.out.println("[SERVER DS_CLIENT] : Il client " + packetIn.getAddress().getHostAddress() + " ha richiesto il file: " + fileName);
						
						boolean found = false;
						tableLock.lock();
						try {
							for(int i=0; i<MAX_RS_SERVER && !found; i++) {
								if(!tableRs[i][0].equals("N")) { // skip se la posizione è vuota	
									if(tableRs[i][2].equals("server/resources/" + fileName)) {
										dout.writeUTF(tableRs[i][0] + ":" + tableRs[i][1]);
										found = true;
									}
								}
							}
						} finally {
							tableLock.unlock();
						}

						if(!found) dout.writeUTF("0:0");
						
						
						dout.flush();
						
						res = bout.toByteArray();
						packetOut.setData(res);
						socket.send(packetOut);
						
						bout.reset();
					}
					
				} catch (IOException e) {
					System.err.println("[SERVER DS_CLIENT] : Errore -> Socket non valida: " + e);
					System.exit(4);
				}
			}
		}
	}

}
