package com.github.xjln.lang;

public class XJLNVariable {

    public final boolean inner;
    public final boolean constant;
    public final String[] types;
    public final String value;
    public String currentType;

    public XJLNVariable(boolean inner, boolean constant, String[] types, String value){
        this.inner = inner;
        this.constant = constant;
        this.types = types;
        this.value = value;
        currentType = null;
    }

    public XJLNVariable(String[] types, String value){
        this.inner = false;
        this.constant = false;
        this.types = types;
        this.value = value;
        currentType = null;
    }

    public static XJLNVariable ofString(String variable){
        String[] args = variable.split(" ");
        return new XJLNVariable(new String[]{args[0]}, args.length > 2 ? args[2] : null);
    }
}
