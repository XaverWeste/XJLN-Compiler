package com.github.xjln.lang;

import java.util.ArrayList;

public class XJLNClass implements Compilable {

    public final String[] parameter;
    public final String constructor;
    public final ArrayList<XJLNMethod> methods;

    public XJLNClass(String[] parameter, String constructor){
        this.parameter = parameter;
        this.constructor = constructor;
        methods = new ArrayList<>();
    }

}
