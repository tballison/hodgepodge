public class SearchClientException extends RuntimeException {

    public SearchClientException(String msg) {
        super(msg);
    }

    public SearchClientException(Throwable t) {
        super(t);
    }

    public SearchClientException(String msg, Throwable t) {
        super(msg, t);
    }
}
