package com.github.xjln.lang;

public class XJLNVariable {

    public final boolean inner;
    public final boolean constant;
    public final String type;

    public XJLNVariable(boolean inner, boolean constant, String type){
        this.inner = inner;
        this.constant = constant;
        this.type = type;
    }

    public XJLNVariable(String type){
        this.inner = false;
        this.constant = false;
        this.type = type;
    }
}
