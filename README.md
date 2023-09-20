![Build](https://github.com/XaverWeste/XJLN-Compiler/actions/workflows/maven.yml/badge.svg)

# XJLN-Compiler
XJLN Compiler for Java Virtual Machine

## XJLN IntelliJ Plugin
https://github.com/XaverWeste/XJLN-IntelliJ-Plugin.git

## How to use
You only have to create a new Object of the Compiler and the Compiler will do the rest.

The Compiler has several parameters,
1. The path to the .xjln File with the main method (if no main method should be executed, the parameter should be null)
2. multiple pathes to folders wich should be Compiled

The compiler first validates all (input and output) folders. Second, all .class files and empty folders in the output folder will be deleted (all other files in this folder should be safe and untouched). Third, the compiler starts compiling all specified src folders and writes the resulting .class files to the output folder. The main method is executed last, if specified.

## Syntax Basics
### Hello World
```
use java/lang/System

main -> System:out:println("Hello World!")
```

### Enum definition
```
def Bool = True | False
```

### Method/Function definion
```
def fib(int n) :: int
  if n < 0 -> return -1
  if (n == 0) | (n == 1) -> return 1
  return fib(n - 1) + fib(n - 2)
end

def doubleInt(int value) :: int = value * 2
```
