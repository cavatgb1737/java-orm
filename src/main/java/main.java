import databases.DatabaseType;

public class main{

    public static void main(String[] args){


        SQLConnection conn = new SQLConnection("postgres", "12345", "db", DatabaseType.POSTGRESQL);
        conn.enableLogging();
        

    }


}