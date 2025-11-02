package test;

import repository.SimpleJDBCRepository;

import java.util.List;

/*
    Test repository class
 */
public interface TestRepository extends SimpleJDBCRepository<Test, String> {

    /*
    Custom repository methods
     */



    List<Test> findByTest_Salary(double test_salary);

    Test findByUsername(String username);
}
