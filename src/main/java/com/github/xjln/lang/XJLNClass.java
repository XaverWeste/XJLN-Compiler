package com.github.xjln.lang;

import java.util.HashMap;

public class XJLNClass implements Compilable {

    public final String[] parameter;
    public final String[] superClasses;
    public final String constructor;
    public final HashMap<String, XJLNMethod> methods;
    public final HashMap<String, XJLNVariable> fields;

    public XJLNClass(String[] parameter, String[] superClasses, String constructor){
        this.parameter = parameter;
        this.superClasses = superClasses;
        this.constructor = constructor;
        methods = new HashMap<>();
        fields = new HashMap<>();
    }

}
