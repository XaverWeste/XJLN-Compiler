package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

public final class XJLNType extends Compilable{

    public final String[] values;

    public XJLNType(AccessFlag accessFlag, String[] values){
        super(accessFlag);
        this.values = values;
    }

    public boolean hasValue(String value){
        for(String s:values)
            if(s.equals(value))
                return true;

        return false;
    }
}
