package org.tallison.crawler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.log4j.Logger;

/**
 * Created by TALLISON on 8/5/2016.
 */
public class DBReporter implements Reporter {
    final PreparedStatement update;
    final PreparedStatement insert;
    final Connection connection;
    final String tableName = "crawl_status";

    static Logger logger = Logger.getLogger(DBReporter.class);

    public DBReporter(Connection connection, boolean freshStart) throws SQLException {
        this.connection = connection;
        if (freshStart) {
            String sql = "drop table if exists " + tableName;
            connection.createStatement().execute(sql);
            sql = "create table " + tableName +
                    " (FETCH_KEY VARCHAR(32), URL VARCHAR(5000) PRIMARY KEY, STATUS INTEGER, " +
                    "HTTP_RESPONSE_CODE INTEGER, " +
                    "HEADER VARCHAR(" + Constants.MAX_HEADER_STRING_LENGTH + "), " +
                    "DIGEST VARCHAR(64));";
            connection.createStatement().execute(sql);
        }

        update = connection.prepareStatement("update "+tableName + " set FETCH_KEY=?, STATUS = ?, " +
                "HTTP_RESPONSE_CODE=?, HEADER=?, DIGEST=? "+
                " where URL=?");
        insert = connection.prepareStatement("insert into "+tableName + " (FETCH_KEY, URL, STATUS) values (?,?,?)");
    }

    @Override
    public boolean setStatus(String key, String url, FETCH_STATUS status) {
        try {
            insert.clearParameters();
            setString(insert, 1, key);
            setString(insert, 2, url);
            setInt(insert, 3, status.ordinal());
            insert.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("primary key")) {
                setResponse(key, url, status, -1, null, null);
                return true;
            } else {
                logger.warn(e);
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    @Override
    public void setResponse(String key, String url, FETCH_STATUS status, int httpCode) {
        setResponse(key, url, status, httpCode, null, null);

    }

    @Override
    public void setResponse(String key, String url, FETCH_STATUS status, int httpCode, String header, String digest) {
        try {
            update.clearParameters();
            setString(update, 1, key);
            setString(update, 6, url);
            setInt(update, 2, status.ordinal());
            setInt(update, 3, httpCode);
            setString(update, 4, header);
            setString(update, 5, digest);
            update.executeUpdate();
        } catch (SQLException e) {
            logger.warn(e);
            e.printStackTrace();
        }
    }

    private void setInt(PreparedStatement update, int i, int val) {
        try {
            update.setInt(i, val);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setString(PreparedStatement update, int i, String val) throws SQLException {
        if (val == null) {
            update.setNull(i, Types.VARCHAR);
        } else {
            update.setString(i, val);
        }
    }
}
