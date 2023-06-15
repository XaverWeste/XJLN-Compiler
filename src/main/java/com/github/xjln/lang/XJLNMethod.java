package com.github.xjln.lang;

public class XJLNMethod {

    public final String[] parameter;
    public final boolean inner;
    public final String returnType;
    public final String code;

    public XJLNMethod(String[] parameter, boolean inner, String returnType, String code){
        this.parameter = parameter;
        this.inner = inner;
        this.returnType = returnType;
        this.code = code;
    }
}
