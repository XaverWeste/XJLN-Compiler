package com.github.xjln.lang;

import java.util.ArrayList;

public class XJLNEnum implements Compilable {

    public final String[] values;
    public final String name;

    public XJLNEnum(String name, ArrayList<String> values){
        this.name = name;
        this.values = values.toArray(new String[0]);
    }

    public boolean hasValue(String value){
        for(String v:values)
            if(v.equals(value))
                return true;
        return false;
    }

    @Override
    public boolean isGeneric(String type) {
        return false;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public String getClassType() {
        return "Enum";
    }
}
