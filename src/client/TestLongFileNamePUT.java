package client;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TestLongFileNamePUT
{
	private static final String SERVER_IP = "127.0.0.1";
	private static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String FILENAME = "larger.txt";
	public static final String PATH = "/Users/joakimbergqvist/Documents/Joakims/Document/Network/Assignment3/SourceFolder/ClientFolder/";
	public static int serverRandomPort = 0;
	//OP codes
	private static final int OP_RRQ = 1;
	private static final int OP_WRQ = 2;
	private static final int OP_DAT = 3;
	private static final int OP_ACK = 4;
	private static final int OP_ERR = 5;
	
	private static DatagramSocket datagramSocket = null;
	private static InetAddress inetAddress = null;
	private static byte[] requestArray;
	private static DatagramPacket requestPacket;
	private static DatagramPacket inPacket;
	
	
	public static void runTest() throws Exception {
		put(FILENAME);
	}
	private static void put(String fileName) throws Exception {
		inetAddress = InetAddress.getByName(SERVER_IP);
		datagramSocket = new DatagramSocket();
		requestArray = createRequest(OP_WRQ, fileName, "octet");
		requestPacket = new DatagramPacket(requestArray,
				requestArray.length, inetAddress, TFTPPORT);
		
		datagramSocket.send(requestPacket);
		write_DATA_receive_ACK(FILENAME, datagramSocket);
	}
	
	private static byte[] createRequest(final int opCode, final String fileName, final String mode) {
		
		//        2 bytes    string   1 byte     string   1 byte
		//        -----------------------------------------------
		// RRQ/  | 01/02 |  Filename  |   0  |    Mode    |   0  |
		// WRQ    -----------------------------------------------
		byte[] request = new byte[BUFSIZE];
		
		int index = 0;
		request[index] = 0;
		index++;
		request[index] = (byte) opCode;
		index++;
		for (int i = 0; i < 514; i++) {
			request[index] = (byte) 'a';
			index++;
		}
		
		return request;
	}
	
	
	private static boolean write_DATA_receive_ACK(String requestedFile, DatagramSocket sendSocket) throws Exception
	{
		byte[] buffer = new byte[BUFSIZE];
		DatagramPacket firstAck = new DatagramPacket(buffer,0,buffer.length);
		datagramSocket.receive(firstAck);
		System.out.println("first received op should be 5, was " + buffer[1]);
		System.out.println("err code should be 4, was " + buffer[3]);
		
		return true;
	}
	
	
	
	
}


