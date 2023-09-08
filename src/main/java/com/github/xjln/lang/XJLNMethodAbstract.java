package com.github.xjln.lang;

import java.util.HashMap;

public sealed class XJLNMethodAbstract permits XJLNMethod{

    public final boolean inner;
    public final String name;
    public final String[] genericTypes;
    public final HashMap<String, String> parameterTypes;
    public final String returnType;

    public XJLNMethodAbstract(boolean inner, String name, String[] genericTypes, HashMap<String, String> parameterTypes, String returnType){
        this.inner = inner;
        this.name = name;
        this.genericTypes = genericTypes;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }
}
