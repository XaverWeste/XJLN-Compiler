package com.github.xjln.lang;

import com.github.xjln.compiler.Compiler;
import com.github.xjln.utility.MatchedList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

public class XJLNClass implements Compilable {

    public final String name;
    public final MatchedList<String, XJLNVariable> parameter;
    public final String[] superClasses;
    public final HashMap<String, XJLNMethod> methods;
    private final HashMap<String, XJLNField> fields;
    public final HashMap<String, String> aliases;

    public XJLNClass(String name, MatchedList<String, XJLNVariable> parameter, String[] superClasses, HashMap<String, String> aliases){
        this.name = name;
        this.parameter = parameter;
        this.superClasses = superClasses;
        this.aliases = aliases;
        methods = new HashMap<>();
        fields = new HashMap<>();
    }

    public void addField(XJLNField field){
        if(fields.containsKey(field.name()))
            throw new RuntimeException("field " + field + " already exist in " + name);
        fields.put(field.name(), field);
    }

    public boolean hasField(String field){
        return fields.containsKey(field);
    }

    public XJLNField getField(String field) throws NoSuchFieldException{
        if(fields.containsKey(field))
            return fields.get(field);

        throw new NoSuchFieldException(field);
    }

    public XJLNField[] getFields(){
        return fields.values().toArray(new XJLNField[0]);
    }

    public void addMethod(String name, XJLNMethod method){
        if(methods.containsKey(name))
            throw new RuntimeException("method " + name + " already exist" + this.name);
        methods.put(name, method);
    }

    public boolean hasMethod(String method, String...parameterTypes){
        return methods.containsKey(method + (parameterTypes.length == 0 ? "()" : Arrays.stream(parameterTypes).map(c -> c == null ? "null" : Compiler.toDesc(c)).collect(Collectors.joining(",", "(", ")"))));
    }

    public XJLNMethod getMethod(String method, String...parameterTypes) throws NoSuchMethodException{
        String methodIdentification = method + (parameterTypes.length == 0 ? "()" : Arrays.stream(parameterTypes).map(c -> c == null ? "null" : Compiler.toDesc(c)).collect(Collectors.joining(",", "(", ")")));

        if(methods.containsKey(methodIdentification))
            return methods.get(methodIdentification);

        throw new NoSuchMethodException(name + '.' + method + (parameterTypes.length == 0 ? "()" : Arrays.stream(parameterTypes).map(c -> c == null ? "null" : c).collect(Collectors.joining(",", "(", ")"))));
    }

    @Override
    public String toString() {
        return "XJLN-class " + name;
    }
}
