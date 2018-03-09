import exceptions.OutsideSourceFolderException;
import exceptions.WrongOPException;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileAlreadyExistsException;

public class TFTPServer
{
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = extra.SourceFolder.getReadFolder(); //custom address at your PC
	public static final String WRITEDIR = extra.SourceFolder.getWriteFolder(); //custom address at your PC
	
	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;
	
	public static void main(String[] args) {
		if (args.length > 0)
		{
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		
		//Starting the server
		try
		{
			TFTPServer server = new TFTPServer();
			server.start();
		}
		catch (SocketException e)
		{e.printStackTrace();}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	private void start() throws SocketException, IOException
	{
		byte[] buf = new byte[BUFSIZE];
		
		// Create socket
		DatagramSocket socket = new DatagramSocket(null);
		
		// Create local bind point
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);
		
		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);
		
		// Loop to handle client requests
		while (true)
		{
			InetSocketAddress clientAddress;
			try
			{
				clientAddress = receiveFrom(socket, buf);
				
				
				// If clientAddress is null, an error occurred in receiveFrom()
				if(clientAddress == null)
					continue;
			}
			catch(ArrayIndexOutOfBoundsException e)
			{
				System.out.println("Initial request was too long");
				continue;
			}
			catch(Exception e)
			{
				System.out.println("something was wrong with receiving initial request");
				continue;
			}
			final StringBuffer requestedFile = new StringBuffer();
			
			//  final TransferMode transferMode = TransferMode.ILLEGAL; // initally ILLEGAL, and changed if parsed correctly
			int reqtype;
			
			try
			{
				reqtype = ParseRQ(buf, requestedFile);
			}
			catch(WrongOPException e)
			{
				socket.connect(clientAddress);
				System.out.println("Incoming starting packet was wrong OP");
				send_ERR(4,socket,"Initial request was neither RRQ or WRQ");
				socket.disconnect();
				continue;
				
			}
			catch(ArrayIndexOutOfBoundsException e)
			{
				socket.connect(clientAddress);
				send_ERR(4,socket,"Filename was too long.");
				socket.disconnect();
				continue;
			}
			catch(Exception e)
			{
				System.out.println("Unhandled error on server while reading initial request");
				socket.connect(clientAddress);
				send_ERR(0,socket);
				socket.disconnect();
				continue;
				
			}
			System.out.println("outside: " + requestedFile);
			
			new Thread()
			{
				public void run()
				{
					try
					{
						TransferMode transferMode = parseTransferMode(buf);
						
						DatagramSocket sendSocket = new DatagramSocket(0);  // Port 0 makes the port "random" which is required by TFTP
						
						System.out.println(clientAddress);
						// Connect to client
						sendSocket.connect(clientAddress);
						
						System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == OP_RRQ)?"Read":"Write",
								clientAddress.getHostName(), sendSocket.getLocalAddress().getHostAddress(), sendSocket.getPort());
						
						// Read request
						if (reqtype == OP_RRQ && transferMode == TransferMode.OCTET)
						{
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ, clientAddress);
						}
						// Write request
						else if(reqtype == OP_WRQ && transferMode == TransferMode.OCTET)
						{
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ, clientAddress);
						}
						else if(transferMode == TransferMode.ILLEGAL)
						{
							System.out.println("errorCode Illegal: ");
							send_ERR(4, sendSocket);
						}
						else if(transferMode == TransferMode.NETASCII)
						{
							
							send_ERR(0,sendSocket, "NETASCII mode is not implemented");
						}
						else if(transferMode == TransferMode.MAIL)
						{
							send_ERR(0,sendSocket, "MAIL mode is not implemented");
						}
						else
						{
							System.out.println("from else...");
							send_ERR(0, sendSocket, "Unhandled error");
						}
						sendSocket.close();
					}
					catch (SocketException e)
					{e.printStackTrace();}
					catch (IOException e)
					{e.printStackTrace();}
				}
			}.start();
		}
	}
	
	/* Returns the transfer mode from a WRQ or RRQ */
	private TransferMode parseTransferMode(byte[] buf)
	{
		/* Find the end of filename */
		boolean loop = true;
		int index = 1;
		while(buf[index++] != 0)
		{
			
		}
		
		int modeStartIndex = index;
		loop = true;
		while(loop)
		{
			index++;
			if(buf[index] == 0)
			{
				loop = false;
			}
		}
		StringBuffer sb = new StringBuffer(new String(buf, modeStartIndex, index - modeStartIndex));
		System.out.println("Parsed Mode: " + sb.toString());  // TODO, debug
		
		TransferMode transferMode;
		try
		{
			transferMode = TransferMode.valueOf(sb.toString().toUpperCase());
		} catch(IllegalArgumentException e)
		{
			transferMode = TransferMode.ILLEGAL;
		}
		
		return transferMode;
	}
	
	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws IOException
	{
		// Create datagram packet
		DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
		
		// Receive packet
		socket.receive(datagramPacket);
		// Get client address and port from the packet
		InetSocketAddress socketAddress = new InetSocketAddress(datagramPacket.getAddress(),datagramPacket.getPort());
		System.out.println("SocketAddress: " + socketAddress);
		
		
		return socketAddress;
	}
	
	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 *
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) throws WrongOPException
	{
		System.out.print("ParseRQ: ");
		for(int i = 0; i < buf.length; i++) {
			System.out.print(buf[i]);
		}
		System.out.println();
		System.out.println();
		
		/* Parse the OpCode */
		ByteBuffer byteBuffer = ByteBuffer.allocate(2);
		byteBuffer.order(ByteOrder.BIG_ENDIAN);
		byteBuffer.put(buf, 0, 2);
		byteBuffer.flip();
		short opcode = byteBuffer.getShort();
		
		if(opcode < 1 || opcode > 2)
		{
			throw new WrongOPException("Unexpected OP");
		}
		
		System.out.println("from parser -- OPCODE: " + opcode);  // TODO, debug line

		/* Parse the filename */
		boolean loop = true;            // TODO how to handle to long filenames  (read that default is max 128)
		int index = 1;
		while(loop)
		{
			index++;
			if(buf[index] == 0)
			{
				loop = false;
			}
		}
		requestedFile.append(new String(buf, 2, index - 2));
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ contents
		System.out.println("Filename = " + requestedFile);  // TODO, debug
		int modeStartIndex = index + 1;
		loop = true;
		while(loop)
		{
			index++;
			if(buf[index] == 0)
			{
				loop = false;
			}
		}
		StringBuffer sb = new StringBuffer(new String(buf, modeStartIndex, index - modeStartIndex));
		System.out.println("Parsed Mode: " + sb.toString());  // TODO, debug

		/*  TODO   NEED TO BE READ SOMEWHERE ELSE, or STATIC variable */
		/*
        try
        {
            transferMode = TransferMode.valueOf(sb.toString().toUpperCase());
        } catch(IllegalArgumentException e)
        {
            transferMode = TransferMode.ILLEGAL;
        }

		 */
		
		return opcode;
	}
	
	/**
	 * Handles RRQ and WRQ requests
	 *
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode, InetSocketAddress orgClientAddress) throws IOException
	{
		if(opcode == OP_RRQ)
		{
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			try
			{
				boolean result = send_DATA_receive_ACK(requestedFile, sendSocket, orgClientAddress);
			}
			catch(FileNotFoundException e)
			{
				send_ERR(1, sendSocket);
			}
			catch(OutsideSourceFolderException e)
			{
				send_ERR(2, sendSocket);
			}
			catch(WrongOPException e)
			{
				
			}
			catch(Exception e)
			{
				System.out.println("EXCEPTION: " + e.getMessage()); // TODO: Ta bort
				send_ERR(0, sendSocket);
			}
		}
		else if (opcode == OP_WRQ)
		{
			System.out.println("Op == WRQ");
			try
			{
				boolean result = receive_DATA_send_ACK(requestedFile, sendSocket, orgClientAddress);
			}
			catch(FileAlreadyExistsException e)
			{
				send_ERR(6, sendSocket);
			}
			catch(OutsideSourceFolderException e)
			{
				send_ERR(2,sendSocket);
			}
			catch(WrongOPException e)
			{
				
			}
			catch(Exception e)
			{
				send_ERR(0,sendSocket);
			}
		}
		else
		{
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(4, sendSocket);
			return;
		}
	}
	
	/**
	 To be implemented
	 */
	private boolean send_DATA_receive_ACK(String requestedFile, DatagramSocket sendSocket, InetSocketAddress orgClientAddress) throws OutsideSourceFolderException, IOException, WrongOPException
	{
		
		File outputfile = new File(requestedFile);
		System.out.println("free space: " + outputfile.getFreeSpace());
		System.out.println("usable space: " + outputfile.getUsableSpace());
		
		if(!outputfile.getCanonicalPath().startsWith(READDIR))
		{
			throw new OutsideSourceFolderException("The writing folder was outside the source folder");
		}
		else if(!outputfile.exists())
		{
			throw new FileNotFoundException("The file could not be found in source folder");
		}
		
		long remainingFileBytes = outputfile.length();
		short blockNumber = 0;
		FileInputStream inputStream = new FileInputStream(requestedFile);
		
		System.out.println(requestedFile);  // TODO debug
		DatagramPacket outputPacket;
		
		byte[] buffer = new byte[BUFSIZE];
		
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
			sendSocket.setSoTimeout(3000); // 3 seconds
			byte[] ACKBuffer = new byte[BUFSIZE];
			int packetsSent = 1;
			
			sendSocket.send(outputPacket);
			System.out.println("packet sent, size: " + outputPacket.getLength());
			System.out.println("Sent blockNumber: " + blockNumber);
			
			boolean correctACK = false;
			while(!correctACK) {
				System.out.println(1);
				DatagramPacket ack = new DatagramPacket(ACKBuffer, ACKBuffer.length);
				System.out.println(2);
				
				try {
					sendSocket.receive(ack);
					System.out.println("Received ACK");
					
					if(ack.getPort() != orgClientAddress.getPort())
					{
						System.out.println("Packet did not come from original sender");
						DatagramSocket tempSocket = new DatagramSocket(null);
						SocketAddress tempAddress = new InetSocketAddress(0);
						SocketAddress incorrectClient = ack.getSocketAddress();
						tempSocket.connect(incorrectClient);
						send_ERR(5, tempSocket);   // Send on new socket to not disturb the transmission from original client
					} else {
						correctACK = parseACK(ACKBuffer,blockNumber);
						// Resends packet if incorrect ACK
						if(!correctACK) {
							sendSocket.send(outputPacket);
							System.out.println("Incorrect ACK, sent packet again, size: " + outputPacket.getLength());
						}
					}
					// Resends packets if socket timed out (nothing received for x seconds)
				} catch (SocketTimeoutException e) {
					System.out.println("SocketTimeoutException thrown");
					sendSocket.send(outputPacket);
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
	
	private boolean receive_DATA_send_ACK(String requestedFile, DatagramSocket sendSocket, InetSocketAddress orgClientAddress) throws IOException, OutsideSourceFolderException, WrongOPException
	{
		File outputFile = new File(requestedFile);
		System.out.println("paretn size: " + outputFile.getParentFile().getFreeSpace());
		System.out.println("free space: " + outputFile.getFreeSpace());
		System.out.println("usable space: " + outputFile.getUsableSpace());
		System.out.println("TotalSpace" + outputFile.getTotalSpace());
		System.out.println("outputFile: " + outputFile.getCanonicalPath());
		
		if(!outputFile.getCanonicalPath().startsWith(WRITEDIR))
		{
			throw new OutsideSourceFolderException("The writing folder was outside the source folder");
		}
		
		else if(outputFile.exists())
		{
			throw new FileAlreadyExistsException("File already excists");
		}
		
		byte[] buffer = new byte[BUFSIZE];
		short blockNumber = 0;
		
		sendAck(sendSocket, blockNumber);

		/* Start the recieve packet and return ACK loop */
		boolean hasMoreData = true;
		System.out.println("StartGettingDATA");
		while(hasMoreData)
		{
			blockNumber++;
			System.out.println("Block#: " + blockNumber);
			int attemts = 0;
			boolean correctRecieve = false;
			sendSocket.setSoTimeout(3000);
			while(attemts++ < 5 && correctRecieve == false )
			{
				System.out.println("New attemt nr: " + attemts);
			/* Recieve the packet */
				DatagramPacket data = new DatagramPacket(buffer, buffer.length);
				try
				{
					sendSocket.receive(data);
					
					
					// Check if data comes from the intended original sender
					if(data.getPort() != orgClientAddress.getPort())
					{
						DatagramSocket tempSocket = new DatagramSocket(null);
						SocketAddress tempAddress = new InetSocketAddress(0);
						SocketAddress incorrectClient = data.getSocketAddress();
						tempSocket.connect(incorrectClient);
						send_ERR(5, tempSocket);   // Send on new socket to not disturb the transmission from original client
					}
					else
					{
						if(outputFile.getParentFile().getFreeSpace() - data.getLength() < 0)
						{
							send_ERR(3, sendSocket);
							if(outputFile.exists())
							{
								outputFile.delete();     // Remove the not completely sent file to avoid faulty files
							}
							return false;
						}
						
						System.out.println("recieved data: ");
						for(int i = 0; i < data.getLength(); i++)
						{
							System.out.print(buffer[i]);
						}
						
						boolean recievedData = parseAndWriteData(buffer, blockNumber, requestedFile, data.getLength());
						System.out.println("Data was recieved" + recievedData);
						
						if(recievedData)
						{
							sendAck(sendSocket, blockNumber);
							correctRecieve = true;
							System.out.println("correctRecieve");
							
							
							if(data.getLength() < 512)
							{
								hasMoreData = false;
								System.out.println("no more data");
							} else
							{
								hasMoreData = true;
								System.out.println("has more data");
							}
						}
						else
						{
							sendAck(sendSocket, (short)(blockNumber-1));
						}
					}
				}
				catch(SocketTimeoutException e)
				{
					continue;
				}
				
			}
			if(attemts >= 5)
			{
				return false;
			}
		}
		return true;
	}
	
	private void sendAck(DatagramSocket sendSocket, short blockNumber) throws IOException
	{
		byte[] ackBuffer = new byte[4];
		/* Send initial ACK that WRQ is recieved */
		ByteBuffer wrap = ByteBuffer.wrap(ackBuffer);

		/* The Op to the first 2 bytes of buffer */
		wrap.putShort((short) OP_ACK);
		/* The blocket number to byte 3-4 of buffer */
		wrap.putShort(2, blockNumber);
		
		System.out.println("OP: " + ackBuffer[1]);
		
		DatagramPacket outputDatagram = new DatagramPacket(ackBuffer, 4);
		System.out.println("created AckPacket");
		sendSocket.send(outputDatagram);
		System.out.println("Sent Datagram to socket");
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
	
	/* Overridden method to ad an own error message */
	private void send_ERR(int errID, DatagramSocket sendSocket, String errorMSG) throws IOException
	{
		System.out.println("Error " + errID + " sent");
		int errorDatagramLength;
		byte[] errorBuffer = new byte[BUFSIZE];
		
		ByteBuffer wrap = ByteBuffer.wrap(errorBuffer);
		/* The Op to the first 2 bytes of buffer */
		wrap.putShort((short) OP_ERR);
		wrap.putShort(2, (short)errID);
		
		System.out.println("errorBuffer:");
		System.out.println(errorBuffer[2] +" " + errorBuffer[3]);
		
		for(int i = 0; i<errorMSG.length(); i++)
		{
			errorBuffer[i+4] = (byte)errorMSG.charAt(i);
		}
		errorBuffer[4+errorMSG.length()] = 0;
		errorDatagramLength = errorMSG.length() +5;
		
		DatagramPacket outputDatagram = new DatagramPacket(errorBuffer, errorDatagramLength);
		sendSocket.send(outputDatagram);
	}
	
	/**
	 * Parses an ACK and returns true if the ACK is valid
	 * @param ack the recieved ACK as a byte array
	 * @param currentBlock the number of last sent block number
	 * @return true if vaild ACK otherwise false
	 */
	private boolean parseACK(byte[] ack, short currentBlock) throws WrongOPException       // TODO, could be an ERROR (op 5) sent instead of ACK
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
	
	private boolean parseAndWriteData(byte[] data, short blockNumber, String requestedFile, int datagramLength) throws IOException, WrongOPException
	{
		ByteBuffer byteBuffer = ByteBuffer.allocate(2);
		byteBuffer.order(ByteOrder.BIG_ENDIAN);
		byteBuffer.put(data, 0, 2);
		byteBuffer.flip();
		short opcode = byteBuffer.getShort();
		System.out.println("Data OP code: " + opcode);
		
		if(opcode != OP_DAT)
		{
			throw new WrongOPException("Wasn't the expected data OP");
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
			System.out.println("dataLenght" + (datagramLength-4));
			if(datagramLength-4 > 0)
			{
				outputStream.write(data, 4, datagramLength - 4);
			}
			outputStream.close();
			
		}
		
		return true;
	}
}



