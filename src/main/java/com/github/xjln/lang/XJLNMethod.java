package com.github.xjln.lang;

import com.github.xjln.utility.SearchList;

public class XJLNMethod {

    public final SearchList<String, XJLNVariable> parameter;
    public final boolean inner;
    public final String returnType;
    public final String[] code;

    public XJLNMethod(SearchList<String, XJLNVariable> parameter, boolean inner, String returnType, String[] code){
        this.parameter = parameter;
        this.inner = inner;
        this.returnType = returnType;
        this.code = code;
    }

    public boolean matches(String...types){
        if(types.length != parameter.size())
            return false;
        for(int i = 0;i < types.length;i++)
            if(!parameter.get(i).type.equals(types[i]))
                return false;
        return true;
    }
}
