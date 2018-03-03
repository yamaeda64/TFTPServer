package exceptions;

/**
 * This exception should be thrown when server notice that client tries to reach or write to a folder outside source folder
 */
public class OutsideSourceFolderException extends Exception
{
    public OutsideSourceFolderException(String msg)
    {
        super(msg);
    }
}
