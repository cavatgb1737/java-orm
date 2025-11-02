package test;

import annotations.*;

/*
    Test entity class
 */

@Table(name = "tests", autoCreate = true)
public class Test {

    @Id
    private String id;

    @Required
    private String name;

    @Column(unique = true)
    private String email;

    private int age;

    @Column(name = "test_salary")
    private Double salary;

    @Column(unique = true, nullable = false)
    private String username;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public Double getSalary() { return salary; }
    public void setSalary(Double salary) { this.salary = salary; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

}
