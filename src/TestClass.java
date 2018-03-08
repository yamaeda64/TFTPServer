import static org.junit.Assert.*;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Assert;
import org.junit.Test;

import exceptions.WrongOPException;
import sun.applet.Main;


public class TestClass {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = extra.SourceFolder.getReadFolder(); //custom address at your PC
	public static final String WRITEDIR = extra.SourceFolder.getWriteFolder(); //custom address at your PC

	@Test
	public void TestError4() throws IOException {
		System.out.println("Testing Error 4  - Illegal TFTP Operation");
		TFTPServer test = new TFTPServer();

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
			final InetSocketAddress clientAddress = test.receiveFrom(socket, buf);

			if (clientAddress == null)
				continue;

			final StringBuffer requestedFile = new StringBuffer();
			int reqtype;
			buf[1] = 8; // Changes OP Code to 8 (invalid op code)
			
			try	{
				reqtype = test.ParseRQ(buf, requestedFile);
				fail("Should have thrown exception");
				break;
			} catch(WrongOPException e) {
				assertEquals("Unexpected OP", e.getMessage());
				break;
			}
		}
	}
}
