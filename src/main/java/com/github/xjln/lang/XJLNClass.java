package com.github.xjln.lang;

public class XJLNClass implements Compilable {

    public final String[] parameter;
    public final String constructor;

    public XJLNClass(String[] parameter, String constructor){
        this.parameter = parameter;
        this.constructor = constructor;
    }

}
