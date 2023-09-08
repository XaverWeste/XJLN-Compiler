package com.github.xjln.lang;

import java.util.HashMap;

public final class XJLNClass extends XJLNClassStatic {

    public final boolean abstrakt;
    public final String[] generics;
    public final String[] superClasses;
    public final HashMap<String, XJLNParameter> parameter;
    public final HashMap<String, XJLNField> fields;
    public final HashMap<String, XJLNMethod> methods;

    public XJLNClass(boolean abstrakt, String name, String[] generics, HashMap<String, XJLNParameter> parameter, String[] superClasses, HashMap<String, String> aliases){
        super(name, aliases);

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

    public void addMethod(String name, XJLNMethod method){
        if(methods.containsKey(name))
            throw new RuntimeException("Method " + name + " already exist in Class " + this.name);

        methods.put(name, method);
    }
}
