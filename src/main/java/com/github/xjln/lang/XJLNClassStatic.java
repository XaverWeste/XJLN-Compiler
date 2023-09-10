package com.github.xjln.lang;

import com.github.xjln.compiler.Compiler;

import java.util.HashMap;

public sealed class XJLNClassStatic implements Compilable permits XJLNClass{

    public final String name;
    public final HashMap<String, XJLNField> staticFields;
    public final HashMap<String, XJLNMethod> staticMethods;
    public final HashMap<String, String> aliases;

    public XJLNClassStatic(XJLNClassStatic staticClass){
        this.name = staticClass.name;
        this.aliases = staticClass.aliases;
        this.staticFields = staticClass.staticFields;
        this.staticMethods = staticClass.staticMethods;
    }

    public XJLNClassStatic(String name, HashMap<String, String> aliases){
        this.name = name;
        this.aliases = aliases;

        this.staticFields = new HashMap<>();
        this.staticMethods = new HashMap<>();
    }

    public XJLNClassStatic(String name, HashMap<String, String> aliases, HashMap<String, XJLNField> staticFields, HashMap<String, XJLNMethodAbstract> staticMethods){
        this.name = name;
        this.aliases = aliases;
        this.staticFields = staticFields;

        this.staticMethods = new HashMap<>();
        for(String desc: staticMethods.keySet()){
            if(staticMethods.get(desc) instanceof XJLNMethod)
                this.staticMethods.put(desc, (XJLNMethod) staticMethods.get(desc));
            else
                throw new RuntimeException("internal Compiler error");
        }
    }

    public void addStaticField(XJLNField field){
        if(staticFields.containsKey(field.name()))
            throw new RuntimeException("Field " + field.name() + " already exist in Class " + name);

        staticFields.put(field.name(), field);
    }

    public void addStaticMethod(XJLNMethod method){
        String methodName = Compiler.toCompilerDesc(method);

        if(staticMethods.containsKey(methodName))
            throw new RuntimeException("Method " + methodName + " already exist in Class " + this.name);

        staticMethods.put(methodName, method);
    }
}
