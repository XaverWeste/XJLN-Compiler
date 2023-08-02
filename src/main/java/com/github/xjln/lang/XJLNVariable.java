package com.github.xjln.lang;

public class XJLNVariable {

    public final boolean constant;
    public final String type;

    public XJLNVariable(boolean constant, String type){
        this.constant = constant;
        this.type = type;
    }

    public XJLNVariable(String type){
        this.constant = false;
        this.type = type;
    }
}
