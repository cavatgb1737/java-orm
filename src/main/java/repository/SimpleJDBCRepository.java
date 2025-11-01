package repository;

import java.util.List;

public interface SimpleJDBCRepository<T, ID> {

    /*
        All pre-built JDBC interactivity methods
     */

    int save(T obj);
    T findById(ID id);
    int delete(ID id);
    boolean existsById(ID id);
    List<T> findAll();
}
