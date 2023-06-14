package com.github.xjln.lang;

import java.util.ArrayList;

public class XJLNEnum extends XJLNClass{

    private final String[] values;

    public XJLNEnum(ArrayList<String> values){
        this.values = values.toArray(new String[0]);
    }

    public String[] getValues(){
        return values;
    }
}
