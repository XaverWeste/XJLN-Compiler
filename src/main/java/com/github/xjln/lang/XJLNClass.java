package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

import java.util.HashMap;

public final class XJLNClass extends Compilable{

    public final HashMap<String, XJLNField> fields;
    public final HashMap<String, XJLNField> staticFields;
    public final boolean finaly;
    public final boolean abstrakt;

    public XJLNClass(AccessFlag accessFlag, boolean finaly, boolean abstrakt){
        super(accessFlag);
        fields = new HashMap<>();
        staticFields = new HashMap<>();
        this.finaly = finaly;
        this.abstrakt = abstrakt;
    }

    public void addField(String name, XJLNField field){
        if(fields.containsKey(name))
            throw new RuntimeException("Field " + name + " already exist");

        fields.put(name, field);
    }

    public void addStaticField(String name, XJLNField field){
        if(staticFields.containsKey(name))
            throw new RuntimeException("Field " + name + " already exist");

        staticFields.put(name, field);
    }

    public XJLNField getField(String name){
        return fields.get(name);
    }

    public XJLNField getStaticField(String name){
        return staticFields.get(name);
    }

    public boolean isEmpty(){
        if(fields.size() > 0)
            return false;

        if(staticFields.size() > 0)
            return false;

        return true;
    }
}
