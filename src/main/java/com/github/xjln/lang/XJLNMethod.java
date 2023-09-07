package com.github.xjln.lang;

import com.github.xjln.utility.MatchedList;

public final class XJLNMethod extends XJLNMethodAbstract{

    public final String[] code;

    public XJLNMethod(boolean inner, String name, String[] genericTypes, MatchedList<String, String> parameterTypes, String returnType, String[] code){
        super(inner, name, genericTypes, parameterTypes, returnType);

        this.code = code;
    }
}
