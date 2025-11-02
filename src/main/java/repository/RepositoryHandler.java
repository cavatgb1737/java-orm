package repository;

import annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import validation.ConstraintValidator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RepositoryHandler implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(RepositoryHandler.class);
    private final SQLConnection sqlConnection;
    private final Class<?> repositoryInterface;

    public RepositoryHandler(SQLConnection sqlConnection, Class<?> repositoryInterface) {
        this.sqlConnection = sqlConnection;
        this.repositoryInterface = repositoryInterface;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if (name.equals("save")) {
            return handleSave(args[0]);

        } else if (name.equals("findById")) {
            Class<?> entityClass = getEntityClass(proxy);
            return handleFindById(entityClass, args[0]);

        } else if (name.equals("delete")) {
            Class<?> entityClass = getEntityClass(proxy);
            return handleDeleteById(entityClass, args[0]);

        } else if (name.startsWith("findBy")) {
            Class<?> entityClass = getEntityClass(proxy);
            String paramName = name.substring(6);
            Class<?> returnType = method.getReturnType();

            if(List.class.isAssignableFrom(returnType)){
                return handleFindByCustomList(entityClass, args[0], paramName);
            }
            else{
                return handleFindByCustom(entityClass, args[0], paramName);
            }

        } else if (name.equals("existsById")) {
            Class<?> entityClass = getEntityClass(proxy);
            return handleExistsById(entityClass, args[0]);

        } else if (name.equals("existsBy")) {
            Class<?> entityClass = getEntityClass(proxy);
            String paramName = name.substring(8);
            return handleExistsByCustom(entityClass, args[0], paramName);

        } else if (method.isAnnotationPresent(CustomQuery.class)) {
            handleCustomQuery(method.getAnnotation(CustomQuery.class).query());

        } else if (name.equals("findAll")) {
            Class<?> entityClass = getEntityClass(proxy);
            return handleFindAll(entityClass);

        } else if (name.equals("saveAll")) {
            handleSaveAll((List<?>) args[0]);
        }

        return null;
    }

    private void handleCustomQuery(String query) {
        Connection conn = sqlConnection.getConn();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
            if (sqlConnection.getLogsEnabled()) log.info(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int handleSave(Object obj) {
        try {
            ConstraintValidator.validateConstraints(obj.getClass(), obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        Class<?> clazz = obj.getClass();
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                String colName = (field.isAnnotationPresent(Column.class) && !field.getAnnotation(Column.class).name().isEmpty())
                        ? field.getAnnotation(Column.class).name()
                        : field.getName();
                columns.add(colName);
                try {
                    values.add(field.get(obj));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append("?,".repeat(columns.size()));
        sql.setLength(sql.length() - 1);
        sql.append(")");
        try (PreparedStatement stmt = sqlConnection.getConn().prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) stmt.setObject(i + 1, values.get(i));
            int rows = stmt.executeUpdate();
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T handleFindById(Class<T> clazz, Object idValue) {
        checkAndThrow(clazz);

        String tableName = clazz.getAnnotation(Table.class).name();
        Field idField = null;
        List<Field> fields = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Id.class)) idField = field;
            if (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class)) fields.add(field);
        }

        if (idField == null) throw new RuntimeException("No @Id field found.");

        String idColumn = idField.isAnnotationPresent(Column.class) && !idField.getAnnotation(Column.class).name().isEmpty()
                ? idField.getAnnotation(Column.class).name()
                : idField.getName();

        String sql = "SELECT * FROM " + tableName + " WHERE " + idColumn + " = ?";

        try (PreparedStatement stmt = sqlConnection.getConn().prepareStatement(sql)) {

            Class<?> type = idField.getType();
            if (type == Integer.class || type == int.class) {
                stmt.setInt(1, (Integer) idValue);
            } else if (type == Long.class || type == long.class) {
                stmt.setLong(1, (Long) idValue);
            } else if (type == Double.class || type == double.class) {
                stmt.setDouble(1, (Double) idValue);
            } else if (type == Float.class || type == float.class) {
                stmt.setFloat(1, (Float) idValue);
            } else {
                stmt.setObject(1, idValue);
            }

            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql);

            if (rs.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Field field : fields) {
                    String colName = field.isAnnotationPresent(Column.class) && !field.getAnnotation(Column.class).name().isEmpty()
                            ? field.getAnnotation(Column.class).name()
                            : field.getName();
                    field.set(instance, rs.getObject(colName));
                }
                return instance;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public int handleDeleteById(Class<?> clazz, Object idValue) {
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        String idColumn = null;
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                idColumn = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
                break;
            }
        }
        String sql = "DELETE FROM " + tableName + " WHERE " + idColumn + " = ?";
        try (PreparedStatement stmt = sqlConnection.getConn().prepareStatement(sql)) {
            stmt.setObject(1, idValue);
            int rows = stmt.executeUpdate();
            if (sqlConnection.getLogsEnabled()) log.info(sql);
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> List<T> handleFindByCustomList(Class<T> clazz, Object param, String paramName) {

        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();

        paramName = Character.toLowerCase(paramName.charAt(0)) + paramName.substring(1);

        Field targetField = null;
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Column col = field.getAnnotation(Column.class);
            String colName = (col != null && !col.name().isEmpty()) ? col.name() : field.getName();
            if (colName.equalsIgnoreCase(paramName) || field.getName().equalsIgnoreCase(paramName)) {
                targetField = field;
                paramName = colName;
                break;
            }
        }
        if (targetField == null)
            throw new RuntimeException("No matching field found for: " + paramName);

        String sql = "SELECT * FROM " + tableName + " WHERE " + paramName + " = ?";

        try (PreparedStatement stmt = sqlConnection.getConn().prepareStatement(sql)) {

            Class<?> type = targetField.getType();
            if (type == Integer.class || type == int.class) {
                stmt.setInt(1, (Integer) param);
            } else if (type == Long.class || type == long.class) {
                stmt.setLong(1, (Long) param);
            } else if (type == Double.class || type == double.class) {
                stmt.setDouble(1, (Double) param);
            } else if (type == Float.class || type == float.class) {
                stmt.setFloat(1, (Float) param);
            } else {
                stmt.setObject(1, param);
            }

            List<T> results = new ArrayList<>();
            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql);

            while(rs.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    String colName = field.isAnnotationPresent(Column.class) && !field.getAnnotation(Column.class).name().isEmpty()
                            ? field.getAnnotation(Column.class).name()
                            : field.getName();
                    field.set(instance, rs.getObject(colName));
                }
                results.add(instance);
            }
            return results;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T handleFindByCustom(Class<T> clazz, Object param, String paramName) {
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();

        paramName = Character.toLowerCase(paramName.charAt(0)) + paramName.substring(1);

        Field targetField = null;
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Column col = field.getAnnotation(Column.class);
            String colName = (col != null && !col.name().isEmpty()) ? col.name() : field.getName();
            if (colName.equalsIgnoreCase(paramName) || field.getName().equalsIgnoreCase(paramName)) {
                targetField = field;
                paramName = colName;
                break;
            }
        }
        if (targetField == null)
            throw new RuntimeException("No matching field found for: " + paramName);

        String sql = "SELECT * FROM " + tableName + " WHERE " + paramName + " = ?";

        try (PreparedStatement stmt = sqlConnection.getConn().prepareStatement(sql)) {

            Class<?> type = targetField.getType();
            if (type == Integer.class || type == int.class) {
                stmt.setInt(1, (Integer) param);
            } else if (type == Long.class || type == long.class) {
                stmt.setLong(1, (Long) param);
            } else if (type == Double.class || type == double.class) {
                stmt.setDouble(1, (Double) param);
            } else if (type == Float.class || type == float.class) {
                stmt.setFloat(1, (Float) param);
            } else {
                stmt.setObject(1, param);
            }

            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql);

            if (rs.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    String colName = field.isAnnotationPresent(Column.class) && !field.getAnnotation(Column.class).name().isEmpty()
                            ? field.getAnnotation(Column.class).name()
                            : field.getName();
                    field.set(instance, rs.getObject(colName));
                }
                return instance;
            }
            return null;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public boolean handleExistsByCustom(Class<?> clazz, Object param, String paramName) {
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        paramName = Character.toLowerCase(paramName.charAt(0)) + paramName.substring(1);
        String sql = "SELECT EXISTS(SELECT 1 FROM " + tableName + " WHERE " + paramName + " = ?)";
        try (PreparedStatement stmt = sqlConnection.getConn().prepareStatement(sql)) {
            stmt.setObject(1, param);
            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql);
            if (rs.next()) return rs.getBoolean(1);
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean handleExistsById(Class<?> clazz, Object idValue) {
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        String idColumn = null;
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                idColumn = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
                break;
            }
        }
        String sql = "SELECT EXISTS(SELECT 1 FROM " + tableName + " WHERE " + idColumn + " = ?)";
        try (PreparedStatement stmt = sqlConnection.getConn().prepareStatement(sql)) {
            stmt.setObject(1, idValue);
            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql);
            if (rs.next()) return rs.getBoolean(1);
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> List<T> handleFindAll(Class<T> clazz) {
        checkAndThrow(clazz);
        List<T> list = new ArrayList<>();
        String tableName = clazz.getAnnotation(Table.class).name();
        String sql = "SELECT * FROM " + tableName;
        try (Statement stmt = sqlConnection.getConn().createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            Field[] fields = clazz.getDeclaredFields();
            while (rs.next()) {
                T obj = clazz.getDeclaredConstructor().newInstance();
                for (Field field : fields) {
                    String colName = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
                    if(colName.isEmpty()) colName = field.getName();
                    field.set(obj, rs.getObject(colName));
                }
                list.add(obj);
            }
            if (sqlConnection.getLogsEnabled()) log.info(sql);
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> void handleSaveAll(List<T> entities) {
        if (entities.isEmpty()) return;
        Connection conn = sqlConnection.getConn();
        try {
            conn.setAutoCommit(false);
            for (T entity : entities) handleSaveWithConnection(conn, entity);
            conn.commit();
        } catch (Exception e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException(e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    private int handleSaveWithConnection(Connection conn, Object obj) {
        try {
            ConstraintValidator.validateConstraints(obj.getClass(), obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        Class<?> clazz = obj.getClass();
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String colName = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
            if(colName.isEmpty()) colName = field.getName();
            try { values.add(field.get(obj)); } catch (IllegalAccessException e) { throw new RuntimeException(e); }
            columns.add(colName);
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append("?,".repeat(columns.size()));
        sql.setLength(sql.length() - 1);
        sql.append(")");
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) stmt.setObject(i + 1, values.get(i));
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkAndThrow(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Table.class)) throw new IllegalArgumentException("Class must have @Table annotation.");
    }

    private Class<?> getEntityClass(Object proxy) {
        return ReflectionUtils.getEntityFromClassInterface(proxy, repositoryInterface);
    }
}
