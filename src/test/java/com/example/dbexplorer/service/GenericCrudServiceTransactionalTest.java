package com.example.dbexplorer.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the generic write methods carry a @Transactional boundary and are
 * public (required for Spring's proxy-based transaction advice to apply).
 */
class GenericCrudServiceTransactionalTest {

    private Method method(String name, Class<?>... params) throws NoSuchMethodException {
        return GenericCrudService.class.getMethod(name, params);
    }

    @Test
    void createIsTransactionalAndPublic() throws Exception {
        Method m = method("create",
            com.example.dbexplorer.config.CrudEntities.CrudEntity.class, Map.class);
        assertNotNull(AnnotationUtils.findAnnotation(m, Transactional.class), "create must be @Transactional");
        assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()), "create must be public");
    }

    @Test
    void updateIsTransactionalAndPublic() throws Exception {
        Method m = method("update",
            com.example.dbexplorer.config.CrudEntities.CrudEntity.class,
            String.class, Map.class, String.class);
        assertNotNull(AnnotationUtils.findAnnotation(m, Transactional.class), "update must be @Transactional");
        assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()), "update must be public");
    }

    @Test
    void deleteIsTransactionalAndPublic() throws Exception {
        Method m = method("delete",
            com.example.dbexplorer.config.CrudEntities.CrudEntity.class,
            String.class, String.class);
        assertNotNull(AnnotationUtils.findAnnotation(m, Transactional.class), "delete must be @Transactional");
        assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()), "delete must be public");
    }
}
