package nadry.indexer;


public class DocumentProcessingException  extends Exception {
    private static final long serialVersionUID = 1L;

	public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}