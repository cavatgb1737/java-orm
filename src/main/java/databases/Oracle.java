package databases;

import java.util.Map;

public class Oracle extends Database {

    private static final Map<String, String> typeMaps = Map.ofEntries(
            Map.entry("int", "NUMBER(10)"),
            Map.entry("Integer", "NUMBER(10)"),
            Map.entry("long", "NUMBER(19)"),
            Map.entry("Long", "NUMBER(19)"),
            Map.entry("double", "BINARY_DOUBLE"),
            Map.entry("Double", "BINARY_DOUBLE"),
            Map.entry("float", "BINARY_FLOAT"),
            Map.entry("Float", "BINARY_FLOAT"),
            Map.entry("boolean", "NUMBER(1)"),
            Map.entry("Boolean", "NUMBER(1)"),
            Map.entry("String", "VARCHAR2(500)")
    );

    @Override
    public String getUrl() { return "jdbc:oracle:thin:@localhost:1521:XE"; }

    @Override
    public String getSqlType(String javaType) {
        return typeMaps.getOrDefault(javaType, "CLOB");
    }
}
