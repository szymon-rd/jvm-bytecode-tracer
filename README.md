# JVM Bytecode Tracer

Tool to trace and output bytecode executed by threads in your JVM program. It keeps track of all execyted bytecode instructions and for some of them, keeps track of the values operated on (ALOAD and ARETURN). Used to debug [dotty/16806](https://github.com/lampepfl/dotty/issues/16806). *It does require refactor and was written as a quick tool to debug this issue.* However, somebody may find it useful. It required refactor, I may polish it in the future.


## Using the bytecode tracer

This tool modifies a given classfile and aggregates debug information, that can be analysed on shutdown of the JVM (or by reading the generated output file). [Scala-cli](https://scala-cli.virtuslab.org/) is required to run it. The command below can be used to run the tool:
```
scala-cli . -M tracer -- <inpath>/input.class <outpath>/output.class
scala-cli run --class <outpath>
```
The first comamnd will transform the input class from `<inpath>/input.class` and put the transformed version in the `<outpath>/output.class` (the classes can be named differently). The second command will run the class at output.
When the JVM shutdowns, there will appear the command line interface of the tracer:
```
-- Debugging tracer logs
Select:
        1. Print thread execution 
        2. Exit
```
Input `1` to see the logs. First, you will need to input the filters. You can leave them empty to display all the logs - just press enter. If you want to filter the executed bytecode, you can input filters in the following format:
```
<thread_filter>;<method_filter>
```
Then, it will output bytecode only executed in threads that have names that contain the `<thread_filter>`. It's the same for the methods - if a filter is provided, then it will output only bytecode executed in methods that match the filter (i.e. have name containing the string). Either option can be left empty to match all threads/methods.
For example:
```
Thread-1;foo
```
Will output only the bytecode executed in in the methods containing `foo` in their name and in the threads containing `Thread-1` in their name. 


## Warnings
1. The log is appended with shared lock. With minor rework it is possible to append asynchronously, but the current version was enough in the case of my issue, as it didn't break the race condition reproduction.
2. This tool is just a PoC that helped me fix my issue - it is unstable and not production-ready.
