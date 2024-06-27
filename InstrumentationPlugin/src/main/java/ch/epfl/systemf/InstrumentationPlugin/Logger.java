package ch.epfl.systemf.InstrumentationPlugin;


import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

public class Logger {

    private TreeHelper helper;
    private TreeMaker mkTree;

    public Logger(TreeHelper helper) {
        this.helper = helper;
        mkTree = helper.mkTree;


        Type nodeId = helper.string;

    }

    /*******************************************************
     **************** Constants ********
     *******************************************************/

    private static final String FILE_LOGGER_PACKAGE = "ch.epfl.systemf";
    private static final String FILE_LOGGER_CLAZZ = "FileLogger";

    private static final TreeHelper.SimpleClass FILE_Logger = new TreeHelper.SimpleClass(FILE_LOGGER_PACKAGE, FILE_LOGGER_CLAZZ);

    public enum FileLoggerSubClasses {
        TryCatch("TryCatch"),
        DefaultControlFlow("DefaultControlFlow"),
        FunControlFlow("FunctionContext"),
        Statment("Statment"),
        SubStatment("SubStatment"),
        Call("Call"),
        VoidCall("VoidCall"),
        Write("Write"),
        InstanceReference("InstanceReference"),
        Identifier("Identifier"),
        LocalIdentifier("LocalIdentifier"),
        StaticIdentifier("StaticIdentifier"),
        FieldIdentifier("FieldIdentifier"),
        Value("Value");

        public final TreeHelper.SimpleClass clazz;

        FileLoggerSubClasses(String name) {
            this.clazz = new TreeHelper.SimpleClass(FILE_LOGGER_PACKAGE, FILE_LOGGER_CLAZZ + "$" + name);
        }

    }

    /*******************************************************
     **************** make concepts ********
     *******************************************************/

    /**************
     ********* Group Event
     **************/

    public JCTree.JCExpression tryCatch() {
        return helper.callStaticMethod(
                FILE_Logger,
                "tryCatch",
                List.nil(),
                helper.type(FileLoggerSubClasses.TryCatch.clazz),
                List.nil());
    }

    public JCTree.JCExpression controlFlow() {
        return helper.callStaticMethod(
                FILE_Logger,
                "controlFlow",
                List.nil(),
                helper.type(FileLoggerSubClasses.DefaultControlFlow.clazz),
                List.nil());
    }

    public JCTree.JCExpression functionFlow(String fullName) {
        return helper.callStaticMethod(
                FILE_Logger,
                "functionFlow",
                List.of(helper.string),
                helper.type(FileLoggerSubClasses.FunControlFlow.clazz),
                List.of(mkTree.Literal(fullName)));
    }

    public JCTree.JCExpression statment(String info) {
        JCTree.JCExpression tmp = helper.callStaticMethod(
                FILE_Logger,
                "statment",
                List.of(helper.string),
                helper.type(FileLoggerSubClasses.Statment.clazz),
                List.of(mkTree.Literal(info)));
        return tmp;
    }

    public JCTree.JCExpression subStatment(String info) {
        return helper.callStaticMethod(
                FILE_Logger,
                "subStatment",
                List.of(helper.string),
                helper.type(FileLoggerSubClasses.SubStatment.clazz),
                List.of(mkTree.Literal(info)));
    }

    /**************
     ********* execution step
     **************/

    public JCTree.JCExpression logSimpleExpression(SourceFormat.NodeSourceFormat nodeFormat, Value result, List<Write> assigns) {
        return helper.callStaticMethod(
                FILE_Logger,
                "logSimpleExpression",
                List.of(
                        helper.string,
                        helper.type(FileLoggerSubClasses.Value.clazz),
                        helper.arrayType(FileLoggerSubClasses.Write.clazz)
                ),
                helper.intP,
                List.of(
                        mkTree.Literal(nodeFormat.identifier()),
                        result.value,
                        helper.array(FileLoggerSubClasses.Write.clazz, assigns.map(a -> a.write))));
    }

    public JCTree.JCExpression logSimpleExpression(SourceFormat.NodeSourceFormat nodeFormat, List<Write> assigns) {
        return helper.callStaticMethod(
                FILE_Logger,
                "logSimpleExpression",
                List.of(
                        helper.string,
                        helper.arrayType(FileLoggerSubClasses.Write.clazz)
                ),
                helper.intP,
                List.of(
                        mkTree.Literal(nodeFormat.identifier()),
                        helper.array(FileLoggerSubClasses.Write.clazz, assigns.map(a -> a.write))));
    }

    public JCTree.JCExpression call(SourceFormat.NodeSourceFormat nodeFormat) {
        return helper.callStaticMethod(
                FILE_Logger,
                "call",
                List.of(helper.string),
                helper.type(FileLoggerSubClasses.Call.clazz),
                List.of(mkTree.Literal(nodeFormat.identifier())));
    }

    public JCTree.JCExpression voidCall(SourceFormat.NodeSourceFormat format) {
        return helper.callStaticMethod(
                FILE_Logger,
                "voidCall",
                List.of(helper.string),
                helper.type(FileLoggerSubClasses.VoidCall.clazz),
                List.of(mkTree.Literal(format.identifier())));
    }

    /**************
     ********* other
     **************/

