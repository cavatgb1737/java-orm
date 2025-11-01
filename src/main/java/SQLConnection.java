import databases.Database;
import databases.DatabaseType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLConnection {


    public SQLConnection(String username, String password, String dbName, DatabaseType dbType) {
        this.username = username;
        this.password = password;
        this.name = dbName;
        this.database = dbType.createDialect();

        String url = this.database.getUrl() + dbName;

        try {
            this.conn = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void enableLogging(){
        this.logsEnabled = true;
    }

    public void disableLogging(){
        this.logsEnabled = false;
    }


    private Connection conn;

    public Connection getConn() {
        return conn;
    }

    private String username;

    private boolean logsEnabled;

    private String password;

    private String name;

    private Database database;

    public Database getDatabase() {
        return database;
    }

    public boolean getLogsEnabled(){
        return logsEnabled;
    }




}
