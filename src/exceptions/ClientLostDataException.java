package exceptions;

/**
 * This exception should be thrown when server notice that client has lost a packet.
 * The server should also catch the exception and resend the last data packet.
 */
public class ClientLostDataException extends Exception
{
    public ClientLostDataException(String msg)
    {
        super(msg);
    }
}
