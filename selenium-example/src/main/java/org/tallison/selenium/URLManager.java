package org.tallison.selenium;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

public class URLManager implements Closeable {


    enum STATUS {
        DONT_FETCH,
        TO_FETCH,
        STARTED_TO_FETCH,
        FETCH_ERR,
        FETCH_GT_MAX_HEADER,
        FETCH_SUCCESS
    }

    private final Connection connection;
    private final PreparedStatement insert;
    private final PreparedStatement update;
    private final PreparedStatement getNext;
    private final URLFilter filter;


    public static URLManager open(Path p, URLFilter filter, boolean freshStart) throws SQLException, ClassNotFoundException {
        Class.forName ("org.h2.Driver");
        Connection conn = DriverManager.getConnection ("jdbc:h2:file:"+p.toAbsolutePath());

        initTables(conn, freshStart);

        PreparedStatement update = conn.prepareStatement("merge into urls key (URL) values (?,?,?,?)");
        PreparedStatement getNext = conn.prepareStatement("select url from urls where status="
                +STATUS.TO_FETCH.ordinal() + " limit 1");
        PreparedStatement insert = conn.prepareStatement("insert into urls (URL, STATUS, UPDATED) values (?,?,?)");

        return new URLManager(conn, insert, update, getNext, filter);
    }

    private static void initTables(Connection conn, boolean freshStart) throws SQLException {
        if (freshStart) {
            try (Statement st = conn.createStatement()) {
                st.execute("drop table if exists urls");
            }
        }

        String sql = "create table if not exists urls ("+
                "URL varchar(1024) primary key,"+
                "STATUS integer,"+
                "DIGEST varchar(128),"+
                "UPDATED timestamp)";
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private URLManager(Connection connection, PreparedStatement insert,
                       PreparedStatement update, PreparedStatement getNext,
                       URLFilter filter) {

        this.connection = connection;
        this.insert = insert;
        this.update = update;
        this.getNext = getNext;
        this.filter = filter;
    }

    public synchronized void insert(String urlString) throws SQLException {
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            //log
            return;
        }
        STATUS status = STATUS.DONT_FETCH;
        if (filter.accept(url)) {
            status = STATUS.TO_FETCH;
        }
        insert.clearParameters();
        insert.setString(1, urlString);
        insert.setInt(2, status.ordinal());
        insert.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
        try {
            insert.execute();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("primary key")) {
                //no problem primary key violation...fine, skip
            } else {
                throw e;
            }
        }
    }

    //need to synchronize this when going multithreaded, or give each
    //thread its own prepared statement...yuck
    public void lazyUpdate(String url, STATUS status) throws SQLException {
        _update(url, status, null);
    }
    public void lazyUpdate(String url, STATUS status, String digest) throws SQLException {
        System.out.println("fetched: "+url + " to "+digest);
        _update(url, status, digest);
    }
    //unsynchronized update
    private void _update(String url, STATUS status, String digest) throws SQLException {
        update.clearParameters();
        update.setString(1, url);
        update.setInt(2, status.ordinal());
        if (digest == null) {
            update.setNull(3, Types.VARCHAR);
        } else {
            update.setString(3, digest);
        }
        update.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
        update.execute();
    }

    public synchronized String next() throws SQLException {
        String url = null;
        try (ResultSet rs = getNext.executeQuery()) {
            if (rs.next()) {
                url = rs.getString(1);
            }
        }
        if (url == null) {
            return url;
        }
        _update(url, STATUS.STARTED_TO_FETCH, null);
        return url;
    }

    public void resetAllStatuses() throws SQLException {
        connection.createStatement().execute("update urls set status="+STATUS.TO_FETCH.ordinal());
        connection.createStatement().execute("update urls set digest = null");
        connection.createStatement().execute("update urls set updated = null");
    }

    @Override
    public void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
