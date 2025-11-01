package databases;

import java.util.Map;

public class MySQL extends Database{

    private static final Map<String, String> typeMaps = Map.of(
            "int", "INT",
            "long", "BIGINT",
            "double", "DOUBLE",
            "float", "FLOAT",
            "boolean", "TINYINT(1)",
            "String", "VARCHAR(500)"
    );

    @Override
    public String getUrl() {
        return "jdbc:mysql://localhost:3306/";
    }

    @Override
    public String getSqlType(String javaType) {
        return typeMaps.getOrDefault(javaType, "TEXT");
    }
}
