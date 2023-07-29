package com.github.xjln.utility;

public class Var {

    private Object value;
    private final String[] allowedTypes;

    public Var(Object value, String...allowedTypes){
        if(allowedTypes.length == 0)
            this.allowedTypes = null;
        else
            this.allowedTypes = allowedTypes;
        this.value = value;
    }

    public Var(Object value){
        allowedTypes = new String[]{value.getClass().toString().split(" ")[1]};
        this.value = value;
    }

    public <T> void setValue(T value) {
        if(allowedTypes != null && !isAllowed(value.getClass().toString().split(" ", 2)[1]))
            throw new ClassCastException();
        this.value = value;
    }

    public <T> T getValue(){
        return (T) value;
    }

    public String getCurrentType(){
        return value.getClass().toString().split(" ", 2)[1];
    }

    public boolean isAllowed(String type){
        if(allowedTypes == null)
            return true;
        for(String allowedType:allowedTypes)
            if(type.equals(allowedType))
                return true;
        return false;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
