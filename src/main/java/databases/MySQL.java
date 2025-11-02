package databases;

import java.util.Map;

public class MySQL extends Database {

    private static final Map<String, String> typeMaps = Map.ofEntries(
            Map.entry("int", "INT"),
            Map.entry("Integer", "INT"),
            Map.entry("long", "BIGINT"),
            Map.entry("Long", "BIGINT"),
            Map.entry("double", "DOUBLE"),
            Map.entry("Double", "DOUBLE"),
            Map.entry("float", "FLOAT"),
            Map.entry("Float", "FLOAT"),
            Map.entry("boolean", "TINYINT(1)"),
            Map.entry("Boolean", "TINYINT(1)"),
            Map.entry("String", "VARCHAR(500)")
    );

    @Override
    public String getUrl() { return "jdbc:mysql://localhost:3306/"; }

    @Override
    public String getSqlType(String javaType) {
        return typeMaps.getOrDefault(javaType, "TEXT");
    }
}
