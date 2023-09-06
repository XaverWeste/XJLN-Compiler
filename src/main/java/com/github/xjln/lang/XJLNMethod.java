package com.github.xjln.lang;

import com.github.xjln.utility.MatchedList;

import java.lang.reflect.Method;

public record XJLNMethod(XJLNClass clazz, boolean inner, String name, String[] genericTypes, MatchedList<String, String> parameterTypes, String returnType, String[] code){

    public static XJLNMethod ofMethod(Method method){ //TODO
        return new XJLNMethod(null, (method.toString().startsWith("private ") || method.toString().startsWith("protected ")), method.getName(), null, null, method.getReturnType().toString().split(" ", 2)[1], null);
    }
}