package exceptions;

/**
 * This exception should be thrown if OP is not the expected one and should be handled differently
 */
public class WrongOPException extends Exception
{
    public WrongOPException(String msg)
    {
        super(msg);
    }
}
