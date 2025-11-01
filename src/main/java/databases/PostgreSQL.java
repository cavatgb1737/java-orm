package databases;

import java.util.Map;

public class PostgreSQL extends Database {

    private static final Map<String, String> typeMaps = Map.of(
            "int", "INT",
            "long", "BIGINT",
            "double", "DOUBLE PRECISION",
            "float", "REAL",
            "boolean", "BOOLEAN",
            "String", "VARCHAR(500)"
    );

    @Override
    public String getUrl() { return "jdbc:postgresql://localhost:5432/"; }

    @Override
    public String getSqlType(String javaType) {
        return typeMaps.getOrDefault(javaType, "TEXT");
    }
}

