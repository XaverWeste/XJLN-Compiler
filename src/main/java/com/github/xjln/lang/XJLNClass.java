package com.github.xjln.lang;

import com.github.xjln.compiler.Compiler;
import com.github.xjln.utility.MatchedList;

import java.util.HashMap;

public final class XJLNClass extends XJLNClassStatic {

    public final boolean isDataClass;
    public final boolean abstrakt;
    public final String[] generics;
    public final String[] superClasses;
    public final MatchedList<String, XJLNParameter> parameter;
    private final HashMap<String, XJLNField> fields;
    private final HashMap<String, XJLNMethodAbstract> methods;

    public XJLNClass(boolean isDataClass, boolean abstrakt, String name, String[] generics, MatchedList<String, XJLNParameter> parameter, String[] superClasses, HashMap<String, String> aliases){
        super(name, aliases);

        this.isDataClass = isDataClass;
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

        this.isDataClass = false;
        this.fields = new HashMap<>();
        this.methods = new HashMap<>();
    }

    public void addField(String name, XJLNField field){
        if(fields.containsKey(name))
            throw new RuntimeException("Field " + name + " already exist in Class " + name);

        fields.put(name, field);
    }

    public void addMethod(XJLNMethodAbstract method){
        String methodName = Compiler.toCompilerDesc(method);

        if(methods.containsKey(methodName))
            throw new RuntimeException("Method " + methodName + " already exist in Class " + name);

        methods.put(methodName, method);
    }

    public boolean hasStatic(){
        return staticMethods.size() > 0 || staticFields.size() > 0;
    }

    public XJLNMethodAbstract generateDefaultInit(){
        return new XJLNMethodAbstract(false, false, "init", null, parameter, "void");
    }
}
