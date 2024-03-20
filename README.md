# PrintWizard

## Description

Print debugging is widely used, but today most programmers are hand making their print statement. It is suboptimal. PrintWizard is a tool for print debugging. It allows you to:
- Collect traces of programs automatically.
- Provide advanced trace formatting. 
- Provide utilities to explore, filter and extract relevant information from the trace.

## Decisions and Alternatives

### Collecting traces

PrintWizard collect traces using a java agent, instruementing bytecode at runtime. It is the most convinent way for the user and we have all the information about the system. Alternatives are using a preprosessor to instrument the source code, to modify the javac compiler or to instrument the bytecode before loading it into the jvm.

### Trace formatting


## Concerns

I am relatively confident that we will be able to map the trace to the AST, but it may be more difficult than expected.

