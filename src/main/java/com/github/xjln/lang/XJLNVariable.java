package com.github.xjln.lang;

public class XJLNVariable {

    public final boolean inner;
    public final boolean constant;
    public final String[] types;
    public final String value;

    public XJLNVariable(boolean inner, boolean constant, String[] types, String value){
        this.inner = inner;
        this.constant = constant;
        this.types = types;
        this.value = value;
    }
}
