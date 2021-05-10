package core.framework.internal.web.service;

import core.framework.api.web.service.PathParam;
import core.framework.api.web.service.ResponseStatus;
import core.framework.internal.asm.CodeBuilder;
import core.framework.internal.asm.DynamicInstanceBuilder;
import core.framework.internal.reflect.Methods;
import core.framework.internal.reflect.Params;
import core.framework.web.Controller;
import core.framework.web.Request;
import core.framework.web.Response;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static core.framework.internal.asm.Literal.type;
import static core.framework.internal.asm.Literal.variable;

/**
 * @author neo
 */
public class WebServiceControllerBuilder<T> {
    final DynamicInstanceBuilder<Controller> builder;
    private final Class<T> serviceInterface;
    private final T service;
    private final Method method;

    public WebServiceControllerBuilder(Class<T> serviceInterface, T service, Method method) {
        this.serviceInterface = serviceInterface;
        this.service = service;
        this.method = method;
        builder = new DynamicInstanceBuilder<>(Controller.class, service.getClass().getSimpleName() + "$" + method.getName());
    }

    public Controller build() {
        builder.addField("private final {} delegate;", type(serviceInterface));
        builder.constructor(new Class<?>[]{serviceInterface}, "this.delegate = $1;");

        boolean deprecated = method.isAnnotationPresent(Deprecated.class);
        builder.addMethod(buildMethod(deprecated));

        return builder.build(service);
    }

    private String buildMethod(boolean deprecated) {
        var builder = new CodeBuilder();
        builder.append("public {} execute({} request) throws Exception {\n", type(Response.class), type(Request.class));

        if (deprecated) builder.indent(1).append("{}.logDeprecation({});\n", type(WebServiceController.class), variable(Methods.path(method)));

        Annotation[][] annotations = method.getParameterAnnotations();
        List<String> params = new ArrayList<>(annotations.length);
        Class<?>[] paramClasses = method.getParameterTypes();
        for (int i = 0; i < annotations.length; i++) {
            Class<?> paramClass = paramClasses[i];
            String paramClassLiteral = type(paramClass);
            PathParam pathParam = Params.annotation(annotations[i], PathParam.class);
            if (pathParam != null) {
                params.add("$" + pathParam.value());
                if (String.class.equals(paramClass)) {
                    builder.indent(1).append("String ${} = request.pathParam(\"{}\");\n", pathParam.value(), pathParam.value());
                } else if (Integer.class.equals(paramClass)) {
                    builder.indent(1).append("Integer ${} = {}.toInt(request.pathParam(\"{}\"));\n", pathParam.value(), type(PathParamHelper.class), pathParam.value());
                } else if (Long.class.equals(paramClass)) {
                    builder.indent(1).append("Long ${} = {}.toLong(request.pathParam(\"{}\"));\n", pathParam.value(), type(PathParamHelper.class), pathParam.value());
                } else if (paramClass.isEnum()) {
                    builder.indent(1).append("{} ${} = ({}){}.toEnum(request.pathParam(\"{}\"), {});\n", paramClassLiteral, pathParam.value(), paramClassLiteral, type(PathParamHelper.class), pathParam.value(), variable(paramClass));
                } else {
                    throw new Error("not supported path param type, type=" + paramClass.getCanonicalName());
                }
            } else {
                params.add("bean");
                builder.indent(1).append("{} bean = ({}) request.bean({});\n", paramClassLiteral, paramClassLiteral, variable(paramClass));
            }
        }

        if (void.class == method.getReturnType()) {
            builder.indent(1).append("delegate.{}(", method.getName());
        } else {
            builder.indent(1).append("{} response = delegate.{}(", type(method.getReturnType()), method.getName());
        }
        builder.appendCommaSeparatedValues(params).append(");\n");

        if (void.class == method.getReturnType()) {
            builder.indent(1).append("return {}.empty()", type(Response.class));
        } else {
            builder.indent(1).append("return {}.bean(response)", type(Response.class));
        }

        ResponseStatus status = method.getDeclaredAnnotation(ResponseStatus.class);
        if (status == null) {
            builder.append(";\n");
        } else {
            builder.append(".status({});\n", variable(status.value()));
        }

        builder.append("}");
        return builder.build();
    }
}
