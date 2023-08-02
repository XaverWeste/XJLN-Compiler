package com.github.xjln.lang;

import com.github.xjln.compiler.Compiler;
import com.github.xjln.utility.MatchedList;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
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
        if(fields.containsKey(field))
            return true;

        for(String superClass:superClasses){
            switch (Objects.requireNonNull(Compiler.getClassLang(superClass))){
                case "xjln" -> {
                    Compilable compilable = Compiler.getXJLNClass(superClass);
                    if(compilable instanceof XJLNClass){
                        if(((XJLNClass) compilable).hasField(field))
                            return true;
                    } //TODO enum values
                }
                case "java" -> {
                    try{
                        Class<?> clazz = Class.forName(superClass);
                        while (clazz != null) {
                            try {
                                clazz.getField(field);
                                return true;
                            } catch (NoSuchFieldException ignored){}
                            clazz = clazz.getSuperclass();
                        }
                    }catch (ClassNotFoundException ignored){}
                }
            }
        }

        return false;
    }

    public XJLNField getField(String field) throws NoSuchFieldException{
        if(fields.containsKey(field))
            return fields.get(field);

        for(String superClass:superClasses){
            switch (Objects.requireNonNull(Compiler.getClassLang(superClass))){
                case "xjln" -> {
                    Compilable compilable = Compiler.getXJLNClass(superClass);
                    if(compilable instanceof XJLNClass){
                        try{
                            return ((XJLNClass) compilable).getField(field);
                        }catch (NoSuchFieldException ignored){}
                    } //TODO enum values
                }
                case "java" -> {
                    try{
                        Class<?> clazz = Class.forName(superClass);
                        while (clazz != null) {
                            try {
                                return XJLNField.ofField(clazz.getField(field));
                            } catch (NoSuchFieldException ignored){}
                            clazz = clazz.getSuperclass();
                        }
                    }catch (ClassNotFoundException ignored){}
                }
            }
        }

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

    public void validateSuperClasses() throws ClassNotFoundException{
        for(String superClass:superClasses)
            if(Compiler.getClassLang(superClass) == null)
                throw new ClassNotFoundException("Class " + superClass + " does not exist");
    }

    @Override
    public String toString() {
        return "XJLN-class " + name;
    }
}
