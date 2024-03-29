package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;
import com.github.xjln.utility.MatchedList;

import java.util.HashMap;

public final class XJLNClass extends Compilable{

    public final HashMap<String, XJLNField> fields;
    public final HashMap<String, XJLNField> staticFields;
    public final HashMap<String, XJLNMethod> methods;
    public final HashMap<String, XJLNMethod> staticMethods;
    public final boolean finaly;
    public final boolean abstrakt;

    public XJLNClass(AccessFlag accessFlag, boolean finaly, boolean abstrakt){
        super(accessFlag);
        fields = new HashMap<>();
        staticFields = new HashMap<>();
        methods = new HashMap<>();
        staticMethods = new HashMap<>();
        this.finaly = finaly;
        this.abstrakt = abstrakt;
    }

    public void addField(String name, XJLNField field){
        if(staticFields.containsKey(name) || fields.containsKey(name))
            throw new RuntimeException("Field " + name + " already exist");

        fields.put(name, field);
    }

    public void addStaticField(String name, XJLNField field){
        if(staticFields.containsKey(name) || fields.containsKey(name))
            throw new RuntimeException("Field " + name + " already exist");

        staticFields.put(name, field);
    }

    public void addMethod(String name, XJLNMethod method){
        if(methods.containsKey(name))
            throw new RuntimeException("Method " + name + " already exist");

        methods.put(name, method);
    }

    public void addStaticMethod(String name, XJLNMethod method){
        if(staticMethods.containsKey(name))
            throw new RuntimeException("Method " + name + " already exist");

        staticMethods.put(name, method);
    }

    public XJLNField getField(String name){
        if(fields.containsKey(name)) return fields.get(name);
        else return staticFields.getOrDefault(name, null);
    }

    public short hasField(String name){
        if(fields.containsKey(name)) return 1;
        else if(staticFields.containsKey(name)) return -1;
        else return 0;
    }

    public boolean isEmpty(){
        return fields.isEmpty() && staticFields.isEmpty();
    }

    public void createDefaultInit(){
        addMethod("init", new XJLNMethod(AccessFlag.ACC_PUBLIC, "void", new MatchedList<>(), "", false, false, false, -1));
    }

    @Override
    public int getAccessFlag() {
        int accessFlag = super.getAccessFlag();

        if(finaly)
            accessFlag += AccessFlag.FINAL;

        if(abstrakt)
            accessFlag += AccessFlag.ABSTRACT;

        return accessFlag;
    }
}
