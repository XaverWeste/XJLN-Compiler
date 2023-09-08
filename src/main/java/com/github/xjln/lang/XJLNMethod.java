package com.github.xjln.lang;

import java.util.HashMap;

public final class XJLNMethod extends XJLNMethodAbstract{

    public final String[] code;

    public XJLNMethod(boolean inner, String name, String[] genericTypes, HashMap<String, String> parameterTypes, String returnType, String[] code){
        super(inner, name, genericTypes, parameterTypes, returnType);

        this.code = code;
    }

    public XJLNMethod(XJLNMethodAbstract abstractMethod, Strung[] code){
        super(abstractMethod);

        this.code = code;
    }
}
