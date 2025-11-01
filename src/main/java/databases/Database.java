package databases;

public abstract class Database {
    public abstract String getUrl();
    public abstract String getSqlType(String javaType);
}
