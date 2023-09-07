package com.github.xjln.lang;

import java.util.HashMap;

public sealed class XJLNClassStatic implements Compilable permits XJLNClass{

    public final String name;
    public final HashMap<String, XJLNField> staticFields;
    public final HashMap<String, XJLNMethod> staticMethods;
    public final HashMap<String, String> aliases;

    public XJLNClassStatic(String name, HashMap<String, String> aliases){
        this.name = name;
        this.aliases = aliases;

        this.staticFields = new HashMap<>();
        this.staticMethods = new HashMap<>();
    }

    public void addStaticField(String name, XJLNField field){
        if(staticFields.containsKey(name))
            throw new RuntimeException("Field " + name + " already exist in Class " + name);

        staticFields.put(name, field);
    }

    public void addStaticMethod(String name, XJLNMethod method){
        if(staticMethods.containsKey(name))
            throw new RuntimeException("Method " + name + " already exist in Class " + this.name);

        staticMethods.put(name, method);
    }
}
