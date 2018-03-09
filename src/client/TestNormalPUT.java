package client;
import exceptions.WrongOPException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TestNormalPUT
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
		byte[] request = new byte[2 + fileName.length() + 1 + mode.length() + 1];
		
		int index = 0;
		request[index] = 0;
		index++;
		request[index] = (byte) opCode;
		index++;
		for (int i = 0; i < fileName.length(); i++) {
			request[index] = (byte) fileName.charAt(i);
			index++;
		}
		request[index] = 0;
		index++;
		
		for (int i = 0; i < mode.length(); i++) {
			request[index] = (byte) mode.charAt(i);
			index++;
		}
		request[index] = 0;
		return request;
	}
	
	
	private static boolean write_DATA_receive_ACK(String requestedFile, DatagramSocket sendSocket) throws Exception
	{
		byte[] buffer = new byte[BUFSIZE];
		DatagramPacket firstAck = new DatagramPacket(buffer,0,buffer.length);
		datagramSocket.receive(firstAck);
		System.out.println("first ack op should be 4, was " + buffer[1]);
		System.out.println("first ack block should be 0, was " + buffer[3]);
		
		datagramSocket.connect(firstAck.getSocketAddress());
		File outputfile = new File(PATH + requestedFile);
		System.out.println("free space: " + outputfile.getFreeSpace());
		System.out.println("usable space: " + outputfile.getUsableSpace());
		
		long remainingFileBytes = outputfile.length();
		short blockNumber = 0;
		FileInputStream inputStream = new FileInputStream(outputfile);
		
		DatagramPacket outputPacket;
		
		
		
		while(remainingFileBytes >= 0)
		{
			blockNumber++;

			/* Opcode: 2 bytes */
			ByteBuffer wrap = ByteBuffer.wrap(buffer);
			wrap.putShort((short) OP_DAT);

			/* BlockNumber: 2 bytes */
			wrap.putShort(2, blockNumber);

			/* Data: 0 - 512 bytes */
			if(remainingFileBytes == 0) // If file is modulo 512, send an packet with only header to communicate end of file
			{
				outputPacket = new DatagramPacket(buffer, 4);
				remainingFileBytes = -1;
			} else if(remainingFileBytes < 512)
			{
				inputStream.read(buffer, 4, (int) remainingFileBytes);
				
				outputPacket = new DatagramPacket(buffer, (int) (remainingFileBytes + 4));
				remainingFileBytes = -1; // End of filetransmission
			} else
			{
				inputStream.read(buffer, 4, 512);
				outputPacket = new DatagramPacket(buffer, 516);
				remainingFileBytes -= 512;
			}

			/* Send the datagram */
			byte[] ACKBuffer = new byte[BUFSIZE];
			int packetsSent = 1;
			
			datagramSocket.send(outputPacket);
			System.out.println("packet sent, size: " + outputPacket.getLength());
			System.out.println("Sent blockNumber: " + blockNumber);
			
			boolean correctACK = false;
			while(!correctACK) {
				System.out.println(1);
				DatagramPacket ack = new DatagramPacket(ACKBuffer, ACKBuffer.length);
				System.out.println(2);
				
				try {
					datagramSocket.receive(ack);
					System.out.println("Received ACK");
					
					correctACK = parseACK(ACKBuffer,blockNumber);
					// Resends packet if incorrect ACK
					if(!correctACK) {
						datagramSocket.send(outputPacket);
						System.out.println("Incorrect ACK, sent packet again, size: " + outputPacket.getLength());
					}
					
					// Resends packets if socket timed out (nothing received for x seconds)
				} catch (SocketTimeoutException e) {
					System.out.println("SocketTimeoutException thrown");
					datagramSocket.send(outputPacket);
					System.out.println("packet sent again, size: " + outputPacket.getLength());
					System.out.println("Sent blockNumber: " + blockNumber);
				} catch (WrongOPException e) {
					throw new WrongOPException("Not the expected OP", buffer);
				}
				packetsSent++;
				
				// Returns false if 5 packets were sent but no correct ACK were received or socket timed out 5 times
				if(packetsSent == 5) {
					System.out.println("5 packets were sent, stopped sending more packets");
					return false;
				}
			}
		}
		return true;
	}
	
	
	private void send_ERR(int errID, DatagramSocket sendSocket) throws IOException
	{
		System.out.println("Error " + errID + " sent");
		int errorDatagramLength;
		byte[] errorBuffer = new byte[BUFSIZE];
		
		ByteBuffer wrap = ByteBuffer.wrap(errorBuffer);
		
		wrap.putShort((short) OP_ERR);
		
		wrap.putShort(2, (short)errID);
		String errorMSG = "";
		System.out.println("errorBuffer:");
		System.out.println(errorBuffer[2] +" " + errorBuffer[3]);
		
		switch(errID)
		{
			case 0:
				errorMSG = "There was something wrong with the server.";
				break;
			case 1:
				errorMSG = "File not found.";
				break;
			case 2:
				errorMSG = "Access violation.";
				break;
			case 3:
				errorMSG = "Disk full or allocation exceeded.";
				break;
			case 4:
				errorMSG = "Illegal TFTP operation.";
				break;
			case 5:
				errorMSG = "Unknown transfer ID.";
				break;
			case 6:
				errorMSG = "File already exists.";
				break;
		}
		
		for(int i = 0; i<errorMSG.length(); i++)
		{
			errorBuffer[i+4] = (byte)errorMSG.charAt(i);
		}
		errorBuffer[4+errorMSG.length()] = 0;
		errorDatagramLength = errorMSG.length() +5;
		
		DatagramPacket outputDatagram = new DatagramPacket(errorBuffer, errorDatagramLength);
		sendSocket.send(outputDatagram);
	}
	
	private static boolean parseACK(byte[] ack, short currentBlock) throws WrongOPException       // TODO, could be an ERROR (op 5) sent instead of ACK
	{
		boolean recievedCorrectACK = false;
		
		ByteBuffer byteBuffer = ByteBuffer.allocate(2);
		byteBuffer.order(ByteOrder.BIG_ENDIAN);
		byteBuffer.put(ack, 0, 2);
		byteBuffer.flip();
		short opcode = byteBuffer.getShort();
		System.out.println("Ack OP code: " + opcode);
		
		if(opcode != OP_ACK)
		{
			throw new WrongOPException("OP code was not the expected", ack);
		}
		
		byteBuffer.clear();
		byteBuffer.order(ByteOrder.BIG_ENDIAN);
		byteBuffer.put(ack, 2, 2);
		byteBuffer.flip();
		short blockNumber = byteBuffer.getShort();
		
		System.out.println("Received block: " + blockNumber);
		System.out.println("Expected block: " + currentBlock);
		if(blockNumber == currentBlock)
		{
			return true;
		}
		else
		{
			return false;
		}
	}
	
}


