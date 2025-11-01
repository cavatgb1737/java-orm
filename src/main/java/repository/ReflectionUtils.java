package repository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ReflectionUtils {

    public static Class<?> getEntityFromClassInterface(Object proxy, Class<?> repoInterface){

        Type[] genericInterfaces = repoInterface.getGenericInterfaces();

        for(Type type: genericInterfaces){
            if(type instanceof ParameterizedType){
                ParameterizedType pt = (ParameterizedType) type;
                Type[] typeArgs = pt.getActualTypeArguments();
                if(typeArgs.length > 0){
                    return (Class<?>) typeArgs[0];
                }
            }
        }

        return Object.class;

    }

}
