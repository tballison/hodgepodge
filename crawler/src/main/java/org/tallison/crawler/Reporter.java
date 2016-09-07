package org.tallison.crawler;

/**
 * Created by TALLISON on 8/5/2016.
 */
public interface Reporter {
    enum FETCH_STATUS {
        ABOUT_TO_FETCH,          //0
        BAD_URL, //1
        RETRIEVED_HEAD,//2
        HEADER_NOT_SELECTED,//3
        HEADER_SELECTED, //4
        FETCHED_IO_EXCEPTION,//5
        FETCHED_NOT_200,//6
        FETCHED_IO_EXCEPTION_READING_ENTITY,//7
        FETCHED_NO_CONTENT_FOUND,//8
        FETCHED_SUCCESSFULLY,//9
        FETCHED_IO_EXCEPTION_SHA1,//10
        ADDED_TO_REPOSITORY, //11
    }

    /**
     *
     * @param key key for this uri
     * @param url url
     * @param status status to set
     * @return whether or not the status was successfully set.  If there was a primary key violation
     * or other problem, this will return false.
     */
    public boolean setStatus(String key, String url, FETCH_STATUS status);
    public void setResponse(String key, String url, FETCH_STATUS status, int httpCode);
    public void setResponse(String key, String url, FETCH_STATUS status, int httpCode, String header, String digest);
}
