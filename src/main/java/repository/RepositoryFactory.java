package repository;

import annotations.Table;

import java.lang.reflect.Proxy;

public class RepositoryFactory {

    @SuppressWarnings("unchecked")
    public static <T, ID> T createRepository(Class<T> repositoryInterface, SQLConnection connection) {
        T repo = (T) Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class[]{repositoryInterface},
                new RepositoryHandler(connection, repositoryInterface)
        );

        Class<?> entityClass = ReflectionUtils.getEntityFromClassInterface(repo, repositoryInterface);
        if (entityClass.isAnnotationPresent(Table.class)) {
            Table table = entityClass.getAnnotation(Table.class);
            if (table.autoCreate()) {
                TableCreator.createTable(entityClass, connection);
            }
        }

        return repo;
    }
}
