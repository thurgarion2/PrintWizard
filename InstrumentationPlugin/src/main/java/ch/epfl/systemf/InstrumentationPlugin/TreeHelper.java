package ch.epfl.systemf.InstrumentationPlugin;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;


public class TreeHelper {



    //don't support inner class, only class that can be identified from package + name
    //we assume the classes are public
    public record SimpleClass(String packageName, String className){};

    public final TreeMaker mkTree;
    private final Names names;
    private final Symtab symb;
    public final Type voidP;
    public final Type intP;
    public final Type string;
    public final Type boolP;
    public final Type objectP;
    public final SimpleClass evaluation;

    public TreeHelper(TreeMaker mkTree, Names names, Symtab symb) {
        this.mkTree = mkTree;
        this.symb = symb;
        this.names = names;
        this.voidP = symb.voidType;
        this.intP = symb.intType;
        SimpleClass string = new SimpleClass("java.lang", "String");
        this.string = type(string);
        this.evaluation = new TreeHelper.SimpleClass("ch.epfl.systemf", "Logger$Evaluation");
        this.boolP = symb.booleanType;
        this.objectP = symb.objectType;
    }


    public JCTree.JCExpression newEvaluation(boolean hasResult, Symbol result, boolean hasAssign, String varName, Symbol value){
        if(hasResult && result==null)
            throw new IllegalStateException();
        if(hasAssign && (varName==null || value==null))
            throw new IllegalStateException();

        JCTree.JCExpression nullExpr = mkTree.Literal(TypeTag.BOT,
                null)
                .setType(symb.botType);

        return simpleNewClass(this.evaluation,
                List.of(
                        mkTree.Literal(hasResult),
                        hasResult ? mkTree.Ident(result) : nullExpr,
                        mkTree.Literal(hasAssign),
                        hasAssign ?  mkTree.Literal(varName) : nullExpr,
                        hasAssign ? mkTree.Ident(value) : nullExpr
                ),
                List.of(boolP, objectP, boolP, string, objectP));
    }


    public JCTree.JCExpression simpleNewClass(SimpleClass clazz, List<JCTree.JCExpression> args, List<Type> argsType){

        JCTree.JCNewClass newClass = mkTree.NewClass(null,
                List.nil(),
                mkTree.Ident(symbol(clazz)),
                args,
                null);

        newClass.constructor = instanceMethod(
                symbol(clazz),
                "<init>",
                argsType,
                voidP
        );

        newClass.constructorType = newClass.constructor.type;
        newClass.polyKind = JCTree.JCPolyExpression.PolyKind.STANDALONE;

        return  newClass.setType(type(clazz));
    }

    public JCTree.JCExpression callFun(JCTree.JCExpression fun, List<JCTree.JCExpression> args){
        return mkTree.App(fun, args);

    }

    public JCTree.JCExpression staticMethod(SimpleClass clazz, String name, List<Type> argTypes, Type ret) {
        Symbol.MethodSymbol symbol = staticMethod(symbol(clazz), name, argTypes, ret);
        return mkTree.Select(mkTree.Ident(symbol(clazz)), symbol);
    }

    public JCTree.JCExpression instanceMethod(SimpleClass clazz, String name, List<Type> argTypes, Type ret) {
        Symbol.MethodSymbol symbol = instanceMethod(symbol(clazz), name, argTypes, ret);
        return mkTree.Select(mkTree.Ident(symbol(clazz)), symbol);
    }


    public Symbol.VarSymbol finalStaticField(Symbol owner, Type type, String name) {
        return new Symbol.VarSymbol(Flags.PUBLIC | Flags.STATIC | Flags.FINAL,
                name(name),
                type,
                owner);
    }

    public Symbol.MethodSymbol instanceMethod(Symbol.ClassSymbol owner, String name,  List<Type> args, Type ret){
        return new Symbol.MethodSymbol(Flags.PUBLIC,
                name(name),
                methodWithoutExceptions(owner, args, ret),
                owner
        );
    }

    public Symbol.MethodSymbol staticMethod(Symbol.ClassSymbol owner, String name,  List<Type> args, Type ret){
        return new Symbol.MethodSymbol(Flags.PUBLIC|Flags.STATIC,
                name(name),
                methodWithoutExceptions(owner, args, ret),
                owner
        );
    }

    public Type.MethodType methodWithoutExceptions(Symbol.ClassSymbol owner, List<Type> args, Type ret){
        return new Type.MethodType(args,
                ret,
                List.nil(),
                owner
        );
    }


    public Type.ClassType type(SimpleClass c){
        return  new Type.ClassType(Type.noType,
                List.nil(),
                symbol(c));
    }

    public Type.ClassType type(Symbol.TypeSymbol sym) {
        return new Type.ClassType(Type.noType, List.nil(), sym);
    }

    public Symbol.ClassSymbol symbol(SimpleClass c){
        Symbol.PackageSymbol pack = getPackage(c.packageName);
        return new Symbol.ClassSymbol(Flags.PUBLIC,
                name(c.className),
                pack
        );
    }

    public Symbol.ClassSymbol classSymbol(Symbol.PackageSymbol pack, String name){
        return new Symbol.ClassSymbol(Flags.PUBLIC,
                name(name),
                pack
                );
    }

    public Symbol.PackageSymbol getPackage(String name) {
        String[] parts = name.split("\\.");
        Symbol.PackageSymbol res = symb.rootPackage;
        for (String part : parts) {
            res = new Symbol.PackageSymbol(name(part), res);
        }
        return res;
    }
    public Symbol system() {
        return symb.systemType.asElement();
    }
    public Name name(String name) {
        return names.fromString(name);
    }
}
