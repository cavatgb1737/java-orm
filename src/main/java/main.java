import databases.DatabaseType;
import repository.RepositoryFactory;
import repository.SQLConnection;
import test.TestRepository;

public class main{

    public static void main(String[] args){

        //Create repository.SQLConnection object
        SQLConnection conn = new SQLConnection("postgres", "12345", "db", DatabaseType.POSTGRESQL);

        //Enable sql logging to console
        conn.enableLogging();

        //Initialize a repository in test folder
        TestRepository testRepository = RepositoryFactory.createRepository(TestRepository.class, conn);

        //Use methods
        testRepository.findAll();
        testRepository.findByName("testname");
        testRepository.existsById("testid");

    }


}