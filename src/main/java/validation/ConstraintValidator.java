package validation;

import annotations.Column;
import annotations.Id;
import annotations.Required;
import annotations.Table;

import java.lang.reflect.Field;

public class ConstraintValidator {

    public static <T> boolean validateConstraints(Class<T> clazz, Object obj) throws IllegalAccessException {

        if(!clazz.isAnnotationPresent(Table.class)){
            throw new TableAnnotationRequired("Class must have @Table annotation.");
        }
        boolean idFound = false;

        for(Field field: clazz.getDeclaredFields()){
            field.setAccessible(true);
            if(field.isAnnotationPresent(Required.class)){
                Object val = field.get(obj);
                if(val == null || val instanceof String && ((String) val).isEmpty()){
                    throw new ConstraintViolationException("@Required constraint is violated for field " + field.getName());
                }
            }
            if(field.isAnnotationPresent(Id.class)){
                idFound = true;
            }
        }
        if(!idFound){
            throw new IdAnnotationRequired("Class must have @Id annotation.");
        }

        return true;



    }
}
