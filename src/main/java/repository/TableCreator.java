package repository;

import annotations.Column;
import annotations.Id;
import annotations.Required;
import annotations.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TableCreator {

    private static final Logger log = LoggerFactory.getLogger(TableCreator.class);

    public static void createTable(Class<?> clazz, SQLConnection sqlConnection){

        String tableName = clazz.getAnnotation(Table.class).name();

        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(tableName)
                .append(" (");

        for (Field field : clazz.getDeclaredFields()) {
            Class<?> typeClass = field.getType();
            String typeName = sqlConnection.getDatabase().getSqlType(typeClass.getSimpleName());
            if (typeName == null) throw new RuntimeException("Unsupported type: " + typeClass.getSimpleName());

            String varName = field.getName();
            if(field.isAnnotationPresent(Column.class)){
                Column col = field.getAnnotation(Column.class);
                if (!col.name().isEmpty()) varName = col.name();
            }

            sql.append(varName).append(" ").append(typeName);

            if (field.isAnnotationPresent(Id.class)) {
                sql.append(" PRIMARY KEY");
            } else if (field.isAnnotationPresent(Column.class)) {
                Column col = field.getAnnotation(Column.class);
                if (!col.nullable() || field.isAnnotationPresent(Required.class)) {
                    sql.append(" NOT NULL");
                }
                if (col.unique()) {
                    sql.append(" UNIQUE");
                }
            }

            sql.append(", ");
        }

        sql.setLength(sql.length() - 2);
        sql.append(");");

        Connection conn = sqlConnection.getConn();

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.executeUpdate();
            if(sqlConnection.getLogsEnabled()){
                log.info("Created table with sql: {}", sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


    }

}
