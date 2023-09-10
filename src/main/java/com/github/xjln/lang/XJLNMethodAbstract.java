package com.github.xjln.lang;

import com.github.xjln.compiler.Compiler;
import com.github.xjln.utility.MatchedList;

public sealed class XJLNMethodAbstract permits XJLNMethod{

    public final boolean statik;
    public final boolean inner;
    public final String name;
    public final String[] genericTypes;
    public final MatchedList<String, XJLNParameter> parameterTypes;
    public final String returnType;

    public XJLNMethodAbstract(boolean statik, boolean inner, String name, String[] genericTypes, MatchedList<String, XJLNParameter> parameterTypes, String returnType){
        this.statik = statik;
        this.inner = inner;
        this.name = name;
        this.genericTypes = genericTypes;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    public XJLNMethodAbstract(XJLNMethodAbstract am){
        this.statik = am.statik;
        this.inner = am.inner;
        this.name = am.name;
        this.genericTypes = am.genericTypes;
        this.parameterTypes = am.parameterTypes;
        this.returnType = am.returnType;
    }

    public XJLNMethod implementMethod(String[] code){
        return new XJLNMethod(this, code);
    }

    @Override
    public String toString() {
        return Compiler.toCompilerDesc(this);
    }
}
