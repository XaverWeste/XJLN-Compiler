package com.github.xjln.lang;

public record XJLNParameter(boolean constant, String type, String name, String value){

    public XJLNField toField(){
        return new XJLNField(false, constant, type, name, value);
    }
}
