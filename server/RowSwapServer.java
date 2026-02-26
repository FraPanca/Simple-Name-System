package server;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class RowSwapServer {
	
	private final static int MAX_FILE_LENGTH = 256;

	private final static byte RESPONSE_OK = 0;
	private final static byte RESPONSE_ERR = 1;
	private final static byte RESPONSE_SERVER_OPENING = 3;
	private final static byte RESPONSE_SERVER_CLOSING = 4;
	private final static byte RESPONSE_SERVER_OFFLINE = 5;
	
	private static InetAddress dsAddr = null;
	private static int dsPort = -1;
	private static int rsPortClient = -1;
	
	private static volatile boolean isOn = false;
	
	
	public static void main(String[] args) {
		// args: IP Discovery Server, porta Registrazione Discovery Server, porta Row Swap Server (socket Discovery Server), nome file
		
		
		// controllo argomenti
		if(args.length != 4) {
			System.err.println("[SERVER] : Errore -> Numero di argomenti errati.");
			System.exit(1);
		}
		
		
		try {
			dsAddr = InetAddress.getByName(args[0]);
		} catch(UnknownHostException e) {
			System.err.println("[SERVER] : Errore -> Indirizzo Discovery Server non valido: " + e);
			System.exit(1);
		}
		
		try {
			dsPort = Integer.parseInt(args[1]);
			if(dsPort > 65535 || dsPort < 0)
				throw new NumberFormatException();
		} catch(NumberFormatException e) {
			System.err.println("[SERVER] : Errore -> Porta Discovery Server non valida: " + e);
			System.exit(1);
		}
		
		int rsPortDS = -1;
		try {
			rsPortDS = Integer.parseInt(args[2]);
			if(rsPortDS > 65535 || rsPortDS < 0)
				throw new NumberFormatException();
		} catch(NumberFormatException e) {
			System.err.println("[SERVER] : Errore -> Porta Row Swap Server non valida: " + e);
			System.exit(1);
		}
		
		try {
			if(args[3].trim().equals(""))
				throw new IllegalArgumentException();
		} catch(IllegalArgumentException e) {
			System.err.println("[SERVER] : Errore -> Nome file non valido: " + e);
			System.exit(1);
		}
		
		
		System.out.println("[SERVER] : Il Row Swap Server si sta avviando sulla porta " + rsPortDS + "...");
		
		
		DatagramSocket socketClient = null;
		DatagramSocket socketDS = null;
		try {			
			// creazioni di due socket: una in ascolto con il Discovery Server e una in ascolto dei Client
			// se associassi sulla stessa porta entrambe le socket, non si saprebbe con certezza in quale dei due thread verrebbe "attivata" la receive
			socketDS = new DatagramSocket(rsPortDS);
			socketClient = new DatagramSocket();
			rsPortClient = socketClient.getLocalPort();
			
			
			// 3 thread: 1 che comunica con il Discovery Server, 1 che comunica con i Client, 1 che legge da input i comandi
			
			// Thread che comunica con il Discovery Server
			RSServer_ThreadDS threadDS = new RSServer_ThreadDS(socketDS, args[3]);	
			threadDS.start();
						
			// Thread che comunica con il Client
			RSServer_ThreadClient threadClient = new RSServer_ThreadClient(socketClient, args[3]);	
			threadClient.start();
													
			// Thread in ascolto su stdin
			RSServer_ThreadStdin threadStdin = new RSServer_ThreadStdin(socketDS);
			threadStdin.start();
									
			
			threadStdin.join();
			threadDS.join();
		} catch (SocketException e) {
			System.err.println("[SERVER] : Errore -> Socket non valida: " + e);
			System.exit(2);
		} catch (InterruptedException e) {
			System.err.println("[SERVER] : Errore -> Thread terminato improvvisamente: " + e);
			System.exit(3);
		} finally {
			if(socketClient != null) socketClient.close();
			if(socketDS != null) socketDS.close();
		}
	}
	
	
	
	private static class RSServer_ThreadDS extends Thread {
		
		private DatagramSocket socket;
		private String fileName;
		
		public RSServer_ThreadDS(DatagramSocket socket, String fileName) {
			this.socket = socket;			
			this.fileName = fileName;
		}
		
		
		public void run() {			
			
			byte[] req = new byte[1];
			byte[] res = new byte[MAX_FILE_LENGTH];
			
			ByteArrayInputStream bin = null;
			DataInputStream din = null;
			ByteArrayOutputStream bout = null;
			DataOutputStream dout = null;
			
			DatagramPacket packetIn = new DatagramPacket(req, req.length);
			DatagramPacket packetOut = new DatagramPacket(res, res.length, dsAddr, dsPort);
			
			// registrazione al Discovery Server comunicando il file
			bout = new ByteArrayOutputStream();
            dout = new DataOutputStream(bout);
            try {
            	dout.writeByte(RESPONSE_SERVER_OPENING);
    			dout.flush();
    			res = bout.toByteArray();
    				
    			packetOut.setData(res);
    			socket.send(packetOut);
    			
    			bout.reset();
    			

				dout.writeUTF("server/resources/" + fileName + ":" + rsPortClient);
				dout.flush();
				res = bout.toByteArray();
				
				packetOut.setData(res);
				socket.send(packetOut);
				
				bout.reset();
			} catch (IOException e) {
				System.err.println("[SERVER RS_DS] : Errore -> Non è stato possibile comunicare il nome del file: " + e);
				System.exit(4);
			}
			
			System.out.println("[SERVER RS_DS] : Comunico al Discovery Server il file...");
			
			while(true) {
				try {
					try {
						socket.receive(packetIn);
					} catch (IOException e) {
						System.err.println("[SERVER RS_DS] : Errore -> Socket non valida: " + e);
						System.exit(5);
					}
					
					// se il pacchetto proviene dal Discovery Server allora viene elaborato, altimenti verrà gestito dall'altro thread
					if(packetIn.getAddress().equals(dsAddr) && packetIn.getPort() == dsPort) {
						System.out.println("[SERVER RS_DS] : Pacchetto ricevuto dal Discovery Server.");
						
						bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
						din = new DataInputStream(bin);
						
						byte flag = din.readByte();
						if(flag == RESPONSE_SERVER_OPENING) {
							isOn = true;
							System.out.println("[SERVER RS_DS] : Registrazione al Discovery Server avvenuta correttamente.");
							System.out.println("[SERVER RS_DS] : Il server è online.");
							System.out.println("[SERVER RS_DS] : Inserisci \"Termina\" per de-registrare e terminare il server...\n");
						} else if(flag == RESPONSE_SERVER_CLOSING) {
							isOn = false;
							System.out.println("[SERVER RS_DS] : Chiusura del Row Server.");
							
							System.exit(0);
						} 
						else throw new IOException((flag == RESPONSE_ERR) ? "non è stato possibile registrare/de-registrare il server." : "errore imprevisto.");						
					
					}
					
				} catch (IOException e) {
					System.err.println("[SERVER RS_DS] : Errore -> Impossibile compiere l'operazione: " + e);
					System.exit(6);
				}
			}
		}
	}


	private static class RSServer_ThreadClient extends Thread {
		
		private DatagramSocket socket;
		private String fileName;
		
		public RSServer_ThreadClient(DatagramSocket socket, String fileName) {
			this.socket = socket;
			this.fileName = fileName;
		}
		
		
		public void run() {
			
			// dato che il formato della richiesta consiste in linea1:linea2, vi sono 18 byte suddivisi tra i due numeri
			// 2 byte di intestazione UTF, 1 byte per ":" -> 21 - 2 - 1 = 18 byte di contenuto
			byte[] req = new byte[21];
			byte[] res = new byte[1];
			
			ByteArrayInputStream bin = null;
			DataInputStream din = null;
			ByteArrayOutputStream bout = null;
			DataOutputStream dout = null;
			
			DatagramPacket packetIn = new DatagramPacket(req, req.length);
			DatagramPacket packetOut = null;
			
			
			BufferedReader in = null;
			PrintWriter out = null;
			
			int index1, index2;
			byte flag;
			
			while(true) {
				try {
					socket.receive(packetIn);					
					
					if(!(packetIn.getAddress().equals(dsAddr) && packetIn.getPort() == dsPort)) {
						packetOut = new DatagramPacket(res, res.length, packetIn.getAddress(), packetIn.getPort());
						
						bout = new ByteArrayOutputStream();
			            dout = new DataOutputStream(bout);
						
						if(isOn) {
							index1 = -1; index2 = -1;
							flag = RESPONSE_OK;
							
							bin = new ByteArrayInputStream(packetIn.getData(), 0, packetIn.getLength());
							din = new DataInputStream(bin);
							
							String[] indexes = din.readUTF().split(":");
							if(indexes.length == 2) {
								try {
					            	index1 = Integer.parseInt(indexes[0]);
					            	index2 = Integer.parseInt(indexes[1]);
					            	
					            	if(index1 == index2 || index1 <= 0 || index2 <= 0) throw new NumberFormatException("gli indici inseriti devono essere positivi e differenti.");
					            } catch(NumberFormatException e) {
					            	System.err.println("[SERVER RS_CLIENT] : Errore -> Sono state inserite delle linee non valide: " + e);
					            	flag = RESPONSE_ERR;
					            }
					            
					            // eventuale switch degli indici
					            if(index1 > index2) {
									int indexTmp = index1;
									index1 = index2;
									index2 = indexTmp;
								}
					            
					            
					            // scambio delle righe
								try {
									in = new BufferedReader(new FileReader("server/resources/" + fileName));
									out = new PrintWriter("server/resources/" + fileName + ".tmp.txt");
									
									// se viene lanciata l'eccezione IOException è stato raggiunto l'EOF prima degli indici passati
									String l = null;
									int i = 1;
									for(; i<index1; i++) {
										if((l = in.readLine()) != null) out.println(l);
										else throw new IOException("l'indice " + index1 + " non è presente nel file.");
									}							
									
									String line = in.readLine();
									StringBuilder txtBetween = new StringBuilder();
									
									for(i=index1; i<index2-1; i++) {
										if((l = in.readLine()) != null) txtBetween.append(l).append("\n");
										else throw new IOException("l'indice " + index2 + " non è presente nel file.");
									}
									
									if((l = in.readLine()) != null)
										out.println(l);
									
									out.print(txtBetween.toString());
									out.println(line);
									
									while((line = in.readLine()) != null)
										out.println(line);
									
									
									in.close();
									out.close();
									
									try {
										Files.move(Paths.get("server/resources/" + fileName + ".tmp.txt"), Paths.get("server/resources/" + fileName), StandardCopyOption.REPLACE_EXISTING);
									} catch(IOException e) {
										System.err.println("[SERVER RS_CLIENT] : Errore -> Non è stato possibile compiere l'operazione col file il file:\n\t" + e);
						            	flag = RESPONSE_ERR;
									}									
								
									System.out.println("[SERVER RS_CLIENT] : scambio per il client " + packetIn.getAddress().getHostAddress() + " è stato effettuato correttamente.");
								} catch(IOException e) {								
									System.err.println("[SERVER RS_CLIENT] : Errore -> Sono state inserite delle linee non valide: " + e);
					            	flag = RESPONSE_ERR;
								} finally {
									if(in != null) in.close();
								}
							} else flag = RESPONSE_ERR;
				            	    				            
						} else {
							System.err.println("[SERVER RS_CLIENT] : Errore -> Un pacchetto ha provato a comunicare col server non ancora registrato.");
							flag = RESPONSE_SERVER_OFFLINE;
						}
						
						dout.writeByte(flag);
						dout.flush();
						
						res = bout.toByteArray();
						packetOut.setData(res);
						socket.send(packetOut);
						
						bout.reset();
					}
					
				} catch (IOException e) {
					System.err.println("[SERVER RS_CLIENT] : Errore -> Socket non valida: " + e);
					System.exit(4);
				}
			}
		}
	}


	private static class RSServer_ThreadStdin extends Thread {
		
		private DatagramSocket socket;
		
		private BufferedReader in = null;
		
		public RSServer_ThreadStdin(DatagramSocket socket) {
			this.socket = socket;
			
			in = new BufferedReader(new InputStreamReader(System.in));
		}
		
		
		public void run() {
			
			byte[] res = new byte[1];
			
			ByteArrayOutputStream bout = null;
			DataOutputStream dout = null;
			
			DatagramPacket packetOut = new DatagramPacket(res, res.length, dsAddr, dsPort);

			String cmd = null;
			try {
				while(true) {	
					if((cmd = in.readLine()) == null) {
						throw new IOException("Non è possibile leggere da console.");
					} else if(cmd.trim().equals("Termina")) {
						try {
							bout = new ByteArrayOutputStream();
				            dout = new DataOutputStream(bout);

				            // invio del comando
							dout.writeByte(RESPONSE_SERVER_CLOSING);
							dout.flush();
							res = bout.toByteArray();
								
							packetOut.setData(res);
							socket.send(packetOut);
							
							bout.reset();
							
							// invio del numero di porta
							dout.writeInt(rsPortClient);
							dout.flush();
							res = bout.toByteArray();
								
							packetOut.setData(res);
							socket.send(packetOut);
							
							bout.reset();
							
							System.out.println("[SERVER RS_CONSOLE] : Comunico al Discovery Server la terminazione del server.");
							break;
						} catch (IOException e) {
							System.err.println("[SERVER RS_CONSOLE] : Errore -> Socket non valida: " + e);
							System.exit(4);
						}
					}					
					
				}
			} catch(IOException e) {
				System.err.println("[SERVER RS_CONSOLE] : Errore -> " + e);
			}			
		}
	}
}

