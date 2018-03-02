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
    
    public static TransferMode transferMode;    // Allergic against static (non final) variables, but since one class program I think it's ok
    
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
            TFTPServer server= new TFTPServer();
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
        byte[] buf= new byte[BUFSIZE];
        
        // Create socket
        DatagramSocket socket= new DatagramSocket(null);
        
        // Create local bind point
        SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
        socket.bind(localBindPoint);
        
        System.out.printf("Listening at port %d for new requests\n", TFTPPORT);
        
        // Loop to handle client requests
        while (true)
        {
            
            final InetSocketAddress clientAddress = receiveFrom(socket, buf);
            
            // If clientAddress is null, an error occurred in receiveFrom()
            if (clientAddress == null)
                continue;
            
            final StringBuffer requestedFile = new StringBuffer();
            
            //  final TransferMode transferMode = TransferMode.ILLEGAL; // initally ILLEGAL, and changed if parsed correctly
            final int reqtype = ParseRQ(buf, requestedFile);
            System.out.println("outside: " + requestedFile);
            System.out.println("outside: " + transferMode);
            
            new Thread()
            {
                public void run()
                {
                    try
                    {
                        DatagramSocket sendSocket= new DatagramSocket(0);  // Port 0 makes the port random which is required by TFTP
                        
                        // Connect to client
                        sendSocket.connect(clientAddress);
                        
                        System.out.printf("%s request for %s from %s using port %d\n",
                                (reqtype == OP_RRQ)?"Read":"Write",
                                clientAddress.getHostName(), sendSocket.getLocalAddress().getHostAddress(), sendSocket.getPort());
                        
                        // Read request
                        if (reqtype == OP_RRQ)
                        {
                            requestedFile.insert(0, READDIR);
                            HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
                        }
                        // Write request
                        else
                        {
                            requestedFile.insert(0, WRITEDIR);
                            HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);
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
    
    /**
     * Reads the first block of data, i.e., the request for an action (read or write).
     * @param socket (socket to read from)
     * @param buf (where to store the read data)
     * @return socketAddress (the socket address of the client)
     */
    private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws IOException
    {
        // Create datagram packet
        DatagramPacket datagramPacket = new DatagramPacket(buf,buf.length);
        
        // Receive packet
        socket.receive(datagramPacket);
        // Get client address and port from the packet
        InetSocketAddress socketAddress = new InetSocketAddress(datagramPacket.getAddress(),datagramPacket.getPort());
        
        return socketAddress;
    }
    
    /**
     * Parses the request in buf to retrieve the type of request and requestedFile
     *
     * @param buf (received request)
     * @param requestedFile (name of file to read/write)
     * @return opcode (request type: RRQ or WRQ)
     */
    private int ParseRQ(byte[] buf, StringBuffer requestedFile)
    {
        
        /* Parse the OpCode */
        ByteBuffer byteBuffer = ByteBuffer.allocate(2);
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        byteBuffer.put(buf, 0, 2);
        byteBuffer.flip();
        short opcode = byteBuffer.getShort();
        
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
        try
        {
            transferMode = TransferMode.valueOf(sb.toString().toUpperCase());
        } catch(IllegalArgumentException e)
        {
            transferMode = TransferMode.ILLEGAL;
        }
        
        
        
        return opcode;
    }
    
    /**
     * Handles RRQ and WRQ requests
     *
     * @param sendSocket (socket used to send/receive packets)
     * @param requestedFile (name of file to read/write)
     * @param opcode (RRQ or WRQ)
     */
    private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) throws IOException
    {
        
        if(opcode == OP_RRQ)
        {
            // See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
            try
            {
                boolean result = send_DATA_receive_ACK(requestedFile, sendSocket);
            }
            catch(FileNotFoundException e)
            {
                send_ERR(1, sendSocket);
            }
        }
        else if (opcode == OP_WRQ)
        {
            System.out.println("Op == WRQ");
            try
            {
                boolean result = receive_DATA_send_ACK(requestedFile, sendSocket);
            }
            catch(FileAlreadyExistsException e)
            {
                send_ERR(6, sendSocket);
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
    private boolean send_DATA_receive_ACK(String requestedFile, DatagramSocket sendSocket) throws IOException
    {
        
        File outputfile = new File(requestedFile);
        if(!outputfile.exists())
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
            sendSocket.send(outputPacket);
            System.out.println("packet sent, size: " + outputPacket.getLength());
        
        /* Recieve ACK */
            
            DatagramPacket ack = new DatagramPacket(buffer, buffer.length);
            sendSocket.receive(ack);
            
            System.out.println("recieved DatagramPacket");
        /* Parse ACK */
            
            try
            {
                boolean correctACK = parseACK(buffer, blockNumber);
            } catch(WrongOPException e)
            {
                // TODO, handle what op is incoming, probably an ERROR
            }
            
            
        }
        return true;
    }
    
    private boolean receive_DATA_send_ACK(String requestedFile, DatagramSocket sendSocket) throws IOException
    {
        File outputFile = new File(requestedFile);
        if(outputFile.exists())
        {
            throw new FileAlreadyExistsException("File already excists");
        }
        short blockNumber = 0;
        
        byte[] buffer = new byte[BUFSIZE];
        
        sendAck(sendSocket, blockNumber);
        
        /* Start the recieve packet and return ACK loop */
        boolean hasMoreData = true;
        while(hasMoreData)
        {
            blockNumber++;
            
            /* Recieve the packet */
            DatagramPacket data = new DatagramPacket(buffer, buffer.length);
            sendSocket.receive(data);
            
            System.out.println("recieved data: ");
            for(int i = 0; i< data.getLength(); i++)
            {
                System.out.print(buffer[i]);
            }
            
            
            boolean recievedData = parseAndWriteData(buffer, blockNumber, requestedFile, data.getLength());
            if(recievedData)
            {
                sendAck(sendSocket,blockNumber);
            }
            
            if(data.getLength()<512)
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
        for(int i = 0; i<4; i++)
        {
            System.out.println(ackBuffer[i]);
        }
        DatagramPacket outputDatagram = new DatagramPacket(ackBuffer, 4);
        sendSocket.send(outputDatagram);
    }
    
    
    private void send_ERR(int errID, DatagramSocket sendSocket) throws IOException
    {
        int errorDatagramLength;
        byte[] errorBuffer = new byte[BUFSIZE];
        /* Send initial ACK that WRQ is recieved */
        ByteBuffer wrap = ByteBuffer.wrap(errorBuffer);
        /* The Op to the first 2 bytes of buffer */
        wrap.putShort((short) OP_ERR);
        /* The blocket number to byte 3-4 of buffer */
        wrap.putShort(2, (short)errID);
        String errorMSG = "";
        switch(errID)
        {
            case 0:
                errorMSG = "Not defined";     // TODO, guess we should come up with a extra error check?
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
            throw new WrongOPException(""+opcode);
        }
        
        byteBuffer.clear();
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        byteBuffer.put(ack, 2, 2);
        byteBuffer.flip();
        short blockNumber = byteBuffer.getShort();
        
        System.out.println("BlockNumber: " + blockNumber);
        if(blockNumber == currentBlock)
        {
            return true;
        }
        else
        {
            return false;
        }
    }
    
    private boolean parseAndWriteData(byte[] data, short blockNumber, String requestedFile, int datagramLength) throws IOException
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
}



