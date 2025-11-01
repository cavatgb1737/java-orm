package test;

import annotations.Id;
import annotations.Required;
import annotations.Table;

/*
    Test entity class
 */
@Table(name = "test", autoCreate = true)
public class Test {

    @Id
    private String id;

    @Required
    private String name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
