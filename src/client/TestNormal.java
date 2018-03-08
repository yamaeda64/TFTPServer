package client;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TestNormal {
	private static final String SERVER_IP = "127.0.0.1";
	private static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String FILENAME = "test.txt";

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
		get(FILENAME);
	}
	private static void get(String fileName) throws Exception {
		inetAddress = InetAddress.getByName(SERVER_IP);
		datagramSocket = new DatagramSocket();
		requestArray = createRequest(OP_RRQ, fileName, "octet");
		requestPacket = new DatagramPacket(requestArray,
				requestArray.length, inetAddress, TFTPPORT);

		datagramSocket.send(requestPacket);
		receive_DATA_send_ACK("test.txt", datagramSocket);
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

	private static void sendAck(DatagramSocket sendSocket, short blockNumber) throws IOException
	{
		byte[] ackBuffer = new byte[4];
		/* Send initial ACK that WRQ is recieved */
		ByteBuffer wrap = ByteBuffer.wrap(ackBuffer);

		/* The Op to the first 2 bytes of buffer */
		wrap.putShort((short) OP_ACK);
		/* The blocket number to byte 3-4 of buffer */
		wrap.putShort(2, blockNumber);

		System.out.println("\nSending ack");
		System.out.println("OP: " + ackBuffer[1]);

		DatagramPacket outputDatagram = new DatagramPacket(ackBuffer, 4, inetAddress, inPacket.getPort());
		sendSocket.send(outputDatagram);
	}

	private static boolean receive_DATA_send_ACK(String requestedFile, DatagramSocket sendSocket) throws Exception
	{
		byte[] buffer = new byte[BUFSIZE];
		short blockNumber = 0;

		/* Start the recieve packet and return ACK loop */
		boolean hasMoreData = true;
		while(hasMoreData)
		{
			blockNumber++;

			/* Recieve the packet */
			inPacket = new DatagramPacket(buffer, buffer.length, inetAddress,
					sendSocket.getLocalPort());
			sendSocket.receive(inPacket);

			System.out.println("\nReceived data: ");
			for(int i = 0; i< inPacket.getLength(); i++)
			{
				System.out.print(buffer[i]);
			}
			System.out.println();

			boolean recievedData = parseAndWriteData(buffer, blockNumber, requestedFile, inPacket.getLength());
			if(recievedData)
			{
				sendAck(sendSocket, blockNumber);
			}

			if(inPacket.getLength()<512)
			{
				hasMoreData = false;
			}
			else
			{
				hasMoreData = true;
			}
		}

		return true;
	}

	private static boolean parseAndWriteData(byte[] data, short blockNumber, String requestedFile, int datagramLength) throws IOException
	{
		ByteBuffer byteBuffer = ByteBuffer.allocate(2);
		byteBuffer.order(ByteOrder.BIG_ENDIAN);
		byteBuffer.put(data, 0, 2);
		byteBuffer.flip();
		short opcode = byteBuffer.getShort();
		System.out.println("Data OP code: " + opcode);

		if(opcode != OP_DAT)
		{
			return false;
		}
		byteBuffer.clear();
		byteBuffer.order(ByteOrder.BIG_ENDIAN);
		byteBuffer.put(data, 2, 2);
		byteBuffer.flip();
		short dataBlockNumber = byteBuffer.getShort();

		System.out.println("BlockNumber: " + blockNumber);
		if(blockNumber != dataBlockNumber)
		{
			return false;
		}
		else
		{
			File outputFile = new File(requestedFile);
			FileOutputStream outputStream = new FileOutputStream(outputFile,true);
			outputStream.write(data,4,datagramLength-4);
			outputStream.close();
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
}


