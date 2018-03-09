package exceptions;

/**
 * This exception should be thrown if OP is not the expected one and should be handled differently
 */
public class WrongOPException extends Exception
{
	private byte[] datagramPacket = null;
	
    public WrongOPException(String msg)
    {
        super(msg);
    }
    
    public WrongOPException(String msg, byte[] datagramPacket) {
    	super(msg);
    	this.datagramPacket = datagramPacket;
    }
    
    public byte[] getData() {
    	return datagramPacket;
    }
}
