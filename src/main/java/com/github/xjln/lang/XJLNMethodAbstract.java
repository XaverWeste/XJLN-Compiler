package com.github.xjln.lang;

import java.util.HashMap;

public sealed class XJLNMethodAbstract permits XJLNMethod{

    public final boolean inner;
    public final String name;
    public final String[] genericTypes;
    public final HashMap<String, XJLNParameter> parameterTypes;
    public final String returnType;

    public XJLNMethodAbstract(boolean inner, String name, String[] genericTypes, HashMap<String, XJLNParameter> parameterTypes, String returnType){
        this.inner = inner;
        this.name = name;
        this.genericTypes = genericTypes;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    public XJLNMethodAbstract(XJLNMethodAbstract am){
        this.inner = am.inner;
        this.name = am.name;
        this.genericTypes = am.genericTypes;
        this.parameterTypes = am.parameterTypes;
        this.returnType = am.returnType;
    }

    public XJLNMethod implementMethod(String[] code){
        return new XJLNMethod(this, code);
    }
}
