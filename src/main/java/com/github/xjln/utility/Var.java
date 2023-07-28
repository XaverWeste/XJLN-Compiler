package com.github.xjln.utility;

public class Var {

    private Object value;
    private final String[] allowedTypes;

    public Var(String...allowedTypes){
        if(allowedTypes.length == 0)
            this.allowedTypes = null;
        else
            this.allowedTypes = allowedTypes;
    }

    public <T> void setValue(T value) {
        if(allowedTypes != null && !isAllowed(value.getClass().toString().split(" ", 2)[1]))
            throw new ClassCastException();
        this.value = value;
    }

    public <T> T getValue(){
        return (T) value;
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
