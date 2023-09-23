package com.github.xjln.lang;

import com.github.xjln.compiler.Compiler;
import com.github.xjln.compiler.Lexer;
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

        createSuperClassFields();
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

        createSuperClassFields();
    }

    private void createSuperClassFields(){
        if(superClasses != null) {
            for (String s : superClasses) {
                String name = Lexer.toToken(s).next().s();

                addField(new XJLNField(false, true, name, name, s));
            }
        }
    }

    public void createParameterFields(){
        for(XJLNParameter p:parameter.getValueList()){
            try{
                addField(p.toField());
            }catch (RuntimeException ignored){}
        }
    }

    public void addField(XJLNField field){
        if(fields.containsKey(field.name()))
            throw new RuntimeException("Field " + field.name() + " already exist in Class " + name);

        fields.put(field.name(), field);
    }

    public void addMethod(XJLNMethodAbstract method){
        String methodName = Compiler.toCompilerDesc(method);

        if(methods.containsKey(methodName))
            throw new RuntimeException("Method " + methodName + " already exist in Class " + name);

        methods.put(methodName, method);
    }

    public HashMap<String, XJLNField> getFields(){
        return fields;
    }
    public HashMap<String, XJLNMethodAbstract> getMethods(){
        return methods;
    }

    public boolean hasStatic(){
        return staticMethods.size() > 0 || staticFields.size() > 0;
    }

    public XJLNMethod generateDefaultInit(){
        return new XJLNMethod(false, false, "init", null, parameter, "void", new String[0], aliases);
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && fields.isEmpty() && methods.isEmpty();
    }

    @Override
    public boolean isGeneric(String type) {
        if(generics != null)
            for(String generic:generics)
                if(generic.equals(type))
                    return true;

        return false;
    }
}
