package com.github.xjln.lang;

import java.util.ArrayList;

public class XJLNClass implements Compilable {

    public final String[] parameter;
    public final String[] superClasses;
    public final String constructor;
    public final ArrayList<XJLNMethod> methods;
    public final ArrayList<String> fields;

    public XJLNClass(String[] parameter, String[] superClasses, String constructor){
        this.parameter = parameter;
        this.superClasses = superClasses;
        this.constructor = constructor;
        methods = new ArrayList<>();
        fields = new ArrayList<>();
    }

}