    public record Write(JCTree.JCExpression write){};
    public Write write(Identifier identifier, Value value) {
        return new Write(helper.callStaticMethod(
                FILE_Logger,
                "write",
                List.of(
                        helper.type(FileLoggerSubClasses.Identifier.clazz),
                        helper.type(FileLoggerSubClasses.Value.clazz)
                ),
                helper.type(FileLoggerSubClasses.Write.clazz),
                List.of(identifier.ident, value.value)));
    }

    public record InstanceReference(JCTree.JCExpression ref){}

    public InstanceReference readReference(JCTree.JCExpression obj) {
        return new InstanceReference(helper.callStaticMethod(
                FILE_Logger,
                "readReference",
                List.of(helper.objectP),
                helper.type(FileLoggerSubClasses.InstanceReference.clazz),
                List.of(obj)));
    }

    public InstanceReference writeReference(JCTree.JCExpression obj) {
        return new InstanceReference(helper.callStaticMethod(
                FILE_Logger,
                "writeReference",
                List.of(helper.objectP),
                helper.type(FileLoggerSubClasses.InstanceReference.clazz),
                List.of(obj)));
    }

    public record Value(JCTree.JCExpression value){}

    public Value valueRepr(JCTree.JCExpression value) {
        return new Value(helper.callStaticMethod(
                FILE_Logger,
                "valueRepr",
                List.of(helper.objectP),
                helper.type(FileLoggerSubClasses.Value.clazz),
                List.of(value)));
    }

    /**************
     ********* identifier
     **************/

    public record Identifier(JCTree.JCExpression ident){}

    public Identifier localIdentifier(String parentNodeId, String name) {

        return new Identifier(helper.callStaticMethod(
                FILE_Logger,
                "localIdentifier",
                List.of(helper.string, helper.string),
                helper.type(FileLoggerSubClasses.LocalIdentifier.clazz),
                List.of(mkTree.Literal(parentNodeId), mkTree.Literal(name))));
    }

    public Identifier staticIdentifier(String packageName, String className, String name) {
        return new Identifier(helper.callStaticMethod(
                FILE_Logger,
                "staticIdentifier",
                List.of(helper.string, helper.string, helper.string),
                helper.type(FileLoggerSubClasses.StaticIdentifier.clazz),
                List.of(mkTree.Literal(packageName), mkTree.Literal(className), mkTree.Literal(name))));
    }

    public Identifier fieldIdentifier(InstanceReference owner, String name) {
        return new Identifier(helper.callStaticMethod(
                FILE_Logger,
                "staticIdentifier",
                List.of(helper.type(FileLoggerSubClasses.InstanceReference.clazz), helper.string),
                helper.type(FileLoggerSubClasses.StaticIdentifier.clazz),
                List.of(owner.ref, mkTree.Literal(name))));
    }


    /*******************************************************
     **************** call instance methods ********
     *******************************************************/

    /**************
     ********* group events instance methods
     **************/

    public JCTree.JCExpression groupMethod(String name, Symbol clazzInstance, TreeHelper.SimpleClass clazz) {

        return helper.callInstanceMethod(
                mkTree.Ident(clazzInstance),
                clazz,
                name,
                List.nil(),
                helper.intP,
                List.nil()
        );
    }

    public JCTree.JCExpression enter(Symbol clazzInstance, TreeHelper.SimpleClass clazz) {

        return helper.callInstanceMethod(
                mkTree.Ident(clazzInstance),
                clazz,
                "enter",
                List.nil(),
                helper.intP,
                List.nil()
        );
    }

    public JCTree.JCExpression exit(Symbol clazzInstance, TreeHelper.SimpleClass clazz) {
        return helper.callInstanceMethod(
                mkTree.Ident(clazzInstance),
                clazz,
                "exit",
                List.nil(),
                helper.intP,
                List.nil()
        );
    }

    /**************
     ********* execution steps
     **************/

    public JCTree.JCExpression logCall(Symbol clazzInstance, List<Value> argValues) {
        return helper.callInstanceMethod(
                mkTree.Ident(clazzInstance),
                FileLoggerSubClasses.Call.clazz,
                "logCall",
                List.of(helper.arrayType(FileLoggerSubClasses.Value.clazz)),
                helper.intP,
                List.of(helper.array(
                        FileLoggerSubClasses.Value.clazz,
                        argValues.map(v -> v.value)))
        );
    }

    public JCTree.JCExpression logReturn(Symbol clazzInstance, Value result) {
        return helper.callInstanceMethod(
                mkTree.Ident(clazzInstance),
                FileLoggerSubClasses.Call.clazz,
                "logReturn",
                List.of(helper.type(FileLoggerSubClasses.Value.clazz)),
                helper.intP,
                List.of(result.value)
        );
    }

    public JCTree.JCExpression logVoidCall(Symbol clazzInstance,  List<Value> argValues) {
        return helper.callInstanceMethod(
                mkTree.Ident(clazzInstance),
                FileLoggerSubClasses.VoidCall.clazz,
                "logCall",
                List.of(helper.arrayType(FileLoggerSubClasses.Value.clazz)),
                helper.intP,
                List.of(helper.array(
                        FileLoggerSubClasses.Value.clazz,
                        argValues.map(v -> v.value)))
        );
    }

    public JCTree.JCExpression logVoidReturn(Symbol clazzInstance) {
        return helper.callInstanceMethod(
                mkTree.Ident(clazzInstance),
                FileLoggerSubClasses.VoidCall.clazz,
                "logReturn",
                List.nil(),
                helper.intP,
                List.nil()
        );
    }

}
