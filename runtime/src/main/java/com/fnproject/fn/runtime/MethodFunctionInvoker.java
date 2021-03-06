package com.fnproject.fn.runtime;


import com.fnproject.fn.api.FunctionInvoker;
import com.fnproject.fn.api.InputEvent;
import com.fnproject.fn.api.InvocationContext;
import com.fnproject.fn.api.OutputEvent;
import com.fnproject.fn.runtime.exception.FunctionInputHandlingException;
import com.fnproject.fn.runtime.exception.InternalFunctionInvocationException;
import com.fnproject.fn.runtime.exception.FunctionOutputHandlingException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Method function invoker
 * <p>
 * <p>
 * This handles the binding and invocation of function calls via java methods.
 */
public class MethodFunctionInvoker implements FunctionInvoker {

    /**
     * Invoke the function wrapped by this loader
     *
     * @param evt The function event
     * @return the function response
     * @throws InternalFunctionInvocationException if the invocation fails
     */
    @Override
    public Optional<OutputEvent> tryInvoke(InvocationContext ctx, InputEvent evt) throws InternalFunctionInvocationException {

        Object[] userFunctionParams = doInputCoercions(ctx, evt);

        FunctionRuntimeContext runtimeContext = (FunctionRuntimeContext) ctx.getRuntimeContext();
        Method targetMethod = runtimeContext.getTargetMethod();
        Object rawResult;

        try {
            rawResult = targetMethod.invoke(ctx.getRuntimeContext().getInvokeInstance().orElse(null), userFunctionParams);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new InternalFunctionInvocationException(e.getCause().getMessage(), e.getCause());
        }

        return doOutputCoercion(ctx, runtimeContext, targetMethod, rawResult);

    }

    private Object[] doInputCoercions(InvocationContext ctx, InputEvent evt) {

        try {

            FunctionRuntimeContext runtimeContext = (FunctionRuntimeContext) ctx.getRuntimeContext();
            Method targetMethod = runtimeContext.getTargetMethod();
            Class<?>[] paramTypes = targetMethod.getParameterTypes();

            Object[] userFunctionParams = new Object[paramTypes.length];

            for (int i = 0; i < userFunctionParams.length; i++) {
                int param = i;
                Optional<Object> arg = runtimeContext.getInputCoercions(targetMethod, param)
                        .stream()
                        .map((c) -> c.tryCoerceParam(ctx, param, evt))
                        .filter(Optional::isPresent)
                        .map(Optional::get).findFirst();

                userFunctionParams[i] = arg.orElseThrow(() -> new FunctionInputHandlingException("No type coercion for argument " + param + " of " + targetMethod + " of found"));
            }

            return userFunctionParams;

        } catch (RuntimeException e) {
            throw new FunctionInputHandlingException("An exception was thrown during Input Coercion: " + e.getMessage(), e);
        }

    }


    private Optional<OutputEvent> doOutputCoercion(InvocationContext ctx, FunctionRuntimeContext runtimeContext, Method targetMethod, Object rawResult) {

        try {
            return Optional.of(runtimeContext.getOutputCoercions(targetMethod)
                    .stream()
                    .map((c) -> c.wrapFunctionResult(ctx, rawResult))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .orElseThrow(() -> new FunctionOutputHandlingException("No coercion found for return type")));

        } catch (RuntimeException e) {
            throw new FunctionOutputHandlingException("An exception was thrown during Output Coercion: " + e.getMessage(), e);
        }
    }
}
