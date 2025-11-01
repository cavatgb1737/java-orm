package databases;

import java.util.Map;

public class Oracle extends Database{

    private static final Map<String, String> typeMaps = Map.of(
            "int", "NUMBER(10)",
            "long", "NUMBER(19)",
            "double", "BINARY_DOUBLE",
            "float", "BINARY_FLOAT",
            "boolean", "NUMBER(1)",
            "String", "VARCHAR2(500)"
    );


    @Override
    public String getUrl() {
        return "jdbc:oracle:thin:@localhost:1521:";
    }

    @Override
    public String getSqlType(String javaType) {
        return typeMaps.getOrDefault(javaType, "TEXT");
    }
}
