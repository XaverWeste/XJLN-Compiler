package com.github.xjln.lang;

import com.github.xjln.utility.MatchedList;

import java.util.HashMap;

public final class XJLNClass extends XJLNClassStatic {

    public final boolean abstrakt;
    public final String[] generics;
    public final String[] superClasses;
    public final MatchedList<String, XJLNParameter> parameter;
    public final HashMap<String, XJLNField> fields;
    public final HashMap<String, XJLNMethodAbstract> methods;

    public XJLNClass(boolean abstrakt, String name, String[] generics, MatchedList<String, XJLNParameter> parameter, String[] superClasses, HashMap<String, String> aliases){
        super(name, aliases);

        this.abstrakt = abstrakt;
        this.generics = generics;
        this.parameter = parameter;
        this.superClasses = superClasses;

        this.fields = new HashMap<>();
        this.methods = new HashMap<>();
    }

    public XJLNClass(XJLNClassStatic staticClass, boolean abstrakt, String[] generics, MatchedList<String, XJLNParameter> parameter, String[] superClasses){
        super(staticClass);

        this.abstrakt = abstrakt;
        this.generics = generics;
        this.parameter = parameter;
        this.superClasses = superClasses;

        this.fields = new HashMap<>();
        this.methods = new HashMap<>();
    }

    public void addField(String name, XJLNField field){
        if(fields.containsKey(name))
            throw new RuntimeException("Field " + name + " already exist in Class " + name);

        fields.put(name, field);
    }

    public void addMethod(String name, XJLNMethodAbstract method){
        if(methods.containsKey(name))
            throw new RuntimeException("Method " + name + " already exist in Class " + this.name);

        methods.put(name, method);
    }
}
