package com.github.xjln.lang;

import com.github.xjln.utility.SearchList;

import java.util.HashMap;

public class XJLNClass implements Compilable {

    public final SearchList<String, XJLNVariable> parameter;
    public final String[] superClasses;
    public final HashMap<String, XJLNMethod> methods;
    public final HashMap<String, XJLNVariable> fields;

    public XJLNClass(SearchList<String, XJLNVariable> parameter, String[] superClasses){
        this.parameter = parameter;
        this.superClasses = superClasses;
        methods = new HashMap<>();
        fields = new HashMap<>();
    }

    public void addField(String name, XJLNVariable var){
        if(fields.containsKey(name))
            throw new RuntimeException("field " + name + " already exist");
        fields.put(name, var);
    }

    public void addMethod(String name, XJLNMethod method){
        if(methods.containsKey(name))
            throw new RuntimeException("method " + name + " already exist");
        methods.put(name, method);
    }

}
