package databases;

public enum DatabaseType {
    POSTGRESQL,
    ORACLE,
    MYSQL;

    public Database createDialect() {
        return switch(this) {
            case POSTGRESQL -> new PostgreSQL();
            case MYSQL -> new MySQL();
            case ORACLE -> new Oracle();
        };
    }
}
