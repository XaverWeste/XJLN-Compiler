package com.github.xjln.lang;

import com.github.xjln.compiler.Compiler;
import com.github.xjln.utility.MatchedList;

import java.util.HashMap;

public sealed class XJLNMethodAbstract permits XJLNMethod{

    public final boolean statik;
    public final boolean inner;
    public final String name;
    public final String[] genericTypes;
    public final MatchedList<String, XJLNParameter> parameterTypes;
    public final String returnType;
    public final HashMap<String, String> aliases;

    public XJLNMethodAbstract(boolean statik, boolean inner, String name, String[] genericTypes, MatchedList<String, XJLNParameter> parameterTypes, String returnType, HashMap<String, String> aliases){
        this.statik = statik;
        this.inner = inner;
        this.name = name;
        this.genericTypes = genericTypes;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
        this.aliases = aliases;
    }

    public XJLNMethodAbstract(XJLNMethodAbstract am){
        this.statik = am.statik;
        this.inner = am.inner;
        this.name = am.name;
        this.genericTypes = am.genericTypes;
        this.parameterTypes = am.parameterTypes;
        this.returnType = am.returnType;
        this.aliases = am.aliases;
    }

    public XJLNMethod implementMethod(String[] code){
        return new XJLNMethod(this, code);
    }

    public boolean isGeneric(String parameterType){
        for(String genericType:genericTypes)
            if(parameterType.equals(genericType))
                return true;

        return false;
    }

    @Override
    public String toString() {
        return Compiler.toCompilerDesc(this);
    }
}
