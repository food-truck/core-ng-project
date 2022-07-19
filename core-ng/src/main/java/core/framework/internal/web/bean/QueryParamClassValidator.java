package core.framework.internal.web.bean;

import core.framework.api.json.Property;
import core.framework.api.web.service.QueryParam;
import core.framework.internal.bean.BeanClassNameValidator;
import core.framework.internal.json.JSONClassValidator;
import core.framework.internal.reflect.Fields;
import core.framework.internal.validate.ClassValidator;
import core.framework.internal.validate.ClassVisitor;
import core.framework.util.Sets;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Set;

import static core.framework.util.Strings.format;

/**
 * @author neo
 */
final class QueryParamClassValidator implements ClassVisitor {
    private final ClassValidator validator;
    private final Set<String> visitedParams = Sets.newHashSet();
    //just change private to public here, since we don't want to make many changes from upstream
    public final BeanClassNameValidator beanClassNameValidator;

    QueryParamClassValidator(Class<?> beanClass, BeanClassNameValidator beanClassNameValidator) {
        this.beanClassNameValidator = beanClassNameValidator;
        validator = new ClassValidator(beanClass);
        validator.allowedValueClasses = Set.of(String.class, Boolean.class,
            Integer.class, Long.class, Double.class, BigDecimal.class,
            LocalDate.class, LocalDateTime.class, ZonedDateTime.class, LocalTime.class);
        validator.visitor = this;
    }

    public void validate() {
        validator.validate();
    }

    @Override
    public void visitClass(Class<?> objectClass, String path) {
//        beanClassNameValidator.validate(objectClass);
    }

    @Override
    public void visitField(Field field, String parentPath) {
        QueryParam queryParam = field.getDeclaredAnnotation(QueryParam.class);
        if (queryParam == null)
            throw new Error("field must have @QueryParam, field=" + Fields.path(field));

        Property property = field.getDeclaredAnnotation(Property.class);
        if (property != null)
            throw new Error("field must not have @Property, field=" + Fields.path(field));

        String name = queryParam.name();

        boolean added = visitedParams.add(queryParam.name());
        if (!added)
            throw new Error(format("found duplicate query param, field={}, name={}", Fields.path(field), name));
    }

    @Override
    public void visitEnum(Class<?> enumClass) {
//        beanClassNameValidator.validate(enumClass);
        JSONClassValidator.validateEnum(enumClass);
    }
}
