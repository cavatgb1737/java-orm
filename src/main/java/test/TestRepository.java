package test;

import repository.SimpleJDBCRepository;

/*
    Test repository class
 */
public interface TestRepository extends SimpleJDBCRepository<Test, String> {
    /*
    Custom repository method
     */
    String findByName(String name);
}
