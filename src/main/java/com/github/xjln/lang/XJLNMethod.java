package com.github.xjln.lang;

import com.github.xjln.utility.MatchedList;

import java.lang.reflect.Method;

public class XJLNMethod {

    public final MatchedList<String, XJLNVariable> parameter;
    public final boolean inner;
    public final String returnType;
    public final String[] code;

    public XJLNMethod(MatchedList<String, XJLNVariable> parameter, boolean inner, String returnType, String[] code){
        this.parameter = parameter;
        this.inner = inner;
        this.returnType = returnType;
        this.code = code;
    }

    public static XJLNMethod ofMethod(Method method){
        return new XJLNMethod(new MatchedList<>(), method.toString().startsWith("private ") || method.toString().startsWith("protected "), method.getReturnType().toString().split(" ", 2)[1], null);
    }
}
