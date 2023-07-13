package com.github.xjln.lang;

import com.github.xjln.utility.SearchList;

import java.util.HashMap;

public class XJLNClass implements Compilable {

    public final SearchList<String, XJLNVariable> parameter;
    public final String[] superClasses;
    public final String constructor;
    public final HashMap<String, XJLNMethod> methods;
    public final HashMap<String, XJLNVariable> fields;

    public XJLNClass(SearchList<String, XJLNVariable> parameter, String[] superClasses, String constructor){
        this.parameter = parameter;
        this.superClasses = superClasses;
        this.constructor = constructor;
        methods = new HashMap<>();
        fields = new HashMap<>();
    }

    public void addField(String name, XJLNVariable var){

    }

}
