package com.github.xjln.lang;

import com.github.xjln.compiler.AST;

public class XJLNMethod {

    public final String[] parameter;
    public final boolean inner;
    public final String returnType;
    public final AST[] code;

    public XJLNMethod(String[] parameter, boolean inner, String returnType, AST[] code){
        this.parameter = parameter;
        this.inner = inner;
        this.returnType = returnType;
        this.code = code;
    }
}
