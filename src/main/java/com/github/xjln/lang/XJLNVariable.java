package com.github.xjln.lang;

import com.github.xjln.compiler.Compiler;

public class XJLNVariable {

    public final boolean inner;
    public final boolean constant;
    public final String[] types;
    public final String value;
    public String currentType;

    public XJLNVariable(boolean inner, boolean constant, String[] types, String value, String currentType){
        this.inner = inner;
        this.constant = constant;
        this.types = types;
        this.value = value;
        this.currentType = currentType;
    } //TODO type check

    public XJLNVariable(String[] types, String value, String currentType){
        this.inner = false;
        this.constant = false;
        this.types = types;
        this.value = value;
        this.currentType = currentType;
    }

    public void validateTypes(){
        for(String type:types) if(!Compiler.classExist(type)) throw new RuntimeException("Class " + type + " does not exist");
    }

    public static XJLNVariable ofString(String variable){
        String[] args = variable.split(" ");
        return new XJLNVariable(new String[]{args[0]}, args.length > 2 ? args[2] : null, null);
    }
}
