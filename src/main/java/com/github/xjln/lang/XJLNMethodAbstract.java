package com.github.xjln.lang;

import com.github.xjln.utility.MatchedList;

public sealed class XJLNMethodAbstract permits XJLNMethod{

    public final boolean inner;
    public final String name;
    public final String[] genericTypes;
    public final MatchedList<String, String> parameterTypes;
    public final String returnType;

    public XJLNMethodAbstract(boolean inner, String name, String[] genericTypes, MatchedList<String, String> parameterTypes, String returnType){
        this.inner = inner;
        this.name = name;
        this.genericTypes = genericTypes;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }
}
