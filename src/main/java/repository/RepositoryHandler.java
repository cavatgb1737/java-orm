package repository;

import annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
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
            return handleFindByCustom(entityClass, args[0], paramName);

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
        Class<?> clazz = obj.getClass();
        checkAndThrow(clazz);
        String name = clazz.getAnnotation(Table.class).name();
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(Required.class)) {
                String colName = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
                columns.add(colName);
                try {
                    Object val = field.get(obj);
                    if (field.isAnnotationPresent(Required.class)) {
                        if (val == null || (val instanceof String && ((String) val).isEmpty())) {
                            throw new IllegalStateException(colName + " breaks @annotations.Required constraint.");
                        }
                    }
                    values.add(val);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(name).append(" (");
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
        String idColumn = null;
        List<Field> fields = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Id.class)) idColumn = field.getName();
            if (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class)) fields.add(field);
        }

        if (idColumn == null) throw new RuntimeException("No @annotations.Id field found.");

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ").append(idColumn).append(" = ? ");
        Connection conn = sqlConnection.getConn();

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setObject(1, idValue);
            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            if (rs.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Field field : fields) {
                    String colName = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
                    Object value = rs.getObject(colName);
                    field.setAccessible(true);
                    field.set(instance, value);
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

        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE ").append(idColumn).append(" = ?");
        Connection conn = sqlConnection.getConn();

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setObject(1, idValue);
            int rows = stmt.executeUpdate();
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T handleFindByCustom(Class<T> clazz, Object param, String paramName) {
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        paramName = Character.toLowerCase(paramName.charAt(0)) + paramName.substring(1);
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ").append(paramName).append(" = ? ");
        Connection conn = sqlConnection.getConn();

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setObject(1, param);
            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            if (rs.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Field field : clazz.getDeclaredFields()) {
                    String colName = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
                    Object value = rs.getObject(colName);
                    field.setAccessible(true);
                    field.set(instance, value);
                }
                return instance;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean handleExistsByCustom(Class<?> clazz, Object param, String paramName) {
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        paramName = Character.toLowerCase(paramName.charAt(0)) + paramName.substring(1);
        StringBuilder sql = new StringBuilder("SELECT EXISTS(SELECT 1 FROM ").append(tableName).append(" WHERE ").append(paramName).append(" = ?);");
        Connection conn = sqlConnection.getConn();
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setObject(1, param);
            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            if (rs.next()) return rs.getBoolean(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public boolean handleExistsById(Class<?> clazz, Object idValue) {
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        String idColumn = null;

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Id.class)) {
                idColumn = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
                break;
            }
        }

        StringBuilder sql = new StringBuilder("SELECT EXISTS(SELECT 1 FROM ").append(tableName).append(" WHERE ").append(idColumn).append(" = ?);");
        Connection conn = sqlConnection.getConn();

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setObject(1, idValue);
            ResultSet rs = stmt.executeQuery();
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            if (rs.next()) return rs.getBoolean(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private void checkAndThrow(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(Table.class)) throw new IllegalArgumentException("Class must have @annotations.Table annotation.");
    }

    public <T> List<T> handleFindAll(Class<T> clazz) {
        checkAndThrow(clazz);
        List<T> resultList = new ArrayList<>();
        String tableName = clazz.getAnnotation(Table.class).name();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);

        try (Statement stmt = sqlConnection.getConn().createStatement()) {
            ResultSet rs = stmt.executeQuery(sql.toString());
            Field[] fields = clazz.getDeclaredFields();
            while (rs.next()) {
                T obj = clazz.getDeclaredConstructor().newInstance();
                for (Field field : fields) {
                    field.setAccessible(true);
                    String colName = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
                    Object val = rs.getObject(colName);
                    field.set(obj, val);
                }
                resultList.add(obj);
            }
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            return resultList;
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
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            throw new RuntimeException(e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }

    private int handleSaveWithConnection(Connection conn, Object obj) {
        Class<?> clazz = obj.getClass();
        checkAndThrow(clazz);
        String tableName = clazz.getAnnotation(Table.class).name();
        List<String> columns = new ArrayList<>();
        List<Object> vals = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String columName = field.isAnnotationPresent(Column.class) ? field.getAnnotation(Column.class).name() : field.getName();
            try {
                Object val = field.get(obj);
                if (field.isAnnotationPresent(Required.class)) {
                    if (val == null || (val instanceof String && ((String) val).isEmpty())) {
                        throw new IllegalStateException(columName + " breaks @annotations.Required constraint.");
                    }
                }
                columns.add(columName);
                vals.add(val);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append("?,".repeat(columns.size()));
        sql.setLength(sql.length() - 1);
        sql.append(")");

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < vals.size(); i++) stmt.setObject(i + 1, vals.get(i));
            if (sqlConnection.getLogsEnabled()) log.info(sql.toString());
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?> getEntityClass(Object proxy) {
        return ReflectionUtils.getEntityFromClassInterface(proxy, repositoryInterface);
    }
}
