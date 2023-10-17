package com.github.xjln.lang;

import com.github.xjln.utility.MatchedList;

import java.util.HashMap;

public final class XJLNMethod extends XJLNMethodAbstract{

    public final String[] code;

    public XJLNMethod(boolean statik, boolean inner, String name, String[] genericTypes, MatchedList<String, XJLNParameter> parameter, String returnType, String[] code, HashMap<String, String> aliases){
        super(statik, inner, name, genericTypes, parameter, returnType, aliases);

        this.code = code;
    }

    public XJLNMethod(XJLNMethodAbstract abstractMethod, String[] code){
        super(abstractMethod);

        this.code = code;
    }
}
