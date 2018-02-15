package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) {//Configuration config
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    rd.processIPAddress = "127.0.0.1";//real IP address
    rd.processPortNumber = config.getShort("socs.network.router.portNum");
    lsd = new LinkStateDatabase(rd);
    
    try {
    	new Thread(new create_server_Socket(rd.processPortNumber,this)).start();
    }catch(Exception e) {
    	e.printStackTrace();;
    }
  }
  
  class create_server_Socket implements Runnable{
	  private ServerSocket serverSocket;
	  Router router;
	  public create_server_Socket(int port, Router r) throws IOException {
		  serverSocket = new ServerSocket(port);
		  router = r;
		  System.out.println("created a server socket at port "+serverSocket.getLocalPort()+ "and ip "+ serverSocket.getInetAddress());
	  }
	  
	  public void run() {
//		  System.out.println("entered the run function");
		  while(true) {
			  try {
				  Socket server = serverSocket.accept();
//				  System.out.println("server created!");
				  new Thread(new ClientService(server,router)).start();
			  }catch(SocketTimeoutException s) {
				  System.out.println("socket times out");
				  break;
			  }catch(Exception e) {
				  e.printStackTrace();
				  break;
			  }
		  }
	  }  
  }
  
  class ClientService implements Runnable{
	  
	  Socket server;
	  Router router;
	  ClientService(Socket s, Router r){
		  server = s;
		  router = r;
	  }

	public void run() {
		ObjectInputStream in = null;
		ObjectOutputStream out = null;
		
		try {
			in = new ObjectInputStream(server.getInputStream());//get the message from the client
			
			SOSPFPacket message = (SOSPFPacket)in.readObject();
//			System.out.println("server: message received from client!");
			if(message.sospfType==0) {//hello message
				System.out.println("Received Hello from "+ message.neighborID);
				//TODO:
				if(!router.isNeighbour(message.neighborID)) {
					router.addNeighbour(message.srcProcessIP, message.srcProcessPort, message.neighborID);
					System.out.println("Set "+message.neighborID + " to INIT state");
				}
				out = new ObjectOutputStream(server.getOutputStream());
				SOSPFPacket response = new SOSPFPacket();
				response.sospfType = 0;
				response.neighborID = router.rd.simulatedIPAddress;
				response.srcProcessIP = router.rd.processIPAddress;
				response.srcProcessPort = router.rd.processPortNumber;
				out.writeObject(response);//finish step 2
				//start step 4
				message = (SOSPFPacket) in.readObject();
				if(message.sospfType==0) {
					System.out.println("Received hello from "+message.neighborID);
					if ( router.getStatus(message.neighborID)==RouterStatus.INIT) {
						router.setStatus(message.neighborID, RouterStatus.TWO_WAY);
						System.out.println("Set "+message.neighborID + " to TWO WAY state");
					}
				}
				
			}
		}catch(Exception e) {
			e.printStackTrace();
		}finally {
			try {
				out.close();
				in.close();
				server.close();
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	  
  }
  
  public boolean isNeighbour(String simIpAddress) {
	  int i = 0;
	  for(i = 0; i<ports.length;i++) {
		  if(ports[i]!=null) {
				if(ports[i].router2.simulatedIPAddress.equals(simIpAddress))
					return true;
			}
	  }
	  return false;
  }
  
  public void addNeighbour(String processIP, short processPort, String simulatedIP) {
	  int i = 0;
	  for(i = 0; i< ports.length; i++) {
		  if ( ports[i]==null) {
			  RouterDescription remoteRouter = new RouterDescription();
			  remoteRouter.processIPAddress = processIP;
			  remoteRouter.processPortNumber = processPort;
			  remoteRouter.simulatedIPAddress = simulatedIP;
			  remoteRouter.status = RouterStatus.INIT;
			  ports[i] = new Link(this.rd,remoteRouter);
			  break;
		  }
	  }
  }
  
  class Client implements Runnable{
	  private String serverName;
	  private int port;
	  private SOSPFPacket packet;
	  
	  public Client(String servername, int port, SOSPFPacket packet) {
		  this.serverName = servername;
		  this.port = port;
		  this.packet = packet;
	  }

	public void run() {
		
		OutputStream outToServer = null;
		InputStream inFromServer = null;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		Socket client = null;
		try {
//			System.out.println(packet.sospfType);
//			System.out.println(serverName);
//			System.out.println(port);
			if(packet.sospfType == 0) { // sending out hello message
				client = new Socket(serverName, port);
				outToServer = client.getOutputStream();
				out = new ObjectOutputStream(outToServer);
				out.writeObject(packet);//send message to server
//				System.out.println("client: message sent to server");
				
				inFromServer = client.getInputStream();
				in = new ObjectInputStream(inFromServer);// wait for a response from server
//				System.out.println("client: message received from server");
				SOSPFPacket response = (SOSPFPacket)in.readObject();//receive a message from server
				if(response.sospfType==0) {
					if(getStatus(response.neighborID)==null) {
						setStatus(response.neighborID,RouterStatus.TWO_WAY);
						System.out.println("Set " + response.neighborID + " state to TWO_WAY");
					}
					
					out.writeObject(packet);//send message to server
					
				}
				
			}
		}catch(Exception e) {
			e.printStackTrace();
		}finally {
			try {
				out.close();
				in.close();
				client.close();
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	  
  }
  
  public RouterStatus getStatus(String simulated_ip) {
	  int i = 0;
	  for(i = 0; i< ports.length; i++) {
		  if(ports[i]!=null) {
			  if(ports[i].router2.simulatedIPAddress.equals(simulated_ip)) {
				  return ports[i].router2.status;
			  }
		  }
	  }
	  return null;
  }
  
  public void setStatus(String simulated_ip, RouterStatus status) {
	  int i = 0;
	  for(i = 0; i< ports.length; i++) {
		  if(ports[i]!=null) {
			  if(ports[i].router2.simulatedIPAddress.equals(simulated_ip)) {
				  ports[i].router2.status = status;
				  break;
			  }
		  }
	  }
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {

  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort,
                             String simulatedIP, short weight) {
	  
	  int i = 0;
	  for(i = 0; i< ports.length; i++) {
		  if ( ports[i]==null) {
			  RouterDescription remoteRouter = new RouterDescription();
			  remoteRouter.processIPAddress = processIP;
			  remoteRouter.processPortNumber = processPort;
			  remoteRouter.simulatedIPAddress = simulatedIP;
			  
			  ports[i] = new Link(this.rd, remoteRouter);
//			  System.out.println("neighbour attached!");
			  
			  LSA new_lsa = lsd._store.get(rd.simulatedIPAddress);
			  new_lsa.lsaSeqNumber++;
			  LinkDescription new_link = new LinkDescription();
			  new_link.linkID = simulatedIP;
			  new_link.portNum = i;
			  new_link.tosMetrics = weight;
			  
			  new_lsa.links.add(new_link);
			  break;
		  }
	  }
	  if(i==4) {
		  System.out.println("no available port");
	  }

  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
//	  System.out.println("started");
	  int i = 0;
	  for(i = 0; i< ports.length; i++) {
		  if(ports[i]!=null) {
			  if(ports[i].router2.status==null) {//TODO: may have problem here, server cannot receive message
				  String servername = ports[i].router2.processIPAddress;
				  int port = ports[i].router2.processPortNumber;
				  SOSPFPacket packet = new SOSPFPacket();
				  packet.sospfType=0;
				  packet.neighborID = rd.simulatedIPAddress;
				  packet.srcProcessIP = rd.processIPAddress;
				  packet.srcProcessPort = rd.processPortNumber;
				  new Thread(new Client(servername, port, packet)).start();
			  }
		  }
		  
	  }
	  //
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {
  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
	  int i = 0;
	  for(i = 0; i< ports.length; i++) {
		  if(ports[i]!=null) {
			  if(ports[i].router2.status!=null) {
				  System.out.println("Ip Address of the neightbour "+i+" is : "+ ports[i].router2.simulatedIPAddress);
			  }
		  }
		  
	  }
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else {
          //invalid command
          break;
        }
        System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
