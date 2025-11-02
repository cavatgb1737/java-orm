package databases;

import java.util.Map;

public class PostgreSQL extends Database {

    private static final Map<String, String> typeMaps = Map.ofEntries(
            Map.entry("int", "INT"),
            Map.entry("Integer", "INT"),
            Map.entry("long", "BIGINT"),
            Map.entry("Long", "BIGINT"),
            Map.entry("double", "DOUBLE PRECISION"),
            Map.entry("Double", "DOUBLE PRECISION"),
            Map.entry("float", "REAL"),
            Map.entry("Float", "REAL"),
            Map.entry("boolean", "BOOLEAN"),
            Map.entry("Boolean", "BOOLEAN"),
            Map.entry("String", "VARCHAR(500)")
    );


    @Override
    public String getUrl() { return "jdbc:postgresql://localhost:5432/"; }

    @Override
    public String getSqlType(String javaType) {
        return typeMaps.getOrDefault(javaType, "TEXT");
    }
}

