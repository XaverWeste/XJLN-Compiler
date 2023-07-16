package com.github.xjln.lang;

import java.util.ArrayList;

public class XJLNEnum implements Compilable {

    public final String[] values;

    public XJLNEnum(ArrayList<String> values){
        this.values = values.toArray(new String[0]);
    }

    public boolean hasValue(String value){
        for(String v:values)
            if(v.equals(value))
                return true;
        return false;
    }
}
