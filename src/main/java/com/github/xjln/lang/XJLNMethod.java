package com.github.xjln.lang;

import com.github.xjln.utility.MatchedList;

import java.lang.reflect.Method;

public class XJLNMethod {

    public final MatchedList<String, XJLNVariable> parameter;
    public final boolean inner;
    public final boolean statik;
    public final String returnType;
    public final String[] generics;
    public final String[] code;

    public XJLNMethod(MatchedList<String, XJLNVariable> parameter, boolean inner, boolean statik, String returnType, String[] code){
        this.parameter = parameter;
        this.inner = inner;
        this.statik = statik;
        this.returnType = returnType;
        this.generics = null; //TODO
        this.code = code;
    }

    public static XJLNMethod ofMethod(Method method){
        return new XJLNMethod(new MatchedList<>(), method.toString().startsWith("private ") || method.toString().startsWith("protected "), method.toString().contains(" static "), method.getReturnType().toString().split(" ", 2)[1], null);
    }
}
