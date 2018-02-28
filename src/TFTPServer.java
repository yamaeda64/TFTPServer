import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
            
            final TransferMode transferMode = TransferMode.ILLEGAL;
            final int reqtype = ParseRQ(buf, requestedFile,transferMode);
            System.out.println("outside: " + requestedFile);
            
            new Thread()
            {
                public void run()
                {
                    try
                    {
                        DatagramSocket sendSocket= new DatagramSocket(0);
                        
                        // Connect to client
                        sendSocket.connect(clientAddress);
                        
                        System.out.printf("%s request for %s from %s using port %d\n",
                                (reqtype == OP_RRQ)?"Read":"Write",
                                clientAddress.getHostName(), socket.getLocalAddress().getHostAddress(), clientAddress.getPort());
                        
                        // Read request
                        if (reqtype == OP_RRQ)
                        {
                            requestedFile.insert(0, READDIR);
                            HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ, socket);
                        }
                        // Write request
                        else
                        {
                            requestedFile.insert(0, WRITEDIR);
                            HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ, socket);
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
    private int ParseRQ(byte[] buf, StringBuffer requestedFile, TransferMode transferMode)
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
        while(loop)             // TODO, not tested
        {
            index++;
            if(buf[index] == 0)
            {
                loop = false;
            }
        }
        StringBuffer sb = new StringBuffer(new String(buf, modeStartIndex, index - modeStartIndex));
        System.out.println("Parsed Mode: " + sb.toString());  // TODO, debug
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
    private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode, DatagramSocket recieveSocket) throws IOException
    {
        
        if(opcode == OP_RRQ)
        {
            // See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
            boolean result = send_DATA_receive_ACK(requestedFile, sendSocket, recieveSocket);
        }
        else if (opcode == OP_WRQ)
        {
            boolean result = receive_DATA_send_ACK();
        }
        else
        {
            System.err.println("Invalid request. Sending an error packet.");
            // See "TFTP Formats" in TFTP specification for the ERROR packet contents
            send_ERR();
            return;
        }
    }
    
    /**
     To be implemented
     */
    private boolean send_DATA_receive_ACK(String requestedFile, DatagramSocket sendSocket, DatagramSocket recieveSocket) throws IOException
    {
     
        File outputfile = new File(requestedFile);
        long remainingFileBytes = outputfile.length();
        short blockNumber = 1;
        FileInputStream inputStream = new FileInputStream(requestedFile);
        
        System.out.println(requestedFile);  // TODO debug
        DatagramPacket outputPacket;
        
        byte[] buffer = new byte[BUFSIZE];
       
        
       /* Opcode: 2 bytes */
        ByteBuffer wrap = ByteBuffer.wrap(buffer);
        wrap.putShort((short)OP_DAT);
    
        /* BlockNumber: 2 bytes */
        wrap.putShort(2,blockNumber);
        
        /* Data: 0 - 512 bytes */
        if(remainingFileBytes < 512)                   // TODO, trigger that datagram is final(internally)
        {
            inputStream.read(buffer, 4, (int) remainingFileBytes);
            
            outputPacket = new DatagramPacket(buffer, (int)(remainingFileBytes+4));
            remainingFileBytes = 0;
        }
        else
        {
            inputStream.read(buffer,4,512);
            outputPacket = new DatagramPacket(buffer, 516);
            remainingFileBytes -= 512;
        }
        
        /* Send the datagram */
        sendSocket.send(outputPacket);
        System.out.println("packet sent, size: " + outputPacket.getLength());
        
        /* Recieve ACK */
        
        DatagramPacket ack = new DatagramPacket(buffer,buffer.length);
        sendSocket.receive(ack);
        
        System.out.println("recieved DatagramPacket");
        /* Parse ACK */
        
        boolean correctACK = parseACK(buffer,blockNumber);
        
        System.out.println("correctACK: " + correctACK);
        return true;}
    
    private boolean receive_DATA_send_ACK()
    {return true;}
    
    private void send_ERR()
    {}
    
    /**
     * Parses an ACK and returns true if the ACK is valid
     * @param ack the recieved ACK as a byte array
     * @param currentBlock the number of last sent block number
     * @return true if vaild ACK otherwise false
     */
    private boolean parseACK(byte[] ack, short currentBlock)        // TODO, could be an ERROR (op 5) sent instead of ACK
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
            return false;
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
}



